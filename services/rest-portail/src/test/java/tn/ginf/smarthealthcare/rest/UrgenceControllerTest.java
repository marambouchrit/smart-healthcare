package tn.ginf.smarthealthcare.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/urgences, conformément à contracts/rest/portail-openapi.yaml.
 * Horloge fixée au mardi 2026-11-17 à 10h10 (Tunis) : les créneaux URGENCES (MED-004) vont de 08:00 à 20:00.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UrgenceControllerTest {

    static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    /** Horloge dont on peut changer l'heure pendant un test. */
    static class HorlogeReglable extends Clock {
        Instant instant = ZonedDateTime.of(2026, 11, 17, 10, 10, 0, 0, TUNIS).toInstant();
        @Override public ZoneId getZone() { return TUNIS; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary
        HorlogeReglable horlogeTest() { return new HorlogeReglable(); }
    }

    @Autowired MockMvc mvc;
    @Autowired HorlogeReglable horloge;

    static final String DEMANDE = """
            {"patientId":"%s","identifiantAlerte":"ALR-0001","niveau":"CRITIQUE",
             "motif":"Fréquence cardiaque 165 bpm > 140 bpm"}""";

    @Test
    void reserveLePremierCreneauLibreDesUrgences() throws Exception {
        mvc.perform(post("/api/urgences").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-0012345")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/rendez-vous/RDV-000001"))
                .andExpect(jsonPath("$.id").value("RDV-000001"))
                .andExpect(jsonPath("$.patientId").value("TN-0012345"))
                .andExpect(jsonPath("$.statut").value("CONFIRME"))
                .andExpect(jsonPath("$.urgence").value(true))
                .andExpect(jsonPath("$.creneau.medecinId").value("MED-004"))
                .andExpect(jsonPath("$.creneau.serviceCode").value("URGENCES"))
                .andExpect(jsonPath("$.creneau.debut").value("2026-11-17T10:30:00+01:00"))
                .andExpect(jsonPath("$.creneau.disponible").value(false));

        // Le créneau de 10h30 est pris : la demande suivante obtient 11h00
        mvc.perform(post("/api/urgences").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-0034567")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("RDV-000002"))
                .andExpect(jsonPath("$.creneau.debut").value("2026-11-17T11:00:00+01:00"));
    }

    @Test
    void aucunCreneauLibreRenvoie409() throws Exception {
        horloge.instant = ZonedDateTime.of(2026, 11, 17, 19, 45, 0, 0, TUNIS).toInstant();
        mvc.perform(post("/api/urgences").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-0012345")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problemes/aucun-creneau"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void patientInconnuRenvoie404() throws Exception {
        mvc.perform(post("/api/urgences").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-9999999")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problemes/non-trouve"));
    }

    @Test
    void identifiantMalFormeRenvoie400() throws Exception {
        mvc.perform(post("/api/urgences").contentType(MediaType.APPLICATION_JSON).content(DEMANDE.formatted("TN-12")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problemes/requete-invalide"));
    }

    @Test
    void niveauInconnuRenvoie400() throws Exception {
        mvc.perform(post("/api/urgences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"TN-0012345\",\"identifiantAlerte\":\"ALR-1\",\"niveau\":\"GRAVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problemes/requete-invalide"));
    }
}
