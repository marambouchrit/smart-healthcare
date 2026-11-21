package tn.ginf.smarthealthcare.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** P2.REST.7 : schéma Flyway, seed et créneaux en base, rendez-vous et profils persistés, double réservation refusée par la base. */
@SpringBootTest
@Import({HorlogeTest.Config.class, PostgresTest.class})
@AutoConfigureMockMvc
class PersistancePortailTest extends PostgresTest.BaseVide {

    @Autowired MockMvc mvc;

    int compter(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void schemaSeedEtCreneauxEnBase() {
        assertThat(jdbc.queryForObject("SELECT max(version) FROM flyway_schema_history", String.class)).isEqualTo("1");
        assertThat(Map.of("medecin", compter("SELECT count(*) FROM medecin"), "patient", compter("SELECT count(*) FROM patient"),
                "traitement", compter("SELECT count(*) FROM traitement")))
                .isEqualTo(Map.of("medecin", 4, "patient", 8, "traitement", 10));
        // horizon de 14 jours depuis le mardi 17 : 2 semaines complètes de plannings
        assertThat(compter("SELECT count(*) FROM creneau")).isEqualTo(464);
        assertThat(jdbc.queryForObject("SELECT medecin_id || ' ' || to_char(debut AT TIME ZONE 'Africa/Tunis', 'YYYY-MM-DD HH24:MI') "
                + "FROM creneau WHERE id = 1", String.class)).isEqualTo("MED-004 2026-11-17 08:00");
    }

    @Test
    void rendezVousEtProfilEcritsEnBase() throws Exception {
        mvc.perform(post("/api/rendez-vous").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"TN-0012345\",\"creneauId\":\"CRN-000009\",\"motif\":\"Contrôle\"}"));
        assertThat(jdbc.queryForMap("SELECT patient_id, creneau_id, statut, urgence FROM rendez_vous WHERE id = 1"))
                .containsEntry("patient_id", "TN-0012345").containsEntry("creneau_id", 9L)
                .containsEntry("statut", "CONFIRME").containsEntry("urgence", false);
        mvc.perform(patch("/api/patients/TN-0012345").contentType(MediaType.APPLICATION_JSON).content("{\"telephone\":\"+216 20 999 999\"}"));
        assertThat(jdbc.queryForObject("SELECT telephone FROM patient WHERE id = 'TN-0012345'", String.class))
                .isEqualTo("+216 20 999 999");
    }

    @Test
    void laBaseRefuseDeuxRendezVousActifsSurLeMemeCreneau() {
        String insertion = "INSERT INTO rendez_vous (patient_id, creneau_id, statut, urgence, cree_le) VALUES (?, 9, ?, false, now())";
        jdbc.update(insertion, "TN-0012345", "CONFIRME");
        assertThatThrownBy(() -> jdbc.update(insertion, "TN-0023456", "CONFIRME"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("rendez_vous_un_seul_actif_par_creneau");
        // un rendez-vous annulé sur le même créneau reste possible (historique)
        jdbc.update(insertion, "TN-0023456", "ANNULE");
        assertThat(compter("SELECT count(*) FROM rendez_vous WHERE creneau_id = 9")).isEqualTo(2);
    }
}
