package tn.ginf.smarthealthcare.rest.web;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erreurs métier au format Problem Details (RFC 9457), avec les « type » du contrat OpenAPI :
 * /problemes/non-trouve (404), /problemes/aucun-creneau, /problemes/creneau-indisponible,
 * /problemes/rendez-vous-annule, /problemes/transition-interdite (409), /problemes/requete-invalide (400).
 * Hériter de ResponseEntityExceptionHandler fournit le 400 de validation au même format.
 */
@RestControllerAdvice
public class Problemes extends ResponseEntityExceptionHandler {

    public static class NonTrouveException extends RuntimeException {
        public NonTrouveException(String message) { super(message); }
    }

    public static class AucunCreneauException extends RuntimeException {
        public AucunCreneauException(String message) { super(message); }
    }

    /** 409 : conflit avec l'état courant (créneau déjà réservé, rendez-vous annulé…). */
    public static class ConflitException extends RuntimeException {
        final String type;
        final String titre;
        public ConflitException(String type, String titre, String message) {
            super(message);
            this.type = type;
            this.titre = titre;
        }
    }

    /** 400 : requête syntaxiquement correcte mais refusée par une règle (ex. PATCH vide). */
    public static class RequeteInvalideException extends RuntimeException {
        public RequeteInvalideException(String message) { super(message); }
    }

    @ExceptionHandler(ConflitException.class)
    ProblemDetail conflit(ConflitException e) {
        return probleme(HttpStatus.CONFLICT, e.type, e.titre, e.getMessage());
    }

    @ExceptionHandler(RequeteInvalideException.class)
    ProblemDetail requeteInvalide(RequeteInvalideException e) {
        return probleme(HttpStatus.BAD_REQUEST, "requete-invalide", "Requête invalide", e.getMessage());
    }

    @ExceptionHandler(NonTrouveException.class)
    ProblemDetail nonTrouve(NonTrouveException e) {
        return probleme(HttpStatus.NOT_FOUND, "non-trouve", "Ressource introuvable", e.getMessage());
    }

    @ExceptionHandler(AucunCreneauException.class)
    ProblemDetail aucunCreneau(AucunCreneauException e) {
        return probleme(HttpStatus.CONFLICT, "aucun-creneau", "Aucun créneau libre", e.getMessage());
    }

    /** Toutes les erreurs 400 produites par Spring (validation, JSON illisible…) prennent le type du contrat. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.value() == 400) {
            ProblemDetail p = body instanceof ProblemDetail pd ? pd
                    : ex instanceof ErrorResponse er ? er.getBody()
                    : ProblemDetail.forStatus(statusCode);
            p.setType(URI.create("/problemes/requete-invalide"));
            p.setTitle("Requête invalide");
            body = p;
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    static ProblemDetail probleme(HttpStatus statut, String type, String titre, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(statut, detail);
        p.setType(URI.create("/problemes/" + type));
        p.setTitle(titre);
        return p;
    }
}
