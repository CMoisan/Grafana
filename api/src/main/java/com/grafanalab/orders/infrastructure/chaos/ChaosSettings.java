package com.grafanalab.orders.infrastructure.chaos;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================================
 * LE "CHAOS PANEL" : le composant le plus utile de ce lab.
 * ============================================================================
 * Ces reglages sont modifiables A CHAUD via POST /api/chaos. Ils permettent de
 * provoquer a la demande, pendant une demo ou un test k6 :
 *   - de la latence supplementaire (p95/p99 qui montent, alerte de latence) ;
 *   - un taux d'erreurs 5xx (burn rate du SLO qui s'emballe) ;
 *   - une panne de la passerelle de paiement (HTTP 503).
 *
 * C'est exactement la boucle d'un Solutions Engineer en demo :
 *   je casse -> le dashboard vire au rouge -> l'alerte part -> j'explore les
 *   logs correles au meme instant dans Loki -> je repare -> tout revient au vert.
 *
 * Thread-safe : champs Atomic car lus par les threads Tomcat pendant qu'un
 * autre thread les modifie via l'endpoint d'admin.
 * ============================================================================
 */
public class ChaosSettings {

    /** Latence artificielle ajoutee a chaque appel de paiement (ms). */
    private final AtomicLong extraLatencyMs = new AtomicLong(0);

    /** Probabilite 0-100 qu'un paiement soit REFUSE (metier, HTTP 200). */
    private final AtomicInteger declineRatePercent = new AtomicInteger(5);

    /** Probabilite 0-100 que la passerelle soit EN PANNE (technique, HTTP 503). */
    private final AtomicInteger gatewayFailureRatePercent = new AtomicInteger(0);

    /** Probabilite 0-100 qu'un endpoint HTTP renvoie une 500 seche. */
    private final AtomicInteger httpErrorRatePercent = new AtomicInteger(0);

    public long extraLatencyMs() { return extraLatencyMs.get(); }
    public int declineRatePercent() { return declineRatePercent.get(); }
    public int gatewayFailureRatePercent() { return gatewayFailureRatePercent.get(); }
    public int httpErrorRatePercent() { return httpErrorRatePercent.get(); }

    public void setExtraLatencyMs(long value) { extraLatencyMs.set(clampLong(value)); }
    public void setDeclineRatePercent(int value) { declineRatePercent.set(clampPercent(value)); }
    public void setGatewayFailureRatePercent(int value) { gatewayFailureRatePercent.set(clampPercent(value)); }
    public void setHttpErrorRatePercent(int value) { httpErrorRatePercent.set(clampPercent(value)); }

    /** Remet tout au calme : le bouton "je repare" de la demo. */
    public void reset() {
        extraLatencyMs.set(0);
        declineRatePercent.set(5);
        gatewayFailureRatePercent.set(0);
        httpErrorRatePercent.set(0);
    }

    private static int clampPercent(int v) { return Math.clamp(v, 0, 100); }
    private static long clampLong(long v) { return Math.clamp(v, 0L, 30_000L); }
}
