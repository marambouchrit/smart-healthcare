package tn.ginf.smarthealthcare.orchestrator.evenements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.PriseEnCharge;

/** Flux SSE /events : noms, identifiants et données des événements conformes à contracts/sse/README.md. */
class DiffusionSseTest {

    static final AlerteRecue ALERTE = new AlerteRecue("ALR-000001", "TN-0012345", "CRITIQUE", "FREQUENCE_CARDIAQUE",
            168, 140, "Fréquence cardiaque 168 bpm > 140 bpm", OffsetDateTime.parse("2026-11-22T17:20:00+01:00"));

    DiffusionSse diffusion;
    MockMvc mvc;

    @BeforeEach
    void preparer() {
        diffusion = new DiffusionSse(new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new EvenementsController(diffusion)).build();
    }

    MvcResult abonner() throws Exception {
        return mvc.perform(get("/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    static List<String> lignes(MvcResult r) throws Exception {
        return Arrays.asList(r.getResponse().getContentAsString(StandardCharsets.UTF_8).split("\n"));
    }

    @Test
    void abonnementOuvreUnFluxTextEventStream() throws Exception {
        MvcResult r = abonner();
        assertThat(r.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(lignes(r)).containsExactly(":connecte");
        assertThat(diffusion.nombreAbonnes()).isEqualTo(1);
    }

    @Test
    void alerteDiffuseeAvecNomIdentifiantEtDonnees() throws Exception {
        MvcResult r = abonner();
        diffusion.alerte(ALERTE);

        assertThat(lignes(r)).containsSubsequence(
                "event:alerte",
                "id:ALR-000001",
                "data:{\"id\":\"ALR-000001\",\"patientId\":\"TN-0012345\",\"niveau\":\"CRITIQUE\","
                        + "\"type\":\"FREQUENCE_CARDIAQUE\",\"valeur\":168.0,\"seuil\":140.0,"
                        + "\"message\":\"Fréquence cardiaque 168 bpm > 140 bpm\",\"horodatage\":\"2026-11-22T17:20:00+01:00\"}");
    }

    @Test
    void priseEnChargeTermineeEtAmbulance() throws Exception {
        MvcResult r = abonner();
        diffusion.priseEnCharge(new PriseEnCharge("ALR-000001", "TN-0012345", "TERMINE", false, "RDV-000001",
                OffsetDateTime.parse("2026-11-22T17:30:00+01:00"), "FIC-000001", null, null,
                OffsetDateTime.parse("2026-11-22T17:20:01+01:00")));
        // exemple du contrat contracts/sse/README.md (P5.2, D53)
        diffusion.priseEnCharge(new PriseEnCharge("ALR-000002", "TN-0089012", "AMBULANCE", true, null, null, "FIC-000002",
                "AMB-000001", OffsetDateTime.parse("2026-11-22T23:41:00+01:00"), OffsetDateTime.parse("2026-11-22T23:21:01+01:00")));

        assertThat(lignes(r)).containsSubsequence(
                "event:prise-en-charge",
                "id:ALR-000001",
                "data:{\"alerteId\":\"ALR-000001\",\"patientId\":\"TN-0012345\",\"etat\":\"TERMINE\",\"aRegulariser\":false,"
                        + "\"rendezVousId\":\"RDV-000001\",\"debutRendezVous\":\"2026-11-22T17:30:00+01:00\","
                        + "\"ficheId\":\"FIC-000001\",\"interventionId\":null,\"arriveeEstimee\":null,"
                        + "\"horodatage\":\"2026-11-22T17:20:01+01:00\"}",
                "event:prise-en-charge",
                "id:ALR-000002",
                "data:{\"alerteId\":\"ALR-000002\",\"patientId\":\"TN-0089012\",\"etat\":\"AMBULANCE\",\"aRegulariser\":true,"
                        + "\"rendezVousId\":null,\"debutRendezVous\":null,\"ficheId\":\"FIC-000002\",\"interventionId\":\"AMB-000001\","
                        + "\"arriveeEstimee\":\"2026-11-22T23:41:00+01:00\",\"horodatage\":\"2026-11-22T23:21:01+01:00\"}");
    }

    @Test
    void chaqueAbonneRecoitLesEvenementsEtLeBattement() throws Exception {
        MvcResult premier = abonner();
        MvcResult second = abonner();
        diffusion.alerte(ALERTE);
        diffusion.battement();

        for (MvcResult r : List.of(premier, second)) {
            assertThat(lignes(r)).containsSubsequence(":connecte", "event:alerte", "id:ALR-000001", ":ping");
        }
        assertThat(diffusion.nombreAbonnes()).isEqualTo(2);
    }

    @Test
    void sansAbonneLaDiffusionNeFaitRien() {
        diffusion.alerte(ALERTE);
        diffusion.battement();
        assertThat(diffusion.nombreAbonnes()).isZero();
    }
}
