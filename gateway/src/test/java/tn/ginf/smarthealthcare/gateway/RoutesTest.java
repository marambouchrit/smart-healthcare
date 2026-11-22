package tn.ginf.smarthealthcare.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Chaque route publique de la gateway atteint le bon service, avec le chemin et la requête intacts.
 * Les services sont remplacés par de petits serveurs HTTP du JDK qui renvoient « nom-du-service METHODE chemin?requête ».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RoutesTest {

    private static final Map<String, HttpServer> SERVICES = Map.of(
            "soap", demarrer("soap"),
            "rest", demarrer("rest"),
            "graphql", demarrer("graphql"),
            "orchestrateur", demarrer("orchestrateur"));

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) {
        registre.add("GATEWAY_SOAP_URL", () -> url("soap"));
        registre.add("GATEWAY_REST_URL", () -> url("rest"));
        registre.add("GATEWAY_GRAPHQL_URL", () -> url("graphql"));
        registre.add("GATEWAY_ORCHESTRATOR_URL", () -> url("orchestrateur"));
    }

    @Autowired
    WebTestClient client;

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "GET,  /ws/dossier?wsdl,                       soap",
            "POST, /ws/dossier,                            soap",
            "GET,  /api/creneaux?medecinId=MED-004,        rest",
            "POST, /api/rendez-vous,                       rest",
            "PATCH,/api/patients/TN-0012345,               rest",
            "GET,  /swagger-ui.html,                       rest",
            "GET,  /openapi/portail-openapi.yaml,          rest",
            "GET,  /v3/api-docs,                           rest",
            "POST, /graphql,                               graphql",
            "GET,  /graphiql,                              graphql",
            "GET,  /events,                                orchestrateur",
            "GET,  /orchestrator/workflows,                orchestrateur",
    })
    void routeVersLeBonService(String methode, String chemin, String service) {
        // un vrai client HTTP/1.1 envoie toujours Host ; le client de test ne le fait pas de lui-même
        client.method(org.springframework.http.HttpMethod.valueOf(methode)).uri(chemin)
                .header("Host", "localhost:8080")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(service + " " + methode + " " + chemin);
    }

    @Test
    void routeSoapConserveLEnTeteHost() {
        // le faux service SOAP renvoie l'en-tête Host reçu
        SERVICES.get("soap").createContext("/ws/dossier/hote", echange -> {
            byte[] octets = echange.getRequestHeaders().getFirst("Host").getBytes(StandardCharsets.UTF_8);
            echange.sendResponseHeaders(200, octets.length);
            echange.getResponseBody().write(octets);
            echange.close();
        });
        client.get().uri("/ws/dossier/hote").header("Host", "passerelle.exemple:8080")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("passerelle.exemple:8080");
    }

    @Test
    void cheminInconnuRenvoie404() {
        client.get().uri("/inconnu").exchange().expectStatus().isNotFound();
        // /ws/autre n'appartient pas au service SOAP publié (/ws/dossier)
        client.get().uri("/ws/autre").exchange().expectStatus().isNotFound();
    }

    @AfterAll
    static void arreter() {
        SERVICES.values().forEach(s -> s.stop(0));
    }

    private static String url(String nom) {
        return "http://localhost:" + SERVICES.get(nom).getAddress().getPort();
    }

    private static HttpServer demarrer(String nom) {
        try {
            HttpServer serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            serveur.createContext("/", echange -> {
                String requete = echange.getRequestURI().getRawQuery();
                String reponse = nom + " " + echange.getRequestMethod() + " " + echange.getRequestURI().getRawPath()
                        + (requete == null ? "" : "?" + requete);
                byte[] octets = reponse.getBytes(StandardCharsets.UTF_8);
                echange.sendResponseHeaders(200, octets.length);
                echange.getResponseBody().write(octets);
                echange.close();
            });
            serveur.start();
            return serveur;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
