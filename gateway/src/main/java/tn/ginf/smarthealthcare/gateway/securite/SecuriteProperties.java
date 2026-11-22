package tn.ginf.smarthealthcare.gateway.securite;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Réglages du JWT émis et vérifié par la gateway (D41, D44).
 *
 * @param jwt     secret HMAC, durée de validité et émetteur des jetons
 * @param comptes comptes de démonstration, indexés par identifiant de connexion
 */
@ConfigurationProperties("securite")
public record SecuriteProperties(Jwt jwt, Map<String, Compte> comptes) {

    public record Jwt(String secret, Duration duree, String emetteur) {}

    /**
     * @param motDePasse mot de passe de développement
     * @param sujet      identité portée par le jeton (identifiant du seed : TN-…, MED-…, ou « admin »)
     * @param role       PATIENT, MEDECIN ou ADMIN
     */
    public record Compte(String motDePasse, String sujet, String role) {}
}
