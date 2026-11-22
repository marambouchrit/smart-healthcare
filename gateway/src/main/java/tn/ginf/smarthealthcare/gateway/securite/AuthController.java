package tn.ginf.smarthealthcare.gateway.securite;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

/**
 * {@code POST /auth/login} : échange un compte de démonstration contre un jeton JWT (D41, D44).
 * Erreurs au format Problem Details, comme le service REST (D19).
 */
@RestController
public class AuthController {

    public record DemandeConnexion(String identifiant, String motDePasse) {}

    public record ReponseConnexion(String jeton, String type, Instant expireLe, String sujet, String role) {}

    private final ServiceJeton serviceJeton;

    public AuthController(ServiceJeton serviceJeton) {
        this.serviceJeton = serviceJeton;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> connecter(@RequestBody(required = false) DemandeConnexion demande) {
        if (demande == null || vide(demande.identifiant()) || vide(demande.motDePasse())) {
            return probleme(HttpStatus.BAD_REQUEST, "requete-invalide", "Requête invalide",
                    "Les champs « identifiant » et « motDePasse » sont obligatoires.");
        }
        return serviceJeton.connecter(demande.identifiant(), demande.motDePasse())
                .<ResponseEntity<?>>map(j -> ResponseEntity.ok(
                        new ReponseConnexion(j.jeton(), "Bearer", j.expireLe(), j.sujet(), j.role())))
                .orElseGet(() -> probleme(HttpStatus.UNAUTHORIZED, "identifiants-invalides", "Identifiants invalides",
                        "Identifiant ou mot de passe incorrect."));
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    private static ResponseEntity<ProblemDetail> probleme(HttpStatus statut, String type, String titre, String detail) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(statut, detail);
        probleme.setType(URI.create("/problemes/" + type));
        probleme.setTitle(titre);
        return ResponseEntity.status(statut).body(probleme);
    }
}
