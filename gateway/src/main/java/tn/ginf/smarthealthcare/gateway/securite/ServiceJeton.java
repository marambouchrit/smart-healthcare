package tn.ginf.smarthealthcare.gateway.securite;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** Vérifie un compte de démonstration et émet son jeton JWT signé (HS256). */
@Service
public class ServiceJeton {

    static final String CLAIM_ROLE = "role";

    private final SecuriteProperties proprietes;
    private final JwtEncoder encodeur;
    private final Clock horloge;

    public ServiceJeton(SecuriteProperties proprietes, JwtEncoder encodeur) {
        this.proprietes = proprietes;
        this.encodeur = encodeur;
        this.horloge = Clock.systemUTC();
    }

    public record JetonEmis(String jeton, Instant expireLe, String sujet, String role) {}

    /** Jeton du compte si le mot de passe est correct, vide sinon (même réponse pour un compte inconnu). */
    public Optional<JetonEmis> connecter(String identifiant, String motDePasse) {
        SecuriteProperties.Compte compte = proprietes.comptes().get(identifiant);
        if (compte == null || !egaux(compte.motDePasse(), motDePasse)) {
            return Optional.empty();
        }
        // un JWT compte en secondes : la date renvoyée au client est celle écrite dans le jeton
        Instant maintenant = horloge.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiration = maintenant.plus(proprietes.jwt().duree());
        JwtClaimsSet revendications = JwtClaimsSet.builder()
                .issuer(proprietes.jwt().emetteur())
                .subject(compte.sujet())
                .issuedAt(maintenant)
                .expiresAt(expiration)
                .claim(CLAIM_ROLE, compte.role())
                .build();
        String jeton = encodeur.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), revendications)).getTokenValue();
        return Optional.of(new JetonEmis(jeton, expiration, compte.sujet(), compte.role()));
    }

    /** Comparaison en temps constant (ne révèle pas la longueur du préfixe commun). */
    private static boolean egaux(String attendu, String recu) {
        return MessageDigest.isEqual(attendu.getBytes(StandardCharsets.UTF_8), recu.getBytes(StandardCharsets.UTF_8));
    }
}
