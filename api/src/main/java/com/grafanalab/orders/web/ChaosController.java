package com.grafanalab.orders.web;

import com.grafanalab.orders.infrastructure.chaos.ChaosSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Panneau de controle du chaos. A NE JAMAIS deployer tel quel en production
 * (aucune authentification) : c'est un outil de demo et de lab.
 *
 * Utilisation typique pendant un test de charge k6 :
 *
 *   # 1. tout va bien
 *   curl -XPOST localhost:8080/api/chaos/reset
 *
 *   # 2. la latence de la banque explose -> le p99 monte, l'alerte latence part
 *   curl -XPOST localhost:8080/api/chaos -H 'Content-Type: application/json' \
 *        -d '{"extraLatencyMs": 800}'
 *
 *   # 3. 20% de 500 -> le burn rate du SLO s'emballe
 *   curl -XPOST localhost:8080/api/chaos -H 'Content-Type: application/json' \
 *        -d '{"httpErrorRatePercent": 20}'
 *
 * C'est CE scenario que vous rejouerez en entretien : il montre en 3 minutes
 * metriques + logs + alertes + correlation.
 */
@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    private final ChaosSettings chaos;

    public ChaosController(ChaosSettings chaos) {
        this.chaos = chaos;
    }

    @GetMapping
    public Map<String, Object> current() {
        return snapshot();
    }

    /**
     * Mise a jour partielle : les cles absentes ne sont pas modifiees.
     * On accepte une Map plutot qu'un DTO strict pour rester souple en demo.
     */
    @PostMapping
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        applyLong(body, "extraLatencyMs", chaos::setExtraLatencyMs);
        applyInt(body, "declineRatePercent", chaos::setDeclineRatePercent);
        applyInt(body, "gatewayFailureRatePercent", chaos::setGatewayFailureRatePercent);
        applyInt(body, "httpErrorRatePercent", chaos::setHttpErrorRatePercent);
        // Log en WARN volontairement : ce changement doit etre visible dans Loki,
        // pour pouvoir annoter le dashboard ("c'est ici que j'ai casse le systeme").
        log.warn("chaos modifie settings={} event=chaos_updated", snapshot());
        return snapshot();
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        chaos.reset();
        log.warn("chaos remis a zero event=chaos_reset");
        return snapshot();
    }

    private Map<String, Object> snapshot() {
        return Map.of(
                "extraLatencyMs", chaos.extraLatencyMs(),
                "declineRatePercent", chaos.declineRatePercent(),
                "gatewayFailureRatePercent", chaos.gatewayFailureRatePercent(),
                "httpErrorRatePercent", chaos.httpErrorRatePercent());
    }

    private void applyInt(Map<String, Object> body, String key, java.util.function.IntConsumer setter) {
        Object value = body.get(key);
        if (value instanceof Number n) {
            setter.accept(n.intValue());
        }
    }

    private void applyLong(Map<String, Object> body, String key, java.util.function.LongConsumer setter) {
        Object value = body.get(key);
        if (value instanceof Number n) {
            setter.accept(n.longValue());
        }
    }
}
