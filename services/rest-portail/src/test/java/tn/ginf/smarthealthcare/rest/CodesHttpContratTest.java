package tn.ginf.smarthealthcare.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

/**
 * P2.REST.5 : chaque code HTTP renvoyé par le service est (1) celui attendu pour le scénario et (2) déclaré dans
 * contracts/rest/portail-openapi.yaml pour cette opération ; toute erreur est en application/problem+json.
 * Horloge : mardi 2026-11-17 10h10. Créneaux utilisés : CRN-000009 = MED-002 mardi 10h30 (libre),
 * CRN-000001 = MED-004 mardi 8h00 (déjà commencé).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HorlogeTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CodesHttpContratTest {

    @Autowired MockMvc mvc;

    @SuppressWarnings("unchecked")
    static Map<String, Object> contrat() throws Exception {
        try (InputStream in = Files.newInputStream(Path.of("../../contracts/rest/portail-openapi.yaml"))) {
            return (Map<String, Object>) new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    static boolean declare(String chemin, String methode, int statut) throws Exception {
        Map<String, Object> paths = (Map<String, Object>) contrat().get("paths");
        Map<String, Object> op = (Map<String, Object>) ((Map<String, Object>) paths.get(chemin)).get(methode.toLowerCase());
        return ((Map<String, Object>) op.get("responses")).containsKey(String.valueOf(statut));
    }

    MockHttpServletResponse appeler(String methode, String url, String corps) throws Exception {
        var req = request(HttpMethod.valueOf(methode), url);
        if (corps != null && !corps.isEmpty()) {
            req.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mvc.perform(req).andReturn().getResponse();
    }

    @ParameterizedTest(name = "{0} {2} → {4}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
        // méthode | chemin du contrat | URL | corps | statut attendu
        "GET    | /api/creneaux                         | /api/creneaux?medecinId=MED-002                 | | 200",
        "GET    | /api/creneaux                         | /api/creneaux?date=demain                       | | 400",
        "GET    | /api/rendez-vous                      | /api/rendez-vous?patientId=TN-0012345           | | 200",
        "GET    | /api/rendez-vous                      | /api/rendez-vous?medecinId=X                    | | 400",
        "POST   | /api/rendez-vous                      | /api/rendez-vous | `{\"patientId\":\"TN-0012345\",\"creneauId\":\"CRN-000009\"}` | 201",
        "POST   | /api/rendez-vous                      | /api/rendez-vous | `{\"patientId\":\"TN-0012345\"}`                              | 400",
        "POST   | /api/rendez-vous                      | /api/rendez-vous | `{\"patientId\":\"TN-9999999\",\"creneauId\":\"CRN-000009\"}` | 404",
        "POST   | /api/rendez-vous                      | /api/rendez-vous | `{\"patientId\":\"TN-0012345\",\"creneauId\":\"CRN-000001\"}` | 409",
        "GET    | /api/rendez-vous/{id}                 | /api/rendez-vous/RDV-999999                     | | 404",
        "PUT    | /api/rendez-vous/{id}                 | /api/rendez-vous/RDV-999999 | `{\"patientId\":\"TN-0012345\",\"creneauId\":\"CRN-000009\"}` | 404",
        "PUT    | /api/rendez-vous/{id}                 | /api/rendez-vous/RDV-000001 | `{}`                                                      | 400",
        "PATCH  | /api/rendez-vous/{id}                 | /api/rendez-vous/RDV-999999 | `{\"motif\":\"x\"}`                                       | 404",
        "PATCH  | /api/rendez-vous/{id}                 | /api/rendez-vous/RDV-000001 | `{\"statut\":\"PERDU\"}`                                  | 400",
        "DELETE | /api/rendez-vous/{id}                 | /api/rendez-vous/RDV-999999                     | | 404",
        "GET    | /api/patients/{patientId}             | /api/patients/TN-0012345                        | | 200",
        "GET    | /api/patients/{patientId}             | /api/patients/TN-9999999                        | | 404",
        "PATCH  | /api/patients/{patientId}             | /api/patients/TN-0012345 | `{\"email\":\"a@b.tn\"}`                                     | 200",
        "PATCH  | /api/patients/{patientId}             | /api/patients/TN-0012345 | `{\"email\":\"non\"}`                                        | 400",
        "PATCH  | /api/patients/{patientId}             | /api/patients/TN-9999999 | `{\"email\":\"a@b.tn\"}`                                     | 404",
        "GET    | /api/patients/{patientId}/ordonnances | /api/patients/TN-0012345/ordonnances            | | 200",
        "GET    | /api/patients/{patientId}/ordonnances | /api/patients/TN-9999999/ordonnances            | | 404",
        "POST   | /api/urgences                         | /api/urgences | `{\"patientId\":\"TN-0012345\",\"identifiantAlerte\":\"A\",\"niveau\":\"URGENT\"}` | 201",
        "POST   | /api/urgences                         | /api/urgences | `{\"patientId\":\"TN-0012345\",\"niveau\":\"URGENT\"}`                              | 400",
        "POST   | /api/urgences                         | /api/urgences | `{\"patientId\":\"TN-9999999\",\"identifiantAlerte\":\"A\",\"niveau\":\"URGENT\"}` | 404",
    })
    void codeAttenduEtDeclareDansLeContrat(String methode, String chemin, String url, String corps, int attendu) throws Exception {
        MockHttpServletResponse r = appeler(methode, url, corps);
        assertThat(r.getStatus()).as("statut de %s %s", methode, url).isEqualTo(attendu);
        assertThat(declare(chemin, methode, attendu)).as("code %d déclaré dans le contrat pour %s %s", attendu, methode, chemin).isTrue();
        if (attendu >= 400) {
            assertThat(r.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat((String) JsonPath.read(r.getContentAsString(), "$.type")).startsWith("/problemes/");
        }
    }

    @Test
    void conflitEtSuppressionSurUnRendezVousExistant() throws Exception {
        assertThat(appeler("POST", "/api/rendez-vous", "{\"patientId\":\"TN-0012345\",\"creneauId\":\"CRN-000009\"}").getStatus()).isEqualTo(201);
        assertThat(appeler("PATCH", "/api/rendez-vous/RDV-000001", "{\"statut\":\"ANNULE\"}").getStatus()).isEqualTo(200);
        MockHttpServletResponse conflit = appeler("PUT", "/api/rendez-vous/RDV-000001", "{\"patientId\":\"TN-0012345\",\"creneauId\":\"CRN-000009\"}");
        assertThat(conflit.getStatus()).isEqualTo(409);
        assertThat(declare("/api/rendez-vous/{id}", "PUT", 409)).isTrue();
        MockHttpServletResponse suppression = appeler("DELETE", "/api/rendez-vous/RDV-000001", null);
        assertThat(suppression.getStatus()).isEqualTo(204);
        assertThat(suppression.getContentAsString()).isEmpty();
        assertThat(declare("/api/rendez-vous/{id}", "DELETE", 204)).isTrue();
    }

    @Test
    void lesMessages400NommentLesChampsRefuses() throws Exception {
        String corps = appeler("POST", "/api/rendez-vous", "{\"patientId\":\"TN-12\",\"creneauId\":\"CRN-1\"}").getContentAsString();
        assertThat((String) JsonPath.read(corps, "$.detail")).contains("patientId").contains("'TN-12'").contains("creneauId");
        assertThat((Integer) JsonPath.read(corps, "$.erreurs.length()")).isEqualTo(2);

        // corps validé en même temps qu'un paramètre d'URL : le champ du corps est nommé, pas le paramètre Java
        String patch = appeler("PATCH", "/api/patients/TN-0012345", "{\"telephone\":\"abc\"}").getContentAsString();
        assertThat((String) JsonPath.read(patch, "$.erreurs[0].champ")).isEqualTo("telephone");
        assertThat((String) JsonPath.read(patch, "$.detail")).startsWith("telephone : valeur 'abc' refusée");

        String param = appeler("GET", "/api/creneaux?medecinId=DR-1", null).getContentAsString();
        assertThat((String) JsonPath.read(param, "$.erreurs[0].champ")).isEqualTo("medecinId");

        String type = appeler("GET", "/api/creneaux?date=17/11/2026", null).getContentAsString();
        assertThat((String) JsonPath.read(type, "$.detail")).isEqualTo("date : valeur '17/11/2026' invalide");

        String enumInvalide = appeler("PATCH", "/api/rendez-vous/RDV-000001", "{\"statut\":\"PERDU\"}").getContentAsString();
        assertThat((String) JsonPath.read(enumInvalide, "$.detail")).isEqualTo("statut : valeur 'PERDU' invalide");

        String illisible = appeler("POST", "/api/rendez-vous", "{pas du json").getContentAsString();
        assertThat((String) JsonPath.read(illisible, "$.detail")).isEqualTo("Corps JSON absent ou illisible.");
    }

    @Test
    void routeInconnue404AuFormatDuContrat() throws Exception {
        MockHttpServletResponse r = appeler("GET", "/api/inexistant", null);
        assertThat(r.getStatus()).isEqualTo(404);
        assertThat((String) JsonPath.read(r.getContentAsString(), "$.type")).isEqualTo("/problemes/non-trouve");
        assertThat((String) JsonPath.read(r.getContentAsString(), "$.detail")).isEqualTo("Aucune route /api/inexistant dans le portail.");
    }
}
