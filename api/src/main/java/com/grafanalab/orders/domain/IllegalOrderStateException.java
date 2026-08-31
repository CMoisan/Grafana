package com.grafanalab.orders.domain;

/**
 * Transition d'etat interdite (ex : payer une commande deja payee).
 *
 * OBSERVABILITE : ce cas doit devenir un HTTP 409, PAS un 500.
 * Un 4xx = "le client a fait une erreur" -> ne doit pas declencher d'alerte SRE.
 * Un 5xx = "notre service est casse" -> doit declencher une alerte.
 * Melanger les deux est l'erreur n°1 quand on construit un SLO.
 */
public class IllegalOrderStateException extends RuntimeException {
    public IllegalOrderStateException(String message) {
        super(message);
    }
}
