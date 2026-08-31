package com.grafanalab.orders.service.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * Commande d'entree du cas d'usage "creer une commande".
 *
 * Pourquoi pas reutiliser le DTO HTTP ? Parce que le service ne doit rien savoir
 * du transport. Demain on ajoute un consumer Kafka : il construit le meme objet.
 * Le controller fait la traduction DTO HTTP -> Command.
 */
public record CreateOrderCommand(String customerId, List<Line> lines) {

    public record Line(String sku, int quantity, BigDecimal unitPrice) { }
}
