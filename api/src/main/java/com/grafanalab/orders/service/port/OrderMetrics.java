package com.grafanalab.orders.service.port;

import com.grafanalab.orders.domain.OrderStatus;

import java.math.BigDecimal;

/**
 * PORT de metriques METIER.
 *
 * ============================================================================
 * POURQUOI CETTE INTERFACE ? (point cle a savoir expliquer en entretien)
 * ============================================================================
 * Le service doit rester du "Java pur" : zero import Micrometer, zero import
 * Spring. Or on veut quand meme des metriques metier (CA encaisse, paniers
 * refuses...). Solution : on definit un port etroit, exprime dans le VOCABULAIRE
 * METIER ("orderPaid"), et l'adaptateur Micrometer traduit ca en compteurs.
 *
 * Benefices :
 *  1. Le service se teste avec un `RecordingOrderMetrics` (fake) et on ASSERTE
 *     que la metrique a bien ete emise. On teste donc l'observabilite elle-meme.
 *  2. Si l'entreprise migre Micrometer -> OpenTelemetry, seul l'adaptateur change.
 *  3. On evite de sprinkler des `meterRegistry.counter(...)` partout dans le metier.
 *
 * Distinction fondamentale :
 *  - metriques TECHNIQUES (latence HTTP, CPU, GC) : fournies gratuitement par
 *    Actuator/Micrometer, on n'ecrit pas une ligne de code.
 *  - metriques METIER (commandes payees, CA) : personne ne peut les deviner a
 *    votre place. C'est la valeur ajoutee, et c'est ce port.
 * ============================================================================
 */
public interface OrderMetrics {

    /** Une commande vient d'etre creee. */
    void orderCreated(int itemCount, BigDecimal amount);

    /** Paiement accepte : on incremente le CA encaisse. */
    void orderPaid(BigDecimal amount);

    /** Paiement refuse par la banque (metier, PAS une panne). */
    void paymentDeclined(String reason);

    /** La passerelle de paiement est tombee (panne technique). */
    void paymentGatewayFailure();

    /** Changement d'etat generique, utile pour un diagramme de flux. */
    void statusTransition(OrderStatus from, OrderStatus to);
}
