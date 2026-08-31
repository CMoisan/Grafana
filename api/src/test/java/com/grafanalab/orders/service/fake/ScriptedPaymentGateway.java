package com.grafanalab.orders.service.fake;

import com.grafanalab.orders.service.port.PaymentGateway;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Passerelle de paiement "scriptee" : on programme a l'avance la suite de
 * reponses. Aucune latence, aucun aleatoire -> les tests sont instantanes et
 * 100% deterministes.
 *
 * C'est exactement pour ca que `PaymentGateway` est une interface : en prod on
 * branche l'implementation lente et faillible, en test on branche celle-ci.
 */
public class ScriptedPaymentGateway implements PaymentGateway {

    private final Deque<Object> script = new ArrayDeque<>();
    public BigDecimal lastAmountCharged;
    public int chargeCount = 0;

    public ScriptedPaymentGateway thenApprove(String reference) {
        script.add(PaymentResult.approved(reference));
        return this;
    }

    public ScriptedPaymentGateway thenDecline(String reason) {
        script.add(PaymentResult.declined(reason));
        return this;
    }

    public ScriptedPaymentGateway thenFail() {
        script.add(new PaymentGatewayUnavailableException("panne simulee en test"));
        return this;
    }

    @Override
    public PaymentResult charge(String orderId, BigDecimal amount) {
        chargeCount++;
        lastAmountCharged = amount;
        Object next = script.poll();
        if (next == null) {
            throw new IllegalStateException("script epuise : le test appelle charge() plus que prevu");
        }
        if (next instanceof RuntimeException e) {
            throw e;
        }
        return (PaymentResult) next;
    }
}
