package tn.ginf.smarthealthcare.orchestrator.clients;

import jakarta.xml.ws.WebServiceException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tn.ginf.smarthealthcare.orchestrator.workflow.ServiceIndisponibleException;

/**
 * Classe les erreurs des clients (P5.3, D54) : panne technique → {@link ServiceIndisponibleException} (réessayable),
 * tout le reste (SOAP Fault, 4xx, erreur GraphQL) est renvoyé tel quel.
 */
final class Pannes {

    private Pannes() {}

    /** Panne technique de RestClient (REST, GraphQL) ou du client CXF (SOAP) ; sinon l'erreur d'origine. */
    static RuntimeException traduire(String service, RuntimeException e) {
        if (e instanceof ResourceAccessException || e instanceof WebServiceException) {
            boolean envoyee = !connexionImpossible(e);
            return new ServiceIndisponibleException(service, envoyee,
                    envoyee ? "pas de réponse (" + cause(e) + ")" : "connexion impossible (" + cause(e) + ")", e);
        }
        if (e instanceof HttpServerErrorException http) {
            return new ServiceIndisponibleException(service, true, "HTTP " + http.getStatusCode().value(), e);
        }
        return e;
    }

    /** Vrai si l'appel n'a pas pu partir : le service n'a rien reçu, un nouvel essai ne crée pas de doublon. */
    static boolean connexionImpossible(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof ConnectException || c instanceof HttpConnectTimeoutException
                    || c instanceof UnknownHostException || c instanceof NoRouteToHostException) {
                return true;
            }
        }
        return false;
    }

    private static String cause(Throwable e) {
        Throwable racine = e;
        while (racine.getCause() != null && racine.getCause() != racine) {
            racine = racine.getCause();
        }
        String message = racine.getMessage();
        return racine.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : " : " + message);
    }
}
