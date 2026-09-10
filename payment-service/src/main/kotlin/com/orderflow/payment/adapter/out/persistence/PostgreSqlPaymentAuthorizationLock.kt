package com.orderflow.payment.adapter.`out`.persistence

import com.orderflow.payment.application.port.`out`.PaymentAuthorizationLock
import com.orderflow.payment.domain.model.OrderId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * PostgreSQL implementation of the order-scoped [PaymentAuthorizationLock] output port.
 *
 * PostgreSQL transaction-level advisory locks provide cross-thread and cross-service-instance mutual
 * exclusion without adding a technical lock table. The external order id is hashed to the signed
 * 64-bit key expected by PostgreSQL. Equal order ids therefore contend for one lock, while unrelated
 * orders normally proceed in parallel. A theoretical hash collision only causes extra serialization;
 * it cannot weaken correctness or mix payment data.
 *
 * [TransactionTemplate] is essential because `pg_advisory_xact_lock` is released automatically only
 * when its database transaction commits or rolls back. Repository calls made inside `operation` join
 * the same Spring transaction, making the read/create/gateway/write sequence one serialized unit.
 *
 * @property jdbcTemplate executes the small provider-specific lock statement.
 * @param transactionManager Spring transaction manager used to build the reusable template.
 */
@Component
class PostgreSqlPaymentAuthorizationLock(
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : PaymentAuthorizationLock {
    /** Programmatic transaction boundary that encloses acquisition, work, commit, and lock release. */
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Executes one authorization workflow while PostgreSQL owns the order-specific advisory lock.
     *
     * `pg_advisory_xact_lock` waits when another transaction currently processes the same key. The
     * statement returns only after this transaction owns the lock. [requireNotNull] protects the
     * generic non-null contract because Spring's Java transaction API expresses callback results as
     * nullable even though [operation] is constrained to `T : Any`.
     *
     * If [operation] throws, [TransactionTemplate] rolls back and PostgreSQL releases the lock. The
     * authorization service deliberately catches gateway technical failures inside the callback so
     * its pending row commits before the exception is rethrown outside this method.
     *
     * @param orderId business key used to derive the advisory lock key.
     * @param operation complete authorization workflow to serialize.
     * @return non-null operation result after successful transaction commit.
     */
    override fun <T : Any> withLock(orderId: OrderId, operation: () -> T): T =
        requireNotNull(
            transactionTemplate.execute {
                // `hashtextextended` deterministically converts the external string into a BIGINT key.
                jdbcTemplate.queryForObject(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                    String::class.java,
                    orderId.value,
                )
                // Repository operations below participate in this same transaction and held lock.
                operation()
            },
        )
}
