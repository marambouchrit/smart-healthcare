package tn.ginf.smarthealthcare.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/ambulances (P5.2, D53), conformément à contracts/rest/portail-openapi.yaml.
 * Horloge fixée au mardi 2026-11-17 à 10h10 (Tunis) ; délai d'arrivée par défaut : 20 minutes.
 */
@SpringBootTest
@Import({HorlogeTest.Config.class, PostgresTest.class})
@AutoConfigureMockMvc
class AmbulanceControllerTest extends PostgresTest.BaseVide {

    @Autowired MockMvc mvc;

    static final String DEMANDE = """
            {"patientId":"%s","identifiantAlerte":"ALR-000007","niveau":"CRITIQUE",
             "motif":"Chute détectée","aRegulariser":%s}""";

    @Test
    void envoieUneAmbulanceAuDomicileDuPatient() throws Exception {
        // TN-0012345 du seed : 12 rue de Marseille, 1000 Tunis
        mvc.perform(post("/api/ambulances").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-0012345", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("AMB-000001"))
                .andExpect(jsonPath("$.patientId").value("TN-0012345"))
                .andExpect(jsonPath("$.identifiantAlerte").value("ALR-000007"))
                .andExpect(jsonPath("$.adresse.rue").value("12 rue de Marseille"))
                .andExpect(jsonPath("$.adresse.ville").value("Tunis"))
                .andExpect(jsonPath("$.adresse.codePostal").value("1000"))
                .andExpect(jsonPath("$.declencheeLe").value("2026-11-17T10:10:00+01:00"))
                .andExpect(jsonPath("$.arriveeEstimee").value("2026-11-17T10:30:00+01:00"));

        mvc.perform(post("/api/ambulances").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-0089012", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("AMB-000002"));

        Map<String, Object> ligne = jdbc.queryForMap("SELECT niveau, motif, a_regulariser FROM intervention_ambulance WHERE id = 2");
        assertThat(ligne).containsEntry("niveau", "CRITIQUE").containsEntry("motif", "Chute détectée")
                .containsEntry("a_regulariser", true);
    }

    @Test
    void utiliseLAdresseActuelleDuPatient() throws Exception {
        mvc.perform(patch("/api/patients/TN-0012345").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":{\"rue\":\"5 avenue Habib Bourguiba\",\"ville\":\"Ariana\",\"codePostal\":\"2080\"}}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/ambulances").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-0012345", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adresse.rue").value("5 avenue Habib Bourguiba"))
                .andExpect(jsonPath("$.adresse.ville").value("Ariana"));
    }

    @Test
    void patientInconnuRenvoie404() throws Exception {
        mvc.perform(post("/api/ambulances").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-9999999", false)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problemes/non-trouve"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intervention_ambulance", Integer.class)).isZero();
    }

    @Test
    void demandeInvalideRenvoie400() throws Exception {
        mvc.perform(post("/api/ambulances").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-12", false)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problemes/requete-invalide"));
    }
}
