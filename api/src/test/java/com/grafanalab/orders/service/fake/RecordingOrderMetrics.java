package com.grafanalab.orders.service.fake;

import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.service.port.OrderMetrics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake de metriques : il ENREGISTRE ce qui a ete emis.
 *
 * Interet enorme et souvent oublie : on peut ecrire un test qui echoue si
 * quelqu'un supprime une metrique metier. L'observabilite devient une
 * fonctionnalite TESTEE, pas un effet de bord qu'on decouvre casse en prod
 * six mois plus tard quand le dashboard est vide.
 *
 * A citer en entretien : "chez nous, casser une metrique casse le build".
 */
public class RecordingOrderMetrics implements OrderMetrics {

    public final List<BigDecimal> created = new ArrayList<>();
    public final List<BigDecimal> paid = new ArrayList<>();
    public final List<String> declineReasons = new ArrayList<>();
    public int gatewayFailures = 0;
    public final List<String> transitions = new ArrayList<>();

    @Override
    public void orderCreated(int itemCount, BigDecimal amount) {
        created.add(amount);
    }

    @Override
    public void orderPaid(BigDecimal amount) {
        paid.add(amount);
    }

    @Override
    public void paymentDeclined(String reason) {
        declineReasons.add(reason);
    }

    @Override
    public void paymentGatewayFailure() {
        gatewayFailures++;
    }

    @Override
    public void statusTransition(OrderStatus from, OrderStatus to) {
        transitions.add(from + "->" + to);
    }
}
