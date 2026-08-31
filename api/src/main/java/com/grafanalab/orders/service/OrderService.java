package com.grafanalab.orders.service;

import com.grafanalab.orders.domain.Order;
import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.service.command.CreateOrderCommand;

import java.util.List;

/**
 * INTERFACE du service applicatif : le contrat des cas d'usage.
 *
 * Le controller ne depend QUE de cette interface. Consequences :
 *  - on peut tester le controller avec un `StubOrderService` (pas de Mockito lourd) ;
 *  - on peut brancher une implementation "cachee/resiliente" par decoration
 *    (ex : CachingOrderService qui delegue) sans toucher au controller ;
 *  - la signature documente le domaine, pas la technique.
 */
public interface OrderService {

    Order create(CreateOrderCommand command);

    /** @throws com.grafanalab.orders.domain.OrderNotFoundException si inconnue */
    Order getById(String id);

    List<Order> list(OrderStatus status, int limit);

    /** Declenche le paiement. @throws IllegalOrderStateException si deja payee. */
    Order pay(String id);

    Order cancel(String id);

    Order ship(String id);

    /** @throws com.grafanalab.orders.domain.OrderNotFoundException si inconnue */
    void delete(String id);
}
