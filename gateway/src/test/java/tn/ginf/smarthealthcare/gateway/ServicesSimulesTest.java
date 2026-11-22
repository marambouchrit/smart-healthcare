package tn.ginf.smarthealthcare.gateway;

import com.sun.net.httpserver.HttpServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import tn.ginf.smarthealthcare.gateway.securite.ServiceJeton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Base des tests : gateway complète sur un port aléatoire, les 4 services et le client web (D51) remplacés par de petits serveurs HTTP du JDK
 * qui renvoient « nom-du-service METHODE chemin?requête ».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
abstract class ServicesSimulesTest {

    static final Map<String, HttpServer> SERVICES = Map.of(
            "soap", demarrer("soap"),
            "rest", demarrer("rest"),
            "graphql", demarrer("graphql"),
            "orchestrateur", demarrer("orchestrateur"),
            "web", demarrer("web"));

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) {
        registre.add("GATEWAY_SOAP_URL", () -> url("soap"));
        registre.add("GATEWAY_REST_URL", () -> url("rest"));
        registre.add("GATEWAY_GRAPHQL_URL", () -> url("graphql"));
        registre.add("GATEWAY_ORCHESTRATOR_URL", () -> url("orchestrateur"));
        registre.add("GATEWAY_WEB_URL", () -> url("web"));
    }

    @Autowired
    WebTestClient client;

    @Autowired
    ServiceJeton serviceJeton;

    /** Jeton valide du compte de démonstration « medecin ». */
    String jeton() {
        return serviceJeton.connecter("medecin", "medecin_dev").orElseThrow().jeton();
    }

    static String url(String nom) {
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
            // les serveurs vivent le temps de la JVM de test (démons)
            serveur.setExecutor(null);
            serveur.start();
            return serveur;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
