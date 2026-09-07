package com.orderflow.order.integration;

import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.CustomerId;
import com.orderflow.order.domain.model.Money;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import com.orderflow.order.domain.model.OrderLine;
import com.orderflow.order.domain.model.PaymentMethodId;
import com.orderflow.order.domain.model.ProductId;
import com.orderflow.order.domain.model.Quantity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration specification using a disposable real PostgreSQL database.
 *
 * <p>The class verifies the same Flyway migration and Hibernate validation used in production. It is
 * disabled rather than failed when Docker is unavailable.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderServiceIntegrationTest {
    /** Shared PostgreSQL container started once for this integration-test class. */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    /**
     * Overrides production datasource properties with the container's random connection details.
     *
     * @param registry Spring test property registry
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Real hexagonal repository adapter under test. */
    @Autowired OrderRepository repository;
    /** HTTP test client that invokes the complete Spring MVC stack without an external server. */
    @Autowired MockMvc mockMvc;

    /** Verifies JPA mapping, line persistence, total validation, status, and event-free rehydration. */
    @Test
    @Transactional
    void shouldPersistAndRehydrateAggregate() {
        Order order = Order.create(new OrderId(UUID.randomUUID()), new CustomerId(UUID.randomUUID()),
                List.of(new OrderLine(new ProductId("PRODUCT-001"), new Quantity(2),
                        Money.eur(new BigDecimal("59.99")))),
                new PaymentMethodId("pm-test"), Instant.parse("2026-09-07T10:00:00Z"));
        order.requestInventoryReservation(Instant.parse("2026-09-07T10:00:01Z"));

        repository.save(order);
        Order restored = repository.findById(order.id()).orElseThrow();

        assertThat(restored.status()).isEqualTo(order.status());
        assertThat(restored.total()).isEqualTo(order.total());
        assertThat(restored.lines()).isEqualTo(order.lines());
        assertThat(restored.pullDomainEvents()).isEmpty();
    }

    /** Verifies POST and GET through validation, application, Flyway-created tables, and JPA. */
    @Test
    void shouldCreateAndRetrieveOrderThroughRestApi() throws Exception {
        String body = """
                {
                  "customerId": "%s",
                  "items": [{"productId": "PRODUCT-001", "quantity": 2, "unitPrice": 59.99}],
                  "paymentMethodId": "pm-test-success"
                }
                """.formatted(UUID.randomUUID());

        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("INVENTORY_RESERVATION_PENDING"))
                .andExpect(jsonPath("$.total").value(119.98))
                .andReturn().getResponse().getContentAsString();
        String orderId = response.replaceAll(".*\\\"orderId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    /** Verifies invalid JSON values use the stable structured API error contract. */
    @Test
    void shouldReturnStructuredValidationError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":null,\"items\":[],\"paymentMethodId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/orders"));
    }
}
