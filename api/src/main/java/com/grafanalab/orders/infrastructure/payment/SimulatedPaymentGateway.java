package com.grafanalab.orders.infrastructure.payment;

import com.grafanalab.orders.infrastructure.chaos.ChaosSettings;
import com.grafanalab.orders.service.port.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ADAPTATEUR : fausse passerelle de paiement.
 *
 * Elle simule le comportement d'un vrai systeme distant :
 *
 *  - une latence de base a distribution LOG-NORMALE, pas uniforme. C'est
 *    important : les vrais systemes ont une longue traine. Une latence uniforme
 *    donnerait un p99 quasi egal au p50, ce qui n'apprend rien. Ici la mediane
 *    est ~35 ms mais le p99 depasse 300 ms : vos histogrammes et vos quantiles
 *    ressembleront enfin a de la vraie production.
 *
 *  - des refus metier et des pannes techniques pilotes par ChaosSettings.
 */
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    /** Motifs de refus : peu nombreux et stables, donc OK comme label Prometheus. */
    private static final List<String> DECLINE_REASONS =
            List.of("insufficient_funds", "card_expired", "fraud_suspected", "limit_exceeded");

    private final ChaosSettings chaos;

    public SimulatedPaymentGateway(ChaosSettings chaos) {
        this.chaos = chaos;
    }

    @Override
    public PaymentResult charge(String orderId, BigDecimal amount) {
        sleepLikeARealNetworkCall();

        if (roll() < chaos.gatewayFailureRatePercent()) {
            // Log structure : dans Loki on pourra requeter
            //   {app="orders-api"} | json | event = "payment_gateway_down"
            log.warn("passerelle de paiement indisponible orderId={} amount={} event=payment_gateway_down",
                    orderId, amount);
            throw new PaymentGatewayUnavailableException("passerelle de paiement injoignable (chaos)");
        }

        if (roll() < chaos.declineRatePercent()) {
            String reason = DECLINE_REASONS.get(
                    ThreadLocalRandom.current().nextInt(DECLINE_REASONS.size()));
            log.info("paiement refuse orderId={} reason={} event=payment_declined", orderId, reason);
            return PaymentResult.declined(reason);
        }

        String reference = "pay_" + UUID.randomUUID().toString().substring(0, 12);
        log.info("paiement accepte orderId={} amount={} reference={} event=payment_approved",
                orderId, amount, reference);
        return PaymentResult.approved(reference);
    }

    /**
     * Latence log-normale : exp(mu + sigma * N(0,1)), mediane = exp(mu).
     * mu = ln(35) ~ 3.55 pour une mediane de ~35 ms ; sigma = 0.6 pour la traine.
     */
    private void sleepLikeARealNetworkCall() {
        double gaussian = ThreadLocalRandom.current().nextGaussian();
        long base = (long) Math.exp(3.55 + 0.6 * gaussian);
        long total = Math.clamp(base, 5L, 5_000L) + chaos.extraLatencyMs();
        try {
            Thread.sleep(total);
        } catch (InterruptedException e) {
            // Toujours restaurer le flag d'interruption, sinon le pool de threads
            // ne saura jamais qu'on lui demande de s'arreter (arret k8s propre).
            Thread.currentThread().interrupt();
        }
    }

    private int roll() {
        return ThreadLocalRandom.current().nextInt(100);
    }
}
