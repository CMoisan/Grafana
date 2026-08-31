package com.grafanalab.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Agregat "Commande" : c'est LUI qui porte les regles de transition d'etat.
 *
 * Choix de conception : objet MUTABLE mais dont les transitions passent
 * obligatoirement par des methodes metier (pay(), cancel(), ship()).
 * On ne peut donc pas mettre une commande dans un etat incoherent depuis
 * l'exterieur -> les tests unitaires du service n'ont pas a re-verifier ca.
 */
public class Order {

    private final String id;
    private final String customerId;
    private final List<OrderLine> lines;
    private final Instant createdAt;

    private OrderStatus status;
    private Instant updatedAt;
    private String paymentReference;
    private String failureReason;

    public Order(String id, String customerId, List<OrderLine> lines, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id obligatoire");
        this.customerId = Objects.requireNonNull(customerId, "customerId obligatoire");
        Objects.requireNonNull(lines, "lines obligatoire");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("une commande doit avoir au moins une ligne");
        }
        // List.copyOf -> copie defensive immuable : personne ne peut modifier les lignes apres coup.
        this.lines = List.copyOf(lines);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt obligatoire");
        this.updatedAt = createdAt;
        this.status = OrderStatus.PENDING;
    }

    /** Somme des lignes. Utilise comme metrique metier (business KPI) dans Grafana. */
    public BigDecimal total() {
        return lines.stream()
                .map(OrderLine::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Nombre total d'articles, toutes lignes confondues. */
    public int itemCount() {
        return lines.stream().mapToInt(OrderLine::quantity).sum();
    }

    // ------------------------------------------------------------------
    // Transitions d'etat. Chacune leve une exception si la transition est
    // interdite : ces exceptions deviennent des HTTP 409 dans le controller,
    // et sont comptees comme "erreurs metier" (a distinguer des HTTP 5xx !).
    // ------------------------------------------------------------------

    public void markPaid(String paymentReference, Instant now) {
        requireStatus(OrderStatus.PENDING, "payer");
        this.status = OrderStatus.PAID;
        this.paymentReference = paymentReference;
        this.updatedAt = now;
    }

    public void markPaymentFailed(String reason, Instant now) {
        requireStatus(OrderStatus.PENDING, "echouer le paiement");
        this.status = OrderStatus.PAYMENT_FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalOrderStateException("impossible d'annuler une commande deja expediee");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = now;
    }

    public void ship(Instant now) {
        requireStatus(OrderStatus.PAID, "expedier");
        this.status = OrderStatus.SHIPPED;
        this.updatedAt = now;
    }

    private void requireStatus(OrderStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalOrderStateException(
                    "impossible de " + action + " une commande au statut " + this.status
                            + " (statut attendu : " + expected + ")");
        }
    }

    public String id() { return id; }
    public String customerId() { return customerId; }
    public List<OrderLine> lines() { return lines; }
    public OrderStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String paymentReference() { return paymentReference; }
    public String failureReason() { return failureReason; }
}
