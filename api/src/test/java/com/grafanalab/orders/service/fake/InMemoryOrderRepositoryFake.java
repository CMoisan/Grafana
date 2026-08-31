package com.grafanalab.orders.service.fake;

import com.grafanalab.orders.domain.Order;
import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.service.port.OrderRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FAKE (pas un mock) : une vraie implementation, simple, en memoire.
 *
 * DIFFERENCE A CONNAITRE (question d'entretien classique) :
 *  - stub  : renvoie des valeurs figees ;
 *  - mock  : verifie que telle methode a ete appelee (couplage au COMMENT) ;
 *  - fake  : implementation reelle mais simplifiee (couplage au QUOI).
 *
 * Preferer les fakes : un test qui verifie `verify(repo).save(any())` casse des
 * qu'on refactorise, meme si le comportement est identique. Un fake permet
 * d'asserter sur l'ETAT FINAL, qui est ce qui compte vraiment.
 *
 * Ce fake existe uniquement parce que le service depend d'une INTERFACE.
 * C'est le retour sur investissement concret de l'architecture hexagonale.
 */
public class InMemoryOrderRepositoryFake implements OrderRepository {

    private final Map<String, Order> store = new LinkedHashMap<>();

    /** Compteur d'appels : utile pour verifier qu'on ne sauvegarde pas 3 fois. */
    public int saveCount = 0;

    @Override
    public void save(Order order) {
        saveCount++;
        store.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findAll(OrderStatus status, int limit) {
        List<Order> result = new ArrayList<>();
        for (Order o : store.values()) {
            if (status == null || o.status() == status) {
                result.add(o);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public boolean deleteById(String id) {
        return store.remove(id) != null;
    }

    @Override
    public long countByStatus(OrderStatus status) {
        return store.values().stream().filter(o -> o.status() == status).count();
    }
}
