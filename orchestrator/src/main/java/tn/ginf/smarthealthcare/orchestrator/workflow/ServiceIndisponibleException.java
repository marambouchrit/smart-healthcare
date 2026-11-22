package tn.ginf.smarthealthcare.orchestrator.workflow;

/**
 * Panne technique d'un service (réseau, délai dépassé, HTTP 5xx), levée par les clients SOAP, REST et GraphQL
 * (P5.3, D54). Les erreurs métier (SOAP Fault, 4xx, erreurs GraphQL) ne sont jamais de ce type et ne sont pas
 * réessayées.
 *
 * <p>{@code requeteEnvoyee} vaut {@code false} seulement si l'appel n'a pas pu partir (connexion refusée ou
 * impossible) : le service n'a rien fait, on peut réessayer même une opération qui crée quelque chose.
 */
public class ServiceIndisponibleException extends RuntimeException {

    private final String service;
    private final boolean requeteEnvoyee;

    public ServiceIndisponibleException(String service, boolean requeteEnvoyee, String message, Throwable cause) {
        super(service + " indisponible : " + message, cause);
        this.service = service;
        this.requeteEnvoyee = requeteEnvoyee;
    }

    public String service() {
        return service;
    }

    public boolean requeteEnvoyee() {
        return requeteEnvoyee;
    }
}
