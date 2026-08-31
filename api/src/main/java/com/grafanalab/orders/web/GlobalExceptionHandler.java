package com.grafanalab.orders.web;

import com.grafanalab.orders.domain.IllegalOrderStateException;
import com.grafanalab.orders.domain.OrderNotFoundException;
import com.grafanalab.orders.service.port.PaymentGateway.PaymentGatewayUnavailableException;
import com.grafanalab.orders.web.dto.OrderDtos.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * TRADUCTION EXCEPTION -> CODE HTTP : la piece qui rend le SLO credible.
 * ============================================================================
 * Le code HTTP n'est pas cosmetique : c'est LE label sur lequel toutes vos
 * alertes et tous vos SLO vont s'appuyer.
 *
 *   400 Bad Request  : le client a envoye n'importe quoi.        -> pas notre faute
 *   404 Not Found    : la ressource n'existe pas.                -> pas notre faute
 *   409 Conflict     : etat metier incompatible (deja payee).    -> pas notre faute
 *   503 Unavailable  : une dependance est tombee.                -> NOTRE probleme
 *   500 Internal     : bug non gere.                             -> NOTRE probleme
 *
 * Le SLO de disponibilite se calcule ensuite en PromQL sur les 5xx uniquement :
 *
 *   sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
 *   / sum(rate(http_server_requests_seconds_count[5m]))
 *
 * Si vous laissez toutes les erreurs partir en 500, votre budget d'erreur se
 * vide a cause des fautes de frappe de vos utilisateurs. C'est l'erreur la plus
 * frequente que vous verrez chez les clients.
 * ============================================================================
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException e) {
        // DEBUG et non ERROR : un 404 est un evenement normal. Logger en ERROR
        // remplirait Loki de bruit et rendrait vos alertes sur logs inutilisables.
        log.debug("commande introuvable orderId={} event=order_not_found", e.orderId());
        return build(HttpStatus.NOT_FOUND, "order_not_found", e.getMessage());
    }

    @ExceptionHandler(IllegalOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalOrderStateException e) {
        log.info("transition interdite message={} event=illegal_state", e.getMessage());
        return build(HttpStatus.CONFLICT, "illegal_order_state", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "validation_error", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", e.getMessage());
    }

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleGatewayDown(PaymentGatewayUnavailableException e) {
        // WARN + 503 : une dependance externe est tombee. C'est bien un incident,
        // mais on sait pourquoi. Une alerte dediee vaut mieux qu'une alerte "500".
        log.warn("dependance indisponible message={} event=dependency_unavailable", e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "payment_gateway_unavailable", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // ERROR + stack trace complete : c'est le seul cas qui merite de reveiller
        // quelqu'un. La stack part dans Loki et est cherchable.
        log.error("erreur inattendue event=unhandled_exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "une erreur interne est survenue");
    }

    /**
     * Ajoute le traceId dans la reponse pour permettre la correlation en support.
     *
     * On lit le MDC plutot que d'injecter un Tracer : Micrometer Tracing y place
     * automatiquement traceId et spanId pour chaque requete (c'est aussi ce que
     * lit logback-spring.xml). Resultat : cette classe n'a AUCUNE dependance sur
     * la librairie de tracing, et le test web n'a pas besoin d'un bean Tracer.
     * Meme principe que partout ailleurs dans ce projet : dependre du strict
     * minimum.
     */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        String traceId = MDC.get("traceId");
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, traceId != null ? traceId : "none", Instant.now()));
    }
}
