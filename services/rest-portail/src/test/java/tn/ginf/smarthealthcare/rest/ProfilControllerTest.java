package tn.ginf.smarthealthcare.rest;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** GET / PATCH /api/patients/{id}. */
@SpringBootTest
@Import({HorlogeTest.Config.class, PostgresTest.class})
@AutoConfigureMockMvc
class ProfilControllerTest extends PostgresTest.BaseVide {

    @Autowired MockMvc mvc;

    ResultActions patcher(String id, String json) throws Exception {
        return mvc.perform(patch("/api/patients/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void profilDuSeed() throws Exception {
        mvc.perform(get("/api/patients/TN-0012345")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nom", is("Mansouri")))
                .andExpect(jsonPath("$.dateNaissance", is("1952-03-14")))
                .andExpect(jsonPath("$.sexe", is("M")))
                .andExpect(jsonPath("$.adresse.ville", is("Tunis")))
                .andExpect(jsonPath("$.medecinTraitantId", is("MED-001")));
    }

    @Test
    void modificationPartielleDesCoordonnees() throws Exception {
        patcher("TN-0012345", "{\"telephone\":\"+216 20 111 222\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone", is("+216 20 111 222")))
                .andExpect(jsonPath("$.email", is("h.mansouri@mail.example")))   // inchangé
                .andExpect(jsonPath("$.adresse.rue", is("12 rue de Marseille")));  // inchangé
        patcher("TN-0012345", "{\"adresse\":{\"rue\":\"4 rue de Marseille\",\"ville\":\"Ariana\",\"codePostal\":\"2080\"}}")
                .andExpect(jsonPath("$.adresse.ville", is("Ariana")))
                .andExpect(jsonPath("$.telephone", is("+216 20 111 222")));
        mvc.perform(get("/api/patients/TN-0012345")).andExpect(jsonPath("$.adresse.codePostal", is("2080")));
    }

    @Test
    void lesChampsNonModifiablesSontIgnoresEtUnPatchSansCoordonneeEstRefuse() throws Exception {
        patcher("TN-0012345", "{\"nom\":\"Autre\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("/problemes/requete-invalide")));
        mvc.perform(get("/api/patients/TN-0012345")).andExpect(jsonPath("$.nom", is("Mansouri")));
        patcher("TN-0012345", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void validation400() throws Exception {
        patcher("TN-0012345", "{\"telephone\":\"abc\"}").andExpect(status().isBadRequest());
        patcher("TN-0012345", "{\"email\":\"pas-un-email\"}").andExpect(status().isBadRequest());
        patcher("TN-0012345", "{\"adresse\":{\"rue\":\"x\",\"ville\":\"Tunis\",\"codePostal\":\"10\"}}").andExpect(status().isBadRequest());
        patcher("TN-0012345", "{\"adresse\":{\"rue\":\"x\"}}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/patients/TN-12")).andExpect(status().isBadRequest());
    }

    @Test
    void patientInconnu404() throws Exception {
        mvc.perform(get("/api/patients/TN-9999999")).andExpect(status().isNotFound());
        patcher("TN-9999999", "{\"telephone\":\"+216 20 111 222\"}").andExpect(status().isNotFound());
    }
}
