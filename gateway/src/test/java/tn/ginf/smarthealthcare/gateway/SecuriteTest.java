package tn.ginf.smarthealthcare.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Authentification JWT à la gateway (P3.4, D41, D44). */
class SecuriteTest extends ServicesSimulesTest {

    @Autowired
    JwtEncoder encodeur;

    @Autowired
    ReactiveJwtDecoder decodeur;

    // ---------- POST /auth/login ----------

    @ParameterizedTest(name = "{0} → {1} {2}")
    @CsvSource({
            "patient, TN-0012345, PATIENT",
            "medecin, MED-001,    MEDECIN",
            "admin,   admin,      ADMIN",
    })
    void loginRenvoieUnJetonSigneAvecSujetEtRole(String compte, String sujet, String role) {
        @SuppressWarnings("unchecked")
        Map<String, Object> reponse = client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("identifiant", compte, "motDePasse", compte + "_dev"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();

        assertThat(reponse).containsEntry("type", "Bearer").containsEntry("sujet", sujet).containsEntry("role", role);
        Jwt jwt = decodeur.decode((String) reponse.get("jeton")).block();
        assertThat(jwt.getSubject()).isEqualTo(sujet);
        assertThat(jwt.getClaimAsString("role")).isEqualTo(role);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("smart-healthcare-gateway");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        // durée de validité par défaut : 1 heure
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(3600));
        assertThat(Instant.parse((String) reponse.get("expireLe"))).isEqualTo(jwt.getExpiresAt());
    }

    @ParameterizedTest(name = "{0} / {1} → 401")
    @CsvSource({"patient, mauvais", "inconnu, patient_dev", "medecin, patient_dev"})
    void mauvaisIdentifiantsRenvoient401(String compte, String motDePasse) {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("identifiant", compte, "motDePasse", motDePasse))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo("/problemes/identifiants-invalides");
    }

    @Test
    void loginIncompletRenvoie400() {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("identifiant", "patient"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.type").isEqualTo("/problemes/requete-invalide");
        client.post().uri("/auth/login").exchange().expectStatus().isBadRequest();
    }

    // ---------- routes protégées ----------

    @ParameterizedTest(name = "{0} {1} sans jeton → 401")
    @CsvSource({
            "POST, /ws/dossier",
            "GET,  /api/creneaux",
            "POST, /api/rendez-vous",
            "POST, /graphql",
            "GET,  /events",
            "GET,  /orchestrator/workflows",
    })
    void routesDesServicesExigentUnJeton(String methode, String chemin) {
        client.method(HttpMethod.valueOf(methode)).uri(chemin)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, "Bearer.*");
    }

    @Test
    void jetonObtenuAuLoginOuvreLesRoutes() {
        Map<?, ?> reponse = client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("identifiant", "patient", "motDePasse", "patient_dev"))
                .exchange().expectBody(Map.class).returnResult().getResponseBody();
        client.get().uri("/api/patients/TN-0012345")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + reponse.get("jeton"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("rest GET /api/patients/TN-0012345");
    }

    @ParameterizedTest(name = "GET {0} public")
    @CsvSource({
            "/ws/dossier?wsdl, soap", "/ws/dossier?xsd=dossier.xsd, soap",
            "/swagger-ui.html, rest", "/swagger-ui/index.html, rest", "/openapi/portail-openapi.yaml, rest",
            "/v3/api-docs, rest", "/graphiql, graphql",
    })
    void outilsDeTestRestentPublics(String chemin, String service) {
        client.get().uri(chemin).header("Host", "localhost:8080")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(service + " GET " + chemin);
    }

    @Test
    void jetonExpireRefuse() {
        Instant passe = Instant.now().minusSeconds(7200);
        String jeton = signer(encodeur, passe, passe.plusSeconds(3600), "smart-healthcare-gateway");
        appelerAvec(jeton).expectStatus().isUnauthorized();
    }

    @Test
    void jetonSigneAvecUnAutreSecretRefuse() {
        JwtEncoder autre = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "un-autre-secret-de-trente-deux-octets-minimum".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String jeton = signer(autre, Instant.now(), Instant.now().plusSeconds(3600), "smart-healthcare-gateway");
        appelerAvec(jeton).expectStatus().isUnauthorized();
    }

    @Test
    void jetonDUnAutreEmetteurRefuse() {
        String jeton = signer(encodeur, Instant.now(), Instant.now().plusSeconds(3600), "autre-emetteur");
        appelerAvec(jeton).expectStatus().isUnauthorized();
    }

    @Test
    void jetonModifieRefuse() {
        String jeton = jeton();
        // dernier caractère de la signature changé
        String modifie = jeton.substring(0, jeton.length() - 1) + (jeton.endsWith("A") ? "B" : "A");
        appelerAvec(modifie).expectStatus().isUnauthorized();
    }

    // ---------- jeton en paramètre d'URL : seulement /events (SSE) ----------

    @Test
    void jetonEnParametreAccepteSurEvents() {
        client.get().uri("/events?access_token=" + jeton())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(corps -> assertThat(corps).startsWith("orchestrateur GET /events?access_token="));
    }

    @Test
    void jetonEnParametreRefuseAilleurs() {
        client.get().uri("/api/creneaux?access_token=" + jeton())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec appelerAvec(String jeton) {
        return client.get().uri("/api/creneaux").header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton).exchange();
    }

    private static String signer(JwtEncoder encodeur, Instant emisLe, Instant expireLe, String emetteur) {
        JwtClaimsSet revendications = JwtClaimsSet.builder()
                .issuer(emetteur).subject("MED-001").issuedAt(emisLe).expiresAt(expireLe).claim("role", "MEDECIN")
                .build();
        return encodeur.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), revendications))
                .getTokenValue();
    }
}
