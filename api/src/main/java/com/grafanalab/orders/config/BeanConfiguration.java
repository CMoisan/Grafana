package com.grafanalab.orders.config;

import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.infrastructure.chaos.ChaosSettings;
import com.grafanalab.orders.infrastructure.metrics.MicrometerOrderMetrics;
import com.grafanalab.orders.infrastructure.payment.SimulatedPaymentGateway;
import com.grafanalab.orders.infrastructure.persistence.InMemoryOrderRepository;
import com.grafanalab.orders.service.DefaultOrderService;
import com.grafanalab.orders.service.OrderService;
import com.grafanalab.orders.service.port.OrderMetrics;
import com.grafanalab.orders.service.port.OrderRepository;
import com.grafanalab.orders.service.port.PaymentGateway;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * ============================================================================
 * LE CABLAGE : le SEUL fichier qui connait a la fois Spring et les impls.
 * ============================================================================
 * Aucune annotation @Service / @Component / @Repository n'existe dans ce projet.
 * Tout est cable ici, explicitement, par des @Bean.
 *
 * Pourquoi ce style plutot que le classpath scanning ?
 *  + le graphe de dependances est LISIBLE : il tient sur un ecran ;
 *  + le code metier reste utilisable hors Spring (batch, tests, lib) ;
 *  + on choisit l'implementation d'un port en changeant UNE ligne ;
 *  + pas de surprise de type "pourquoi Spring a injecte cette impl la ?".
 *  - un peu plus verbeux. C'est un tres bon echange.
 *
 * C'est aussi ce qui vous permet de dire en entretien : "ma logique metier n'a
 * aucune dependance framework, je peux la porter ou la tester en isolation".
 * ============================================================================
 */
@Configuration
public class BeanConfiguration {

    /**
     * Clock injectee partout plutot que Instant.now() en dur.
     * En test on passe Clock.fixed(...) et le temps devient deterministe.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ChaosSettings chaosSettings() {
        return new ChaosSettings();
    }

    /** On retourne le TYPE CONCRET ici pour pouvoir lire size() dans la gauge. */
    @Bean
    public InMemoryOrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    @Bean
    public PaymentGateway paymentGateway(ChaosSettings chaos) {
        return new SimulatedPaymentGateway(chaos);
    }

    @Bean
    public OrderMetrics orderMetrics(MeterRegistry registry) {
        return new MicrometerOrderMetrics(registry);
    }

    /**
     * Le service. Notez le type de retour : l'INTERFACE.
     * Pour brancher une autre implementation, on ne change que cette ligne.
     */
    @Bean
    public OrderService orderService(OrderRepository repository,
                                     PaymentGateway paymentGateway,
                                     OrderMetrics metrics,
                                     Clock clock) {
        return new DefaultOrderService(repository, paymentGateway, metrics, clock);
    }

    /**
     * ------------------------------------------------------------------------
     * GAUGES METIER : "combien de commandes dans chaque etat, maintenant ?"
     * ------------------------------------------------------------------------
     * Une gauge n'est PAS poussee : Micrometer appelle la lambda a chaque scrape
     * (toutes les 15 s ici). La fonction doit donc etre RAPIDE et sans effet de
     * bord. Ne mettez jamais une requete SQL lourde dans une gauge : vous
     * transformeriez votre monitoring en source de panne.
     *
     * Une gauge par statut -> 5 series. En PromQL :
     *   sum(orders_in_status) by (status)
     */
    // On retourne un MeterBinder et NON un MeterRegistry : Spring Boot appelle
    // automatiquement bindTo() sur chaque MeterBinder du contexte. Retourner un
    // MeterRegistry creerait un SECOND bean de ce type et rendrait toute
    // injection de MeterRegistry ambigue. Piege classique.
    @Bean
    public MeterBinder businessGauges(InMemoryOrderRepository repository) {
        return registry -> {
            for (OrderStatus status : OrderStatus.values()) {
                Gauge.builder("orders.in.status", repository, r -> r.countByStatus(status))
                        .tag("status", status.name())
                        .description("Nombre de commandes actuellement dans ce statut")
                        .register(registry);
            }
            Gauge.builder("orders.stored.total", repository, InMemoryOrderRepository::size)
                    .description("Nombre total de commandes en memoire")
                    .register(registry);
        };
    }
}
