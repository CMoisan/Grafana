package com.grafanalab.orders.service;

import com.grafanalab.orders.domain.IllegalOrderStateException;
import com.grafanalab.orders.domain.Order;
import com.grafanalab.orders.domain.OrderLine;
import com.grafanalab.orders.domain.OrderNotFoundException;
import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.service.command.CreateOrderCommand;
import com.grafanalab.orders.service.port.OrderMetrics;
import com.grafanalab.orders.service.port.OrderRepository;
import com.grafanalab.orders.service.port.PaymentGateway;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * ============================================================================
 * IMPLEMENTATION DU SERVICE - 100% JAVA PUR
 * ============================================================================
 * Regardez les imports ci-dessus : PAS UN SEUL `org.springframework.*`,
 * PAS UN SEUL `io.micrometer.*`. C'etait l'objectif.
 *
 * Toutes les dependances arrivent par le CONSTRUCTEUR, sous forme d'interfaces.
 * Le cablage Spring se fait ailleurs (config/BeanConfiguration.java).
 *
 * Ce que ca vous donne :
 *  - un test unitaire demarre en ~5 ms au lieu de ~3 s (pas de contexte Spring) ;
 *  - `Clock` injecte => le temps est deterministe dans les tests ;
 *  - `Supplier<String> idGenerator` injecte => les IDs sont deterministes aussi.
 *    (Regle : toute source d'indeterminisme - horloge, aleatoire, UUID, reseau -
 *     doit etre injectee. C'est la difference entre "testable" et "flaky".)
 * ============================================================================
 */
public class DefaultOrderService implements OrderService {

    private final OrderRepository repository;
    private final PaymentGateway paymentGateway;
    private final OrderMetrics metrics;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    /** Constructeur principal : tout est explicite, tout est remplacable. */
    public DefaultOrderService(OrderRepository repository,
                               PaymentGateway paymentGateway,
                               OrderMetrics metrics,
                               Clock clock,
                               Supplier<String> idGenerator) {
        this.repository = repository;
        this.paymentGateway = paymentGateway;
        this.metrics = metrics;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /** Constructeur de confort pour la prod : UUID aleatoires. */
    public DefaultOrderService(OrderRepository repository,
                               PaymentGateway paymentGateway,
                               OrderMetrics metrics,
                               Clock clock) {
        this(repository, paymentGateway, metrics, clock, () -> UUID.randomUUID().toString());
    }

    @Override
    public Order create(CreateOrderCommand command) {
        Instant now = clock.instant();

        List<OrderLine> lines = command.lines().stream()
                .map(l -> new OrderLine(l.sku(), l.quantity(), l.unitPrice()))
                .toList();

        Order order = new Order(idGenerator.get(), command.customerId(), lines, now);
        repository.save(order);

        // Metrique metier : combien d'articles par panier, quel montant.
        metrics.orderCreated(order.itemCount(), order.total());
        return order;
    }

    @Override
    public Order getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Override
    public List<Order> list(OrderStatus status, int limit) {
        // Garde-fou : on ne laisse jamais un client demander 1 million de lignes.
        // Sans ca, un seul appel peut faire exploser la latence p99 de TOUT le service
        // (effet "noisy neighbor" tres classique en incident).
        int safeLimit = Math.clamp(limit, 1, 500);
        return repository.findAll(status, safeLimit);
    }

    @Override
    public Order pay(String id) {
        Order order = getById(id);
        OrderStatus before = order.status();

        if (before != OrderStatus.PENDING) {
            // On leve AVANT d'appeler la banque : on ne double-debite pas un client.
            throw new IllegalOrderStateException(
                    "impossible de payer une commande au statut " + before);
        }

        PaymentGateway.PaymentResult result;
        try {
            result = paymentGateway.charge(order.id(), order.total());
        } catch (PaymentGateway.PaymentGatewayUnavailableException e) {
            // PANNE TECHNIQUE : on compte a part, on ne change pas l'etat de la
            // commande, et on laisse remonter -> HTTP 503.
            metrics.paymentGatewayFailure();
            throw e;
        }

        Instant now = clock.instant();
        if (result.approved()) {
            order.markPaid(result.reference(), now);
            metrics.orderPaid(order.total());
        } else {
            // REFUS METIER : ce n'est PAS une panne. La commande passe en
            // PAYMENT_FAILED et l'API repondra 200 avec le statut.
            order.markPaymentFailed(result.declineReason(), now);
            metrics.paymentDeclined(result.declineReason());
        }

        repository.save(order);
        metrics.statusTransition(before, order.status());
        return order;
    }

    @Override
    public Order cancel(String id) {
        Order order = getById(id);
        OrderStatus before = order.status();
        order.cancel(clock.instant());
        repository.save(order);
        metrics.statusTransition(before, order.status());
        return order;
    }

    @Override
    public Order ship(String id) {
        Order order = getById(id);
        OrderStatus before = order.status();
        order.ship(clock.instant());
        repository.save(order);
        metrics.statusTransition(before, order.status());
        return order;
    }

    @Override
    public void delete(String id) {
        if (!repository.deleteById(id)) {
            throw new OrderNotFoundException(id);
        }
    }
}
