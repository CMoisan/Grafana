package com.grafanalab.orders.web;

import com.grafanalab.orders.domain.Order;
import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.infrastructure.chaos.ChaosSettings;
import com.grafanalab.orders.service.OrderService;
import com.grafanalab.orders.web.dto.OrderDtos.CreateOrderRequest;
import com.grafanalab.orders.web.dto.OrderDtos.OrderResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ============================================================================
 * CONTROLLER : la couche la plus FINE possible.
 * ============================================================================
 * Ses seules responsabilites :
 *   1. deserialiser + valider l'entree,
 *   2. traduire DTO -> Command,
 *   3. appeler le service,
 *   4. traduire le resultat en code HTTP.
 * Aucune regle metier ici. Si vous ecrivez un `if` metier dans un controller,
 * il est au mauvais endroit : il ne sera pas testable sans MockMvc.
 *
 * ------------------------ POINT OBSERVABILITE CLE ---------------------------
 * Notez `@RequestMapping("/api/orders")` + `@GetMapping("/{id}")`.
 * Spring/Micrometer publie automatiquement la metrique :
 *
 *   http_server_requests_seconds_bucket{uri="/api/orders/{id}", method="GET", status="200"}
 *
 * Le label `uri` contient le TEMPLATE ("/api/orders/{id}"), pas l'URL reelle
 * ("/api/orders/9f3b..."). C'est fondamental : sinon chaque commande creerait
 * une serie temporelle -> cardinalite infinie -> Mimir sature.
 * Si un jour vous voyez des milliers de series sur `uri`, cherchez un endpoint
 * qui construit son chemin a la main.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;   // <-- l'INTERFACE, jamais l'impl
    private final ChaosSettings chaos;

    public OrderController(OrderService orderService, ChaosSettings chaos) {
        this.orderService = orderService;
        this.chaos = chaos;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        maybeExplode();
        Order order = orderService.create(request.toCommand());
        log.info("commande creee orderId={} customerId={} total={} event=order_created",
                order.id(), order.customerId(), order.total());
        // 201 + Location : le REST correct. k6 lira ce header pour chainer les appels.
        return ResponseEntity.created(URI.create("/api/orders/" + order.id()))
                .body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable String id) {
        maybeExplode();
        return OrderResponse.from(orderService.getById(id));
    }

    @GetMapping
    public List<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "20") int limit) {
        maybeExplode();
        return orderService.list(status, limit).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping("/{id}/pay")
    public OrderResponse pay(@PathVariable String id) {
        maybeExplode();
        return OrderResponse.from(orderService.pay(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable String id) {
        return OrderResponse.from(orderService.cancel(id));
    }

    @PostMapping("/{id}/ship")
    public OrderResponse ship(@PathVariable String id) {
        return OrderResponse.from(orderService.ship(id));
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        orderService.delete(id);
    }

    /**
     * Injection d'erreurs 5xx pilotee par le chaos panel.
     * Volontairement dans le controller (pas le service) : c'est une panne
     * d'INFRASTRUCTURE simulee, pas une regle metier. Le service reste pur.
     */
    private void maybeExplode() {
        int rate = chaos.httpErrorRatePercent();
        if (rate > 0 && ThreadLocalRandom.current().nextInt(100) < rate) {
            throw new IllegalStateException("panne simulee par le chaos panel");
        }
    }
}
