package com.grafanalab.orders.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grafanalab.orders.domain.IllegalOrderStateException;
import com.grafanalab.orders.domain.Order;
import com.grafanalab.orders.domain.OrderLine;
import com.grafanalab.orders.domain.OrderNotFoundException;
import com.grafanalab.orders.infrastructure.chaos.ChaosSettings;
import com.grafanalab.orders.service.OrderService;
import com.grafanalab.orders.service.port.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================================
 * TEST DE LA COUCHE WEB - on verifie LA TRADUCTION EXCEPTION -> CODE HTTP
 * ============================================================================
 * @WebMvcTest ne demarre que la couche web (pas le service reel, pas la config
 * metier). On y branche un stub d'OrderService.
 *
 * Pourquoi ces tests comptent AUTANT que les tests metier :
 * les codes HTTP sont la matiere premiere de vos SLO. Si un jour quelqu'un
 * transforme un 404 en 500, votre budget d'erreur se videra sans qu'aucune
 * alerte de test ne se declenche. Ces assertions verrouillent votre SLO.
 * ============================================================================
 */
@WebMvcTest(controllers = {OrderController.class, ChaosController.class})
@Import(OrderControllerTest.StubConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void creationValide_retourne201EtLocation() throws Exception {
        var body = Map.of(
                "customerId", "cust-1",
                "lines", List.of(Map.of("sku", "SKU-A", "quantity", 2, "unitPrice", "10.00")));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("cust-1"))
                .andExpect(jsonPath("$.itemCount").value(2));
    }

    @Test
    void quantiteNegative_retourne400_pas500() throws Exception {
        var body = Map.of(
                "customerId", "cust-1",
                "lines", List.of(Map.of("sku", "SKU-A", "quantity", 0, "unitPrice", "10.00")));

        // 400 = faute du client -> ne consomme PAS le budget d'erreur du SLO.
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void commandeInconnue_retourne404() throws Exception {
        mockMvc.perform(get("/api/orders/inconnue"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("order_not_found"));
    }

    @Test
    void dejaPayee_retourne409_pas500() throws Exception {
        mockMvc.perform(post("/api/orders/conflit/pay"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("illegal_order_state"));
    }

    @Test
    void passerelleEnPanne_retourne503() throws Exception {
        // 503 = notre probleme -> DOIT consommer le budget d'erreur et alerter.
        mockMvc.perform(post("/api/orders/panne/pay"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("payment_gateway_unavailable"));
    }

    /**
     * Stub du service : ecrit a la main, sans Mockito.
     * Possible uniquement parce que OrderService est une INTERFACE.
     */
    @TestConfiguration
    static class StubConfig {

        @Bean
        ChaosSettings chaosSettings() {
            return new ChaosSettings();
        }

        @Bean
        OrderService orderService() {
            return new OrderService() {
                @Override
                public Order create(com.grafanalab.orders.service.command.CreateOrderCommand c) {
                    return new Order("order-1", c.customerId(),
                            c.lines().stream()
                                    .map(l -> new OrderLine(l.sku(), l.quantity(), l.unitPrice()))
                                    .toList(),
                            Instant.parse("2026-01-15T10:00:00Z"));
                }

                @Override
                public Order getById(String id) {
                    throw new OrderNotFoundException(id);
                }

                @Override
                public List<Order> list(com.grafanalab.orders.domain.OrderStatus s, int limit) {
                    return List.of();
                }

                @Override
                public Order pay(String id) {
                    if ("panne".equals(id)) {
                        throw new PaymentGateway.PaymentGatewayUnavailableException("down");
                    }
                    throw new IllegalOrderStateException("deja payee");
                }

                @Override
                public Order cancel(String id) {
                    throw new OrderNotFoundException(id);
                }

                @Override
                public Order ship(String id) {
                    throw new OrderNotFoundException(id);
                }

                @Override
                public void delete(String id) {
                    throw new OrderNotFoundException(id);
                }
            };
        }
    }
}
