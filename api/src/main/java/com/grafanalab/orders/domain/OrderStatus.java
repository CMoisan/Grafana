package com.grafanalab.orders.domain;

/**
 * Statuts possibles d'une commande.
 *
 * IMPORTANT (observabilite) : ce enum finira en LABEL Prometheus (`status="PAID"`).
 * Regle d'or : un label ne doit avoir qu'un nombre BORNE et PETIT de valeurs.
 * Ici 5 valeurs -> parfait. Si on mettait l'ID de commande en label, on creerait
 * une serie temporelle par commande = "high cardinality" = explosion de Mimir.
 */
public enum OrderStatus {
    /** Creee, pas encore payee. */
    PENDING,
    /** Paiement accepte par la passerelle. */
    PAID,
    /** Paiement refuse (fonds, fraude...). */
    PAYMENT_FAILED,
    /** Annulee par le client avant paiement. */
    CANCELLED,
    /** Expediee. */
    SHIPPED
}
