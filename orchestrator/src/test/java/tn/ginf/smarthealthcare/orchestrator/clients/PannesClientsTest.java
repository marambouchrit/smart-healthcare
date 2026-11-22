package tn.ginf.smarthealthcare.orchestrator.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.ServiceIndisponibleException;

/**
 * Délais d'attente et classement des erreurs des clients (P5.3, D54), sur un vrai réseau local :
 * un petit serveur HTTP du JDK joue le service (503, 409, 400, réponse trop lente) et un port fermé la panne.
 */
class PannesClientsTest {

    static final AlerteRecue ALERTE = new AlerteRecue("ALR-000001", "TN-0012345", "CRITIQUE", "FREQUENCE_CARDIAQUE",
            165, 140, "Fréquence cardiaque 165 bpm > 140 bpm", OffsetDateTime.parse("2026-11-22T17:20:00+01:00"));
    static final Duration CONNEXION = Duration.ofSeconds(1);
    static final Duration REPONSE = Duration.ofMillis(500);

    static HttpServer serveur;

    @BeforeAll
    static void demarrer() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        repondre("/indisponible/api/urgences", 503, "{}");
        repondre("/conflit/api/urgences", 409, "{\"type\":\"/problemes/aucun-creneau\",\"status\":409}");
        repondre("/invalide/api/urgences", 400, "{\"type\":\"/problemes/requete-invalide\",\"status\":400}");
        repondre("/graphql-erreur", 200, "{\"errors\":[{\"message\":\"Aucun patient TN-9999999\"}]}");
        repondre("/graphql-panne", 502, "");
        serveur.createContext("/lent/api/urgences", echange -> {
            try {
                Thread.sleep(2_000);   // plus long que le délai de réponse (500 ms)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            echange.sendResponseHeaders(201, -1);
            echange.close();
        });
        serveur.start();
    }

    @AfterAll
    static void arreter() {
        serveur.stop(0);
    }

    static void repondre(String chemin, int statut, String corps) {
        serveur.createContext(chemin, echange -> {
            byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("Content-Type", "application/json");
            echange.sendResponseHeaders(statut, octets.length == 0 ? -1 : octets.length);
            if (octets.length > 0) echange.getResponseBody().write(octets);
            echange.close();
        });
    }

    static String url(String prefixe) {
        return "http://localhost:" + serveur.getAddress().getPort() + prefixe;
    }

    /** Adresse d'un port fermé : la connexion est refusée immédiatement. */
    static String portFerme() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return "http://localhost:" + s.getLocalPort();
        }
    }

    static RestClient.Builder builder() {
        return RestClient.builder().requestFactory(ConfigurationAppels.fabrique(CONNEXION, REPONSE));
    }

    static ClientPortailRest portail(String url) {
        return new ClientPortailRest(builder(), url);
    }

    @Test
    void connexionRefuseeRequeteNonPartie() throws IOException {
        assertThatThrownBy(() -> portail(portFerme()).reserverUrgence(ALERTE, false))
                .isInstanceOfSatisfying(ServiceIndisponibleException.class, e -> {
                    assertThat(e.service()).isEqualTo("REST");
                    assertThat(e.requeteEnvoyee()).isFalse();
                    assertThat(e).hasMessageContaining("connexion impossible");
                });
    }

    @Test
    void reponse503RequetePartie() {
        assertThatThrownBy(() -> portail(url("/indisponible")).reserverUrgence(ALERTE, false))
                .isInstanceOfSatisfying(ServiceIndisponibleException.class, e -> {
                    assertThat(e.requeteEnvoyee()).isTrue();
                    assertThat(e).hasMessageContaining("HTTP 503");
                });
    }

    @Test
    void delaiDeReponseDepasseRequetePartie() {
        long debut = System.nanoTime();
        assertThatThrownBy(() -> portail(url("/lent")).reserverUrgence(ALERTE, false))
                .isInstanceOfSatisfying(ServiceIndisponibleException.class, e -> assertThat(e.requeteEnvoyee()).isTrue());
        // le délai de 500 ms coupe l'appel bien avant les 2 s du serveur
        assertThat(Duration.ofNanos(System.nanoTime() - debut)).isLessThan(Duration.ofMillis(1_800));
    }

    @Test
    void conflit409ResteUneReponseMetier() {
        assertThat(portail(url("/conflit")).reserverUrgence(ALERTE, false)).isEmpty();
    }

    @Test
    void erreur400NEstPasUnePanne() {
        assertThatThrownBy(() -> portail(url("/invalide")).reserverUrgence(ALERTE, false))
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .isNotInstanceOf(ServiceIndisponibleException.class);
    }

    @Test
    void graphqlErreursMetierEtPanne() {
        assertThatThrownBy(() -> new ClientTableauDeBordGraphql(builder(), url("/graphql-erreur")).enregistrerAlerte(ALERTE))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(ServiceIndisponibleException.class)
                .hasMessageContaining("Aucun patient TN-9999999");
        assertThatThrownBy(() -> new ClientTableauDeBordGraphql(builder(), url("/graphql-panne")).enregistrerAlerte(ALERTE))
                .isInstanceOfSatisfying(ServiceIndisponibleException.class, e -> {
                    assertThat(e.service()).isEqualTo("GraphQL");
                    assertThat(e).hasMessageContaining("HTTP 502");
                });
    }

    @Test
    void soapInjoignableRequeteNonPartie() throws Exception {
        ClientDossierSoap soap = new ClientDossierSoap(portFerme() + "/ws/dossier", "C", CONNEXION, REPONSE);
        assertThatThrownBy(() -> soap.verifierDroits("TN-0012345", LocalDate.of(2026, 11, 22)))
                .isInstanceOfSatisfying(ServiceIndisponibleException.class, e -> {
                    assertThat(e.service()).isEqualTo("SOAP");
                    assertThat(e.requeteEnvoyee()).isFalse();
                });
    }
}
