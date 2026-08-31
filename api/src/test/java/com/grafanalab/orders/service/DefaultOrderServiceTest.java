package com.grafanalab.orders.service;

import com.grafanalab.orders.domain.IllegalOrderStateException;
import com.grafanalab.orders.domain.Order;
import com.grafanalab.orders.domain.OrderNotFoundException;
import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.service.command.CreateOrderCommand;
import com.grafanalab.orders.service.fake.InMemoryOrderRepositoryFake;
import com.grafanalab.orders.service.fake.RecordingOrderMetrics;
import com.grafanalab.orders.service.fake.ScriptedPaymentGateway;
import com.grafanalab.orders.service.port.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * TESTS UNITAIRES DU SERVICE - AUCUN SPRING, AUCUN MOCKITO
 * ============================================================================
 * Lancez-les : la suite complete tourne en quelques dizaines de millisecondes.
 * Comparez avec un @SpringBootTest (2 a 5 secondes de demarrage). Sur 500 tests
 * la difference represente des dizaines de minutes de CI par jour.
 *
 * Ce que ces tests demontrent, et que vous pourrez raconter en entretien :
 *  1. le temps est deterministe (Clock.fixed) ;
 *  2. les IDs sont deterministes (idGenerator injecte) ;
 *  3. on teste aussi les METRIQUES emises, pas seulement le resultat metier ;
 *  4. on distingue explicitement panne technique et refus metier.
 * ============================================================================
 */
class DefaultOrderServiceTest {

    /** Horloge figee : Instant.now() ne rentre jamais dans le test. */
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryOrderRepositoryFake repository;
    private ScriptedPaymentGateway gateway;
    private RecordingOrderMetrics metrics;
    private DefaultOrderService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepositoryFake();
        gateway = new ScriptedPaymentGateway();
        metrics = new RecordingOrderMetrics();
        AtomicInteger seq = new AtomicInteger();
        // Generateur d'ID previsible : order-1, order-2, ...
        service = new DefaultOrderService(repository, gateway, metrics, fixedClock,
                () -> "order-" + seq.incrementAndGet());
    }

    private CreateOrderCommand sampleCommand() {
        return new CreateOrderCommand("cust-42", List.of(
                new CreateOrderCommand.Line("SKU-A", 2, new BigDecimal("10.00")),
                new CreateOrderCommand.Line("SKU-B", 1, new BigDecimal("5.50"))));
    }

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("cree une commande PENDING avec le bon total et l'heure figee")
        void createsPendingOrder() {
            Order order = service.create(sampleCommand());

            assertThat(order.id()).isEqualTo("order-1");
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
            // 2 x 10.00 + 1 x 5.50 = 25.50
            assertThat(order.total()).isEqualByComparingTo("25.50");
            assertThat(order.itemCount()).isEqualTo(3);
            assertThat(order.createdAt()).isEqualTo(NOW);
            assertThat(repository.findById("order-1")).isPresent();
        }

        @Test
        @DisplayName("emet la metrique metier orders.created")
        void emitsCreationMetric() {
            service.create(sampleCommand());
            // On teste l'observabilite comme n'importe quelle fonctionnalite.
            assertThat(metrics.created).hasSize(1);
            assertThat(metrics.created.get(0)).isEqualByComparingTo("25.50");
        }
    }

    @Nested
    @DisplayName("paiement")
    class Payment {

        @Test
        @DisplayName("paiement accepte : statut PAID, reference stockee, CA compte")
        void approvedPayment() {
            Order created = service.create(sampleCommand());
            gateway.thenApprove("pay_abc");

            Order paid = service.pay(created.id());

            assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
            assertThat(paid.paymentReference()).isEqualTo("pay_abc");
            assertThat(gateway.lastAmountCharged).isEqualByComparingTo("25.50");
            assertThat(metrics.paid).hasSize(1);
            assertThat(metrics.transitions).containsExactly("PENDING->PAID");
        }

        @Test
        @DisplayName("refus banque : PAYMENT_FAILED, pas d'exception, motif compte")
        void declinedPayment() {
            Order created = service.create(sampleCommand());
            gateway.thenDecline("insufficient_funds");

            Order result = service.pay(created.id());

            // Point cle : un refus n'est PAS une erreur technique.
            // L'API repondra 200. Le SLO de disponibilite n'est pas impacte.
            assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            assertThat(result.failureReason()).isEqualTo("insufficient_funds");
            assertThat(metrics.declineReasons).containsExactly("insufficient_funds");
            assertThat(metrics.paid).isEmpty();
        }

        @Test
        @DisplayName("panne passerelle : exception propagee, commande inchangee")
        void gatewayDown() {
            Order created = service.create(sampleCommand());
            gateway.thenFail();

            assertThatThrownBy(() -> service.pay(created.id()))
                    .isInstanceOf(PaymentGateway.PaymentGatewayUnavailableException.class);

            // La commande reste PENDING : on pourra reessayer sans double debit.
            assertThat(repository.findById(created.id()).orElseThrow().status())
                    .isEqualTo(OrderStatus.PENDING);
            assertThat(metrics.gatewayFailures).isEqualTo(1);
        }

        @Test
        @DisplayName("double paiement refuse SANS rappeler la banque")
        void noDoubleCharge() {
            Order created = service.create(sampleCommand());
            gateway.thenApprove("pay_abc");
            service.pay(created.id());

            assertThatThrownBy(() -> service.pay(created.id()))
                    .isInstanceOf(IllegalOrderStateException.class);

            // LE test important : la banque n'a ete appelee qu'une seule fois.
            // Un bug ici, c'est un client debite deux fois.
            assertThat(gateway.chargeCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("lecture et suppression")
    class ReadAndDelete {

        @Test
        @DisplayName("commande inconnue -> OrderNotFoundException (futur HTTP 404)")
        void unknownOrder() {
            assertThatThrownBy(() -> service.getById("nope"))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("la limite est bornee pour proteger la latence du service")
        void limitIsClamped() {
            for (int i = 0; i < 10; i++) {
                service.create(sampleCommand());
            }
            // Meme si le client demande 1 000 000, on ne depasse pas 500.
            assertThat(service.list(null, 1_000_000)).hasSize(10);
            // Et une limite absurde a 0 est ramenee a 1, pas a "tout".
            assertThat(service.list(null, 0)).hasSize(1);
        }

        @Test
        @DisplayName("suppression d'une commande inexistante -> 404")
        void deleteUnknown() {
            assertThatThrownBy(() -> service.delete("nope"))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }
}
