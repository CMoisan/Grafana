package com.grafanalab.orders.service.port;

import java.math.BigDecimal;

/**
 * PORT vers un systeme externe (passerelle de paiement).
 *
 * C'est la dependance la plus interessante a mocker : c'est elle qui, en prod,
 * est lente et faillible. Dans le lab, l'implementation simule de la latence et
 * des echecs -> c'est ce qui rendra les dashboards Grafana vivants.
 */
public interface PaymentGateway {

    /** Resultat d'un appel de paiement. `reference` non nulle si approved. */
    record PaymentResult(boolean approved, String reference, String declineReason) {

        public static PaymentResult approved(String reference) {
            return new PaymentResult(true, reference, null);
        }

        public static PaymentResult declined(String reason) {
            return new PaymentResult(false, null, reason);
        }
    }

    /**
     * @throws PaymentGatewayUnavailableException si le systeme distant est en panne
     *         (=> HTTP 503 cote API, et CA c'est une vraie erreur qui consomme le budget SLO).
     */
    PaymentResult charge(String orderId, BigDecimal amount);

    /** Panne technique du prestataire : distincte d'un refus de paiement. */
    class PaymentGatewayUnavailableException extends RuntimeException {
        public PaymentGatewayUnavailableException(String message) {
            super(message);
        }
    }
}
