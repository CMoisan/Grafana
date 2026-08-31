package com.grafanalab.orders.infrastructure.metrics;

import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.service.port.OrderMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;

/**
 * ============================================================================
 * ADAPTATEUR Micrometer : traduit le vocabulaire METIER en metriques Prometheus.
 * ============================================================================
 * C'est le SEUL endroit du code metier ou Micrometer apparait.
 *
 * CONVENTIONS DE NOMMAGE (a connaitre par coeur pour Grafana) :
 *  - nom en snake_case, prefixe par le domaine  -> orders_created_total
 *  - suffixe _total pour un counter monotone
 *  - suffixe d'unite explicite                  -> _seconds, _bytes, _euros
 *  - Micrometer utilise des points ("orders.created") et les convertit
 *    automatiquement en underscores pour Prometheus. On ecrit donc en style
 *    Micrometer ici, et on lit "orders_created_total" dans PromQL.
 *
 * LES 3 TYPES QUE VOUS DEVEZ MAITRISER :
 *  - Counter   : ne fait que monter. On ne le lit JAMAIS brut, toujours via
 *                rate() ou increase(). Ex : nombre de commandes.
 *  - Gauge     : monte et descend, on lit la valeur telle quelle.
 *                Ex : commandes en attente, memoire utilisee.
 *  - Histogram : distribution (buckets cumulatifs). Permet histogram_quantile()
 *                pour le p95/p99, et surtout permet d'AGREGER plusieurs pods,
 *                ce qu'un "summary" cote client ne permet pas. Retenez ca :
 *                un p99 par pod n'est PAS moyennable ; il faut un histogramme.
 *
 * PIEGE CARDINALITE : chaque combinaison de labels cree une serie temporelle
 * stockee separement dans Mimir. Jamais d'ID, d'email, d'URL brute ou de
 * timestamp en label. Ici on met `reason` (4 valeurs) et `status` (5 valeurs).
 * ============================================================================
 */
public class MicrometerOrderMetrics implements OrderMetrics {

    private final MeterRegistry registry;

    // On pre-cree les meters sans label variable : c'est plus rapide (pas de
    // lookup dans une map a chaque appel) et ca documente la liste des metriques.
    private final Counter ordersCreated;
    private final Counter ordersPaid;
    private final DistributionSummary basketItems;
    private final DistributionSummary basketAmount;
    private final Counter revenue;

    public MicrometerOrderMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.ordersCreated = Counter.builder("orders.created")
                .description("Nombre total de commandes creees")
                .register(registry);

        this.ordersPaid = Counter.builder("orders.paid")
                .description("Nombre total de commandes payees avec succes")
                .register(registry);

        // Un DistributionSummary est un histogramme pour une valeur non temporelle.
        // publishPercentileHistogram() -> expose les buckets `_bucket` necessaires
        // a histogram_quantile() cote PromQL (agregation multi-pods possible).
        this.basketItems = DistributionSummary.builder("orders.basket.items")
                .description("Nombre d'articles par commande")
                .publishPercentileHistogram()
                .register(registry);

        this.basketAmount = DistributionSummary.builder("orders.basket.amount.euros")
                .description("Montant du panier en euros")
                .baseUnit("euros")
                .publishPercentileHistogram()
                .register(registry);

        // Le chiffre d'affaires est un counter : il ne fait que croitre.
        // Dans Grafana : increase(orders_revenue_euros_total[1h]) = CA de l'heure.
        this.revenue = Counter.builder("orders.revenue.euros")
                .description("Chiffre d'affaires encaisse cumule")
                .baseUnit("euros")
                .register(registry);
    }

    @Override
    public void orderCreated(int itemCount, BigDecimal amount) {
        ordersCreated.increment();
        basketItems.record(itemCount);
        basketAmount.record(amount.doubleValue());
    }

    @Override
    public void orderPaid(BigDecimal amount) {
        ordersPaid.increment();
        revenue.increment(amount.doubleValue());
    }

    @Override
    public void paymentDeclined(String reason) {
        // Label dynamique mais issu d'une liste fermee -> cardinalite maitrisee.
        registry.counter("orders.payments.declined", "reason", reason).increment();
    }

    @Override
    public void paymentGatewayFailure() {
        registry.counter("orders.payments.gateway.failures").increment();
    }

    @Override
    public void statusTransition(OrderStatus from, OrderStatus to) {
        // 5 statuts x 5 statuts = 25 series max. Acceptable et tres parlant :
        // ca permet de dessiner un diagramme de flux (Sankey) dans Grafana.
        registry.counter("orders.status.transitions",
                "from", from.name(),
                "to", to.name()).increment();
    }
}
