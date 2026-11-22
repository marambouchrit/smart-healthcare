package tn.ginf.smarthealthcare.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.nio.charset.StandardCharsets;

/** Chaque route publique de la gateway atteint le bon service, avec le chemin et la requête intacts (P3.3). */
class RoutesTest extends ServicesSimulesTest {

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
            "GET,  /,                                      web",
            "GET,  /main-F67VSUIB.js,                      web",
            "GET,  /seed/medecins.json,                    web",
            "GET,  /connexion,                             web",
            "GET,  /medecin/patients/TN-0012345,           web",
    })
    void routeVersLeBonService(String methode, String chemin, String service) {
        // un vrai client HTTP/1.1 envoie toujours Host ; le client de test ne le fait pas de lui-même
        client.method(HttpMethod.valueOf(methode)).uri(chemin)
                .header("Host", "localhost:8080")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton())
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
        String bearer = "Bearer " + jeton();
        client.get().uri("/inconnu").header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange().expectStatus().isNotFound();
        // /ws/autre n'appartient pas au service SOAP publié (/ws/dossier)
        client.get().uri("/ws/autre").header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange().expectStatus().isNotFound();
        // le client web n'a qu'une liste de chemins (D51) : pas de route attrape-tout
        client.get().uri("/patients").header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange().expectStatus().isNotFound();
        client.get().uri("/scripts/app.js").header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange().expectStatus().isNotFound();
    }
}
