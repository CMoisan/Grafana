package com.grafanalab.orders.service.port;

import com.grafanalab.orders.domain.Order;
import com.grafanalab.orders.domain.OrderStatus;

import java.util.List;
import java.util.Optional;

/**
 * PORT de persistance (architecture hexagonale).
 *
 * Le mot important : c'est le SERVICE qui definit cette interface, pas la base de
 * donnees. L'implementation (InMemoryOrderRepository, JpaOrderRepository, ...) vit
 * dans `infrastructure` et DEPEND de cette interface. On inverse la dependance :
 *
 *      web  ---> service (pur Java)  <--- infrastructure
 *
 * Consequence concrete pour les tests : on remplace ce port par une fausse
 * implementation en 20 lignes, sans Mockito, sans Spring, sans H2.
 */
public interface OrderRepository {

    /** Insere ou remplace. Idempotent sur l'id. */
    void save(Order order);

    Optional<Order> findById(String id);

    /** Liste filtrable. `status == null` => pas de filtre. */
    List<Order> findAll(OrderStatus status, int limit);

    /** @return true si la commande existait et a ete supprimee. */
    boolean deleteById(String id);

    /** Compte par statut : sert a exposer une gauge metier dans Prometheus. */
    long countByStatus(OrderStatus status);
}
