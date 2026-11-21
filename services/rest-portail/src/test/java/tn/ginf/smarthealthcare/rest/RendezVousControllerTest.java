package tn.ginf.smarthealthcare.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** CRUD /api/rendez-vous. Horloge : mardi 2026-11-17 10h10 (Tunis). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({HorlogeTest.Config.class, PostgresTest.class})
class RendezVousControllerTest extends PostgresTest.BaseVide {

    @Autowired MockMvc mvc;

    /** Identifiant du n-ième créneau libre (0 = premier) d'un médecin un jour donné. */
    String creneau(String medecin, String date, int n) throws Exception {
        String json = mvc.perform(get("/api/creneaux").param("medecinId", medecin).param("date", date))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[" + n + "].id");
    }

    ResultActions envoyer(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String json) throws Exception {
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String creer(String patient, String creneau) throws Exception {
        String json = envoyer(post("/api/rendez-vous"),
                "{\"patientId\":\"%s\",\"creneauId\":\"%s\",\"motif\":\"Contrôle\"}".formatted(patient, creneau))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void cycleCompletCreerLireRemplacerModifierSupprimer() throws Exception {
        String c1 = creneau("MED-001", "2026-11-18", 0);
        String c2 = creneau("MED-001", "2026-11-18", 1);

        // POST → 201 + Location
        envoyer(post("/api/rendez-vous"), "{\"patientId\":\"TN-0012345\",\"creneauId\":\"%s\",\"motif\":\"Contrôle cardiologique\"}".formatted(c1))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/rendez-vous/RDV-000001"))
                .andExpect(jsonPath("$.statut", is("CONFIRME")))
                .andExpect(jsonPath("$.urgence", is(false)))
                .andExpect(jsonPath("$.creneau.id", is(c1)))
                .andExpect(jsonPath("$.creneau.debut", is("2026-11-18T09:00:00+01:00")));

        // GET /{id} et GET liste filtrée
        mvc.perform(get("/api/rendez-vous/RDV-000001")).andExpect(status().isOk()).andExpect(jsonPath("$.motif", is("Contrôle cardiologique")));
        mvc.perform(get("/api/rendez-vous").param("patientId", "TN-0012345")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/rendez-vous").param("medecinId", "MED-001").param("date", "2026-11-18")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/rendez-vous").param("patientId", "TN-0023456")).andExpect(jsonPath("$", hasSize(0)));

        // PUT : changement de créneau → l'ancien redevient libre, le nouveau est pris
        envoyer(put("/api/rendez-vous/RDV-000001"), "{\"patientId\":\"TN-0012345\",\"creneauId\":\"%s\",\"motif\":\"Décalé\"}".formatted(c2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creneau.id", is(c2)))
                .andExpect(jsonPath("$.motif", is("Décalé")));
        mvc.perform(get("/api/creneaux").param("medecinId", "MED-001").param("date", "2026-11-18"))
                .andExpect(jsonPath("$[0].id", is(c1)));

        // PATCH : motif seul, puis annulation (libère le créneau)
        envoyer(patch("/api/rendez-vous/RDV-000001"), "{\"motif\":\"Douleur thoracique\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut", is("CONFIRME"))).andExpect(jsonPath("$.motif", is("Douleur thoracique")));
        envoyer(patch("/api/rendez-vous/RDV-000001"), "{\"statut\":\"ANNULE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut", is("ANNULE")));
        mvc.perform(get("/api/creneaux").param("medecinId", "MED-001").param("date", "2026-11-18"))
                .andExpect(jsonPath("$", hasSize(6)));

        // DELETE → 204 puis 404
        mvc.perform(delete("/api/rendez-vous/RDV-000001")).andExpect(status().isNoContent());
        mvc.perform(get("/api/rendez-vous/RDV-000001")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", is("/problemes/non-trouve")));
    }

    @Test
    void creneauDejaReserve409() throws Exception {
        String c = creneau("MED-002", "2026-11-19", 0);
        creer("TN-0034567", c);
        envoyer(post("/api/rendez-vous"), "{\"patientId\":\"TN-0045678\",\"creneauId\":\"%s\"}".formatted(c))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("/problemes/creneau-indisponible")));
    }

    @Test
    void creneauDejaCommence409() throws Exception {
        String c = creneau("MED-002", "2026-11-17", 0); // 10h30
        horloge.regler(17, 11, 0);
        envoyer(post("/api/rendez-vous"), "{\"patientId\":\"TN-0034567\",\"creneauId\":\"%s\"}".formatted(c))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("/problemes/creneau-indisponible")));
    }

    @Test
    void ressourcesInconnues404() throws Exception {
        envoyer(post("/api/rendez-vous"), "{\"patientId\":\"TN-9999999\",\"creneauId\":\"%s\"}".formatted(creneau("MED-004", "2026-11-18", 0)))
                .andExpect(status().isNotFound());
        envoyer(post("/api/rendez-vous"), "{\"patientId\":\"TN-0012345\",\"creneauId\":\"CRN-999999\"}")
                .andExpect(status().isNotFound());
        envoyer(put("/api/rendez-vous/RDV-999999"), "{\"patientId\":\"TN-0012345\",\"creneauId\":\"CRN-000001\"}")
                .andExpect(status().isNotFound());
        envoyer(patch("/api/rendez-vous/RDV-999999"), "{\"motif\":\"x\"}").andExpect(status().isNotFound());
        mvc.perform(delete("/api/rendez-vous/RDV-999999")).andExpect(status().isNotFound());
    }

    @Test
    void reglesDeModification() throws Exception {
        String id = creer("TN-0012345", creneau("MED-001", "2026-11-18", 0));
        // changer de patient par PUT : refusé
        envoyer(put("/api/rendez-vous/" + id), "{\"patientId\":\"TN-0023456\",\"creneauId\":\"%s\"}".formatted(creneau("MED-001", "2026-11-18", 0)))
                .andExpect(status().isBadRequest());
        // PATCH vide : refusé
        envoyer(patch("/api/rendez-vous/" + id), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("/problemes/requete-invalide")));
        // HONORE puis ANNULE : transition interdite
        envoyer(patch("/api/rendez-vous/" + id), "{\"statut\":\"HONORE\"}").andExpect(status().isOk());
        envoyer(patch("/api/rendez-vous/" + id), "{\"statut\":\"ANNULE\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("/problemes/transition-interdite")));
        // rendez-vous annulé : plus modifiable
        String id2 = creer("TN-0023456", creneau("MED-001", "2026-11-18", 1));
        envoyer(patch("/api/rendez-vous/" + id2), "{\"statut\":\"ANNULE\"}").andExpect(status().isOk());
        envoyer(patch("/api/rendez-vous/" + id2), "{\"motif\":\"reprise\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("/problemes/rendez-vous-annule")));
    }

    @Test
    void validationDesEntrees400() throws Exception {
        envoyer(post("/api/rendez-vous"), "{\"patientId\":\"TN-0012345\"}").andExpect(status().isBadRequest());
        envoyer(post("/api/rendez-vous"), "{\"patientId\":\"TN-12\",\"creneauId\":\"CRN-000001\"}").andExpect(status().isBadRequest());
        envoyer(patch("/api/rendez-vous/RDV-000001"), "{\"statut\":\"PERDU\"}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/rendez-vous/abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/rendez-vous").param("patientId", "X")).andExpect(status().isBadRequest());
    }

    @Test
    void lUrgenceCreeUnRendezVousGerableParLeCrud() throws Exception {
        envoyer(post("/api/urgences"), "{\"patientId\":\"TN-0012345\",\"identifiantAlerte\":\"ALR-1\",\"niveau\":\"CRITIQUE\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/rendez-vous/RDV-000001")).andExpect(jsonPath("$.urgence", is(true)));
        envoyer(patch("/api/rendez-vous/RDV-000001"), "{\"statut\":\"HONORE\"}").andExpect(status().isOk());
    }
}
