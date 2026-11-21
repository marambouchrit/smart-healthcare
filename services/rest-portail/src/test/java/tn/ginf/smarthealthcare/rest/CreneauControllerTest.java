package tn.ginf.smarthealthcare.rest;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/creneaux. Horloge : mardi 2026-11-17 10h10 (Tunis).
 * Plannings du seed : MED-001 lun/mer/ven 9h-12h, MED-002 mar/jeu 9h-13h, MED-003 lun-ven 14h-17h, MED-004 tous les jours 8h-20h.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HorlogeTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CreneauControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void creneauxLibresDUnMedecinUnJourDonne() throws Exception {
        // MED-002 le mardi 17 : 9h00 à 12h30 = 8 créneaux, dont 9h00, 9h30 et 10h00 déjà commencés → 5 libres
        mvc.perform(get("/api/creneaux").param("medecinId", "MED-002").param("date", "2026-11-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].debut", is("2026-11-17T10:30:00+01:00")))
                .andExpect(jsonPath("$[0].fin", is("2026-11-17T11:00:00+01:00")))
                .andExpect(jsonPath("$[*].medecinId", everyItem(is("MED-002"))))
                .andExpect(jsonPath("$[*].serviceCode", everyItem(is("PNEUMO"))))
                .andExpect(jsonPath("$[*].disponible", everyItem(is(true))))
                .andExpect(jsonPath("$[0].id", startsWith("CRN-")));
    }

    @Test
    void medecinSansConsultationCeJourLa() throws Exception {
        // MED-001 ne consulte pas le mardi
        mvc.perform(get("/api/creneaux").param("medecinId", "MED-001").param("date", "2026-11-17"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        // … mais le mercredi 18 : 9h00 à 11h30 = 6 créneaux
        mvc.perform(get("/api/creneaux").param("medecinId", "MED-001").param("date", "2026-11-18"))
                .andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    void filtreParService() throws Exception {
        mvc.perform(get("/api/creneaux").param("serviceCode", "CARDIO").param("date", "2026-11-18"))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].medecinId", everyItem(is("MED-001"))));
    }

    @Test
    void unCreneauReserveDisparaitDesDisponiblesEtApparaitDansLesReserves() throws Exception {
        mvc.perform(post("/api/urgences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"TN-0012345\",\"identifiantAlerte\":\"ALR-1\",\"niveau\":\"CRITIQUE\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/creneaux").param("medecinId", "MED-004").param("date", "2026-11-17").param("disponible", "false"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].debut", is("2026-11-17T10:30:00+01:00")))
                .andExpect(jsonPath("$[0].disponible", is(false)));
        mvc.perform(get("/api/creneaux").param("medecinId", "MED-004").param("date", "2026-11-17"))
                .andExpect(jsonPath("$[0].debut", is("2026-11-17T11:00:00+01:00")));
    }

    @Test
    void parametresInvalides400() throws Exception {
        mvc.perform(get("/api/creneaux").param("medecinId", "DR-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("/problemes/requete-invalide")));
        mvc.perform(get("/api/creneaux").param("date", "17/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("/problemes/requete-invalide")));
    }
}
