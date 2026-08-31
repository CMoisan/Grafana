package com.grafanalab.orders.infrastructure.persistence;

import com.grafanalab.orders.domain.Order;
import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.service.port.OrderRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADAPTATEUR de persistance : une simple Map concurrente.
 *
 * Pourquoi pas une vraie base ? Parce que l'objet du lab est Grafana, pas JPA.
 * Une base ajouterait un pod, des migrations, et surtout du bruit dans les
 * dashboards. Ici la latence vient uniquement de ce qu'on simule, donc les
 * dashboards restent lisibles et pedagogiques.
 *
 * ATTENTION : l'etat est perdu au redemarrage du pod. C'est en soi une bonne
 * demo ("quand k8s redemarre le pod, la gauge repart a zero") pour expliquer
 * la difference entre un counter, une gauge, et pourquoi on utilise rate().
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> store = new ConcurrentHashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findAll(OrderStatus status, int limit) {
        return store.values().stream()
                .filter(o -> status == null || o.status() == status)
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public boolean deleteById(String id) {
        return store.remove(id) != null;
    }

    @Override
    public long countByStatus(OrderStatus status) {
        return store.values().stream().filter(o -> o.status() == status).count();
    }

    /** Taille totale : lue par une gauge Micrometer. */
    public int size() {
        return store.size();
    }
}
