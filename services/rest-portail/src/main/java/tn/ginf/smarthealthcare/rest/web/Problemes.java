package tn.ginf.smarthealthcare.rest.web;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /**
     * Erreurs produites par Spring : les 400 (validation, paramètre mal typé, JSON illisible) prennent le type
     * du contrat et un détail qui nomme chaque champ refusé (propriété « erreurs ») ; les 404 de route inconnue
     * prennent le type non-trouve.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.value() == 400 || statusCode.value() == 404) {
            ProblemDetail p = body instanceof ProblemDetail pd ? pd
                    : ex instanceof ErrorResponse er ? er.getBody()
                    : ProblemDetail.forStatus(statusCode);
            if (statusCode.value() == 404) {
                p.setType(URI.create("/problemes/non-trouve"));
                p.setTitle("Ressource introuvable");
                if (ex instanceof NoResourceFoundException n) {
                    p.setDetail("Aucune route /" + n.getResourcePath() + " dans le portail.");
                }
            } else {
                p.setType(URI.create("/problemes/requete-invalide"));
                p.setTitle("Requête invalide");
                List<Map<String, String>> erreurs = erreursDeChamps(ex);
                if (!erreurs.isEmpty()) {
                    p.setProperty("erreurs", erreurs);
                    p.setDetail(erreurs.stream().map(e -> e.get("champ") + " : " + e.get("message"))
                            .collect(Collectors.joining(" ; ")));
                } else if (ex instanceof HttpMessageNotReadableException) {
                    p.setDetail("Corps JSON absent ou illisible.");
                }
            }
            body = p;
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    /** Liste [{champ, message}] des valeurs refusées, quelle que soit l'origine de l'erreur. */
    private static List<Map<String, String>> erreursDeChamps(Exception ex) {
        List<Map<String, String>> l = new ArrayList<>();
        if (ex instanceof MethodArgumentNotValidException m) {
            m.getBindingResult().getFieldErrors().forEach(f -> l.add(erreur(f.getField(),
                    "valeur " + valeur(f.getRejectedValue()) + " refusée (" + f.getDefaultMessage() + ")")));
        } else if (ex instanceof HandlerMethodValidationException h) {
            h.getParameterValidationResults().forEach(r -> {
                if (r instanceof ParameterErrors corps) {
                    // corps @Valid validé en même temps qu'un paramètre d'URL : on descend jusqu'aux champs
                    corps.getFieldErrors().forEach(f -> l.add(erreur(f.getField(),
                            "valeur " + valeur(f.getRejectedValue()) + " refusée (" + f.getDefaultMessage() + ")")));
                } else {
                    r.getResolvableErrors().forEach(e -> l.add(erreur(r.getMethodParameter().getParameterName(),
                            "valeur " + valeur(r.getArgument()) + " refusée (" + e.getDefaultMessage() + ")")));
                }
            });
        } else if (ex instanceof TypeMismatchException t) {
            String nom = t instanceof MethodArgumentTypeMismatchException m ? m.getName() : t.getPropertyName();
            l.add(erreur(nom, "valeur " + valeur(t.getValue()) + " invalide"));
        } else if (ex instanceof HttpMessageNotReadableException n && n.getCause() instanceof InvalidFormatException f) {
            String champ = f.getPath().stream().map(r -> r.getFieldName() == null ? "[" + r.getIndex() + "]" : r.getFieldName())
                    .collect(Collectors.joining("."));
            l.add(erreur(champ, "valeur " + valeur(f.getValue()) + " invalide"));
        }
        return l;
    }

    private static Map<String, String> erreur(String champ, String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("champ", champ);
        m.put("message", message);
        return m;
    }

    private static String valeur(Object v) {
        return v == null ? "absente" : "'" + v + "'";
    }

    static ProblemDetail probleme(HttpStatus statut, String type, String titre, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(statut, detail);
        p.setType(URI.create("/problemes/" + type));
        p.setTitle(titre);
        return p;
    }
}
