package com.grafanalab.orders.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Une ligne de commande. `record` Java = immuable, equals/hashCode/toString generes.
 *
 * NOTE : aucune annotation Spring / Jackson ici. Le domaine reste du Java pur,
 * il ne connait ni le framework web, ni la base de donnees. C'est ce qui permet
 * de le tester sans demarrer de contexte Spring (tests en millisecondes).
 */
public record OrderLine(String sku, int quantity, BigDecimal unitPrice) {

    /** Le constructeur compact d'un record : ideal pour les invariants metier. */
    public OrderLine {
        Objects.requireNonNull(sku, "sku obligatoire");
        Objects.requireNonNull(unitPrice, "unitPrice obligatoire");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku ne peut pas etre vide");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity doit etre > 0");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice ne peut pas etre negatif");
        }
    }

    /** Montant de la ligne. */
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
