package tn.ginf.smarthealthcare.rest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/patients/{id}/ordonnances, à partir de contracts/seed/traitements.json. */
@SpringBootTest
@AutoConfigureMockMvc
class OrdonnanceControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void prescriptionsDuMemeJourDansUneOrdonnance() throws Exception {
        mvc.perform(get("/api/patients/TN-0012345/ordonnances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].medecinId", is("MED-001")))
                .andExpect(jsonPath("$[0].date", is("2026-02-10")))
                .andExpect(jsonPath("$[0].lignes[*].medicament", contains("Bisoprolol", "Furosémide")))
                .andExpect(jsonPath("$[0].lignes[0].dateFin", nullValue()))
                .andExpect(jsonPath("$[0].lignes[0].statut", is("EN_COURS")));
    }

    @Test
    void plusRecentesDAbordEtFiltreParStatut() throws Exception {
        // TN-0045678 : Salbutamol (2026-04-05, en cours) et Prednisolone (2026-09-12, terminé)
        mvc.perform(get("/api/patients/TN-0045678/ordonnances"))
                .andExpect(jsonPath("$[*].date", contains("2026-09-12", "2026-04-05")))
                .andExpect(jsonPath("$[0].lignes[0].dateFin", is("2026-09-17")));
        mvc.perform(get("/api/patients/TN-0045678/ordonnances").param("statut", "TERMINE"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lignes[0].medicament", is("Prednisolone")));
    }

    @Test
    void identifiantsStables() throws Exception {
        // ordre (date, médecin, patient) : la plus ancienne prescription du seed est Metformine (2025-06-01)
        mvc.perform(get("/api/patients/TN-0067890/ordonnances")).andExpect(jsonPath("$[0].id", is("ORD-000001")));
    }

    @Test
    void erreurs() throws Exception {
        mvc.perform(get("/api/patients/TN-9999999/ordonnances")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", is("/problemes/non-trouve")));
        mvc.perform(get("/api/patients/TN-12/ordonnances")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/patients/TN-0012345/ordonnances").param("statut", "PERDU")).andExpect(status().isBadRequest());
    }
}
