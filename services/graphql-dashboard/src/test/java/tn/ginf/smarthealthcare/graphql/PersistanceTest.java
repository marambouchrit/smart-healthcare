package tn.ginf.smarthealthcare.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;

/** P2.GQL.4 : schéma Flyway, seed en base, écritures de l'orchestrateur persistées, alerte idempotente. */
@SpringBootTest
@AutoConfigureGraphQlTester
@Import(PostgresTest.class)
class PersistanceTest extends PostgresTest.BaseVide {

    @Autowired GraphQlTester graphql;

    static final String ALERTE = """
            mutation { enregistrerAlerte(alerte: { identifiantAlerte: "ALR-000042", identifiantPatient: "TN-0012345",
              niveau: CRITIQUE, type: FREQUENCE_CARDIAQUE, valeur: %s, seuil: 140, message: "m",
              horodatage: "2026-11-17T10:30:00+01:00" }) { id valeur horodatage } }""";

    int compter(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void schemaEtSeedEnBase() {
        assertThat(jdbc.queryForObject("SELECT max(version) FROM flyway_schema_history", String.class)).isEqualTo("1");
        assertThat(Map.of("service_hospitalier", compter("service_hospitalier"), "medecin", compter("medecin"),
                "patient", compter("patient"), "traitement", compter("traitement")))
                .isEqualTo(Map.of("service_hospitalier", 4, "medecin", 4, "patient", 8, "traitement", 10));
    }

    @Test
    void lesMutationsEcriventDansPostgresql() {
        graphql.document(ALERTE.formatted("165")).execute().errors().verify();
        graphql.document("""
                mutation { creerFicheUrgence(fiche: { identifiantPatient: "TN-0012345", identifiantAlerte: "ALR-000042",
                  statutPriseEnCharge: A_REGULARISER, action: AMBULANCE }) { id } }""").execute().errors().verify()
                .path("creerFicheUrgence.id").entity(String.class).isEqualTo("FIC-000001");
        graphql.document("""
                mutation { enregistrerConstantes(identifiantPatient: "TN-0012345",
                  constantes: [{ type: SPO2, valeur: 93, horodatage: "2026-11-17T10:00:00+01:00" }]) { type } }""")
                .execute().errors().verify();
        graphql.document("""
                mutation { mettreAJourCouverture(couverture: { identifiantPatient: "TN-0012345", statut: EXPIRES,
                  codeVignette: "VIG-2025-100006", tauxRemboursement: 80, dateFin: "2025-12-31" }) { statut } }""")
                .execute().errors().verify();

        assertThat(jdbc.queryForMap("SELECT niveau, valeur FROM alerte WHERE id = 'ALR-000042'"))
                .containsEntry("niveau", "CRITIQUE").containsEntry("valeur", 165.0);
        assertThat(jdbc.queryForObject("SELECT action FROM fiche_urgence WHERE id = 1", String.class)).isEqualTo("AMBULANCE");
        assertThat(jdbc.queryForObject("SELECT valeur FROM constante WHERE patient_id = 'TN-0012345' AND type = 'SPO2'",
                Double.class)).isEqualTo(93.0);
        assertThat(jdbc.queryForObject("SELECT statut FROM couverture WHERE patient_id = 'TN-0012345'", String.class))
                .isEqualTo("EXPIRES");
    }

    @Test
    void lHorodatageEstRelUAvecLeFuseauDuService() {
        graphql.document(ALERTE.formatted("165")).execute().errors().verify()
                .path("enregistrerAlerte.horodatage").entity(String.class).isEqualTo("2026-11-17T10:30:00+01:00");
    }

    @Test
    void uneAlerteRenvoyeeDeuxFoisNEstNiDoubleeNiEcrasee() {
        graphql.document(ALERTE.formatted("165")).execute().errors().verify();
        graphql.document(ALERTE.formatted("999")).execute().errors().verify()
                .path("enregistrerAlerte.valeur").entity(Double.class).isEqualTo(165.0);
        assertThat(compter("alerte")).isEqualTo(1);
    }
}
