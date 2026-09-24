package com.orderflow.payment.application.service

import com.orderflow.payment.application.port.`in`.GetPaymentUseCase
import com.orderflow.payment.application.port.`out`.PaymentRepository
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentId
import org.springframework.stereotype.Service

/**
 * Servicio de aplicación de solo lectura para recuperar instantáneas de agregados de pago.
 *
 * El servicio expone deliberadamente el puerto [GetPaymentUseCase] y delega el acceso al almacenamiento
 * en [PaymentRepository], independiente del framework. No contiene comportamiento de mutación CRUD ni
 * expone tipos de Spring Data a los llamadores.
 *
 * @property repository puerto de persistencia de salida que se usa para la búsqueda.
 */
@Service
class GetPaymentService(private val repository: PaymentRepository) : GetPaymentUseCase {
    /**
     * Recupera el último pago conservado sin alterar el estado.
     *
     * @param paymentId clave primaria del agregado que se debe localizar.
     * @return instantánea del pago, o `null` cuando no existe dicho identificador.
     */
    override fun getPayment(paymentId: PaymentId): Payment? = repository.findById(paymentId)
}
