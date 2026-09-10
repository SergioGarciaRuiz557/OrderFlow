package com.orderflow.payment.adapter.`out`.persistence

import com.orderflow.payment.application.port.`out`.PaymentAuthorizationLock
import com.orderflow.payment.domain.model.OrderId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class PostgreSqlPaymentAuthorizationLock(
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : PaymentAuthorizationLock {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun <T : Any> withLock(orderId: OrderId, operation: () -> T): T =
        requireNotNull(
            transactionTemplate.execute {
                jdbcTemplate.queryForObject(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                    String::class.java,
                    orderId.value,
                )
                operation()
            },
        )
}
