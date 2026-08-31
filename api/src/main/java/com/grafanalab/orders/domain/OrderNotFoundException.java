package com.grafanalab.orders.domain;

/** Commande inexistante -> HTTP 404 (donc hors du budget d'erreur SLO). */
public class OrderNotFoundException extends RuntimeException {
    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("commande introuvable : " + orderId);
        this.orderId = orderId;
    }

    public String orderId() { return orderId; }
}
