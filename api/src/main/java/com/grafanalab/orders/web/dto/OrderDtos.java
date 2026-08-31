package com.grafanalab.orders.web.dto;

import com.grafanalab.orders.domain.Order;
import com.grafanalab.orders.service.command.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO du transport HTTP, regroupes dans un seul fichier pour la lisibilite du lab.
 *
 * POURQUOI DES DTO SEPARES DU DOMAINE ?
 *  - le contrat HTTP evolue moins vite que le modele interne (et inversement) ;
 *  - on ne veut PAS que Jackson serialise accidentellement un champ interne ;
 *  - la validation (jakarta.validation) est une preoccupation du transport,
 *    pas du domaine. Le domaine, lui, garde ses propres invariants.
 *
 * Consequence observabilite : une requete invalide est rejetee ICI, avant le
 * service, et produit un HTTP 400. Un 400 ne doit jamais compter comme une
 * erreur de VOTRE service dans le SLO.
 */
public final class OrderDtos {

    private OrderDtos() { }

    /** Corps de POST /api/orders */
    public record CreateOrderRequest(
            @NotBlank(message = "customerId obligatoire")
            @Size(max = 64)
            String customerId,

            @NotEmpty(message = "au moins une ligne est requise")
            @Size(max = 50, message = "50 lignes maximum par commande")
            List<@Valid LineRequest> lines
    ) {
        /** Traduction DTO -> commande de service : la frontiere est ici. */
        public CreateOrderCommand toCommand() {
            return new CreateOrderCommand(
                    customerId,
                    lines.stream()
                            .map(l -> new CreateOrderCommand.Line(l.sku(), l.quantity(), l.unitPrice()))
                            .toList());
        }
    }

    public record LineRequest(
            @NotBlank @Size(max = 64) String sku,
            @Min(value = 1, message = "quantity doit etre >= 1") int quantity,
            @DecimalMin(value = "0.0", message = "unitPrice doit etre >= 0") BigDecimal unitPrice
    ) { }

    /** Reponse renvoyee au client. */
    public record OrderResponse(
            String id,
            String customerId,
            String status,
            BigDecimal total,
            int itemCount,
            List<LineResponse> lines,
            Instant createdAt,
            Instant updatedAt,
            String paymentReference,
            String failureReason
    ) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.id(),
                    order.customerId(),
                    order.status().name(),
                    order.total(),
                    order.itemCount(),
                    order.lines().stream()
                            .map(l -> new LineResponse(l.sku(), l.quantity(), l.unitPrice(), l.subtotal()))
                            .toList(),
                    order.createdAt(),
                    order.updatedAt(),
                    order.paymentReference(),
                    order.failureReason());
        }
    }

    public record LineResponse(String sku, int quantity, BigDecimal unitPrice, BigDecimal subtotal) { }

    /**
     * Reponse d'erreur normalisee.
     * `traceId` est renvoye au client : c'est LE detail qui change tout en
     * support. L'utilisateur donne ce traceId, et vous retrouvez instantanement
     * la requete dans Loki avec  {app="orders-api"} | json | traceId = "abc..."
     */
    public record ErrorResponse(String error, String message, String traceId, Instant timestamp) { }
}
