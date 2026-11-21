package tn.ginf.smarthealthcare.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

/**
 * P2.REST.6 : la description générée depuis le code (/v3/api-docs, springdoc) a les mêmes chemins, méthodes et
 * operationId que contracts/rest/portail-openapi.yaml ; Swagger UI et le contrat sont servis.
 * Seule différence admise : POST /api/ambulances, prévu au contrat pour P5.2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AlignementContratTest {

    static final Set<String> PREVU_PLUS_TARD = Set.of("POST /api/ambulances envoyerAmbulance");
    static final Set<String> METHODES = Set.of("get", "post", "put", "patch", "delete");

    @Autowired MockMvc mvc;

    /** "METHODE chemin operationId" pour chaque opération d'une description OpenAPI. */
    @SuppressWarnings("unchecked")
    static Set<String> operations(Map<String, Object> openapi) {
        Map<String, String> ops = new TreeMap<>();
        ((Map<String, Map<String, Object>>) openapi.get("paths")).forEach((chemin, item) -> item.forEach((methode, op) -> {
            if (METHODES.contains(methode)) {
                String cle = methode.toUpperCase() + " " + chemin.replace("{patientId}", "{id}");
                ops.put(cle, cle + " " + ((Map<String, Object>) op).get("operationId"));
            }
        }));
        return new java.util.TreeSet<>(ops.values());
    }

    @SuppressWarnings("unchecked")
    @Test
    void codeEtContratDecriventLesMemesOperations() throws Exception {
        Map<String, Object> contrat;
        try (InputStream in = Files.newInputStream(Path.of("../../contracts/rest/portail-openapi.yaml"))) {
            contrat = (Map<String, Object>) new Yaml().load(in);
        }
        String json = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        Map<String, Object> genere = new ObjectMapper().readValue(json, Map.class);

        Set<String> duContrat = operations(contrat);
        Set<String> duCode = operations(genere);
        Set<String> manquantes = new java.util.TreeSet<>(duContrat);
        manquantes.removeAll(duCode);
        Set<String> enTrop = new java.util.TreeSet<>(duCode);
        enTrop.removeAll(duContrat);

        assertThat(enTrop).as("opérations du code absentes du contrat").isEmpty();
        assertThat(manquantes).as("opérations du contrat non implémentées").isEqualTo(PREVU_PLUS_TARD);
        assertThat(duCode).hasSize(11); // 1 créneaux + 6 rendez-vous + 2 profil + 1 ordonnances + 1 urgences
    }

    @Test
    void leContratEstServiTelQuel() throws Exception {
        String servi = mvc.perform(get("/openapi/portail-openapi.yaml")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(servi).isEqualTo(Files.readString(Path.of("../../contracts/rest/portail-openapi.yaml")));
    }

    @Test
    void swaggerUiPointeSurLeContrat() throws Exception {
        String config = mvc.perform(get("/v3/api-docs/swagger-config")).andReturn().getResponse().getContentAsString();
        assertThat(config).contains("/openapi/portail-openapi.yaml");
        assertThat(mvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
