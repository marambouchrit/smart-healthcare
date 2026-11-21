package tn.ginf.smarthealthcare.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;

/**
 * P2.GQL.3. Données préparées : 3 alertes de TN-0012345 (CRITIQUE le 2026-10-05, URGENT et CRITIQUE le 2026-11-17)
 * et 2 fiches (consultation, ambulance) créées le 2026-11-17 (horloge fixée).
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@Import(PostgresTest.class)
class HistoriqueSoinsTest extends PostgresTest.BaseVide {

    @TestConfiguration
    static class Horloge {
        @Bean @Primary
        Clock horlogeFixe() { return Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), ZoneId.of("Africa/Tunis")); }
    }

    @Autowired GraphQlTester graphql;

    void alerte(String id, String niveau, String horodatage) {
        graphql.document("""
                mutation { enregistrerAlerte(alerte: { identifiantAlerte: "%s", identifiantPatient: "TN-0012345", niveau: %s,
                  type: FREQUENCE_CARDIAQUE, valeur: 150, seuil: 140, message: "m", horodatage: "%s" }) { id } }"""
                .formatted(id, niveau, horodatage)).execute().errors().verify();
    }

    void fiche(String alerte, String action) {
        graphql.document("""
                mutation { creerFicheUrgence(fiche: { identifiantPatient: "TN-0012345", identifiantAlerte: "%s",
                  statutPriseEnCharge: PRISE_EN_CHARGE, action: %s }) { id } }""".formatted(alerte, action)).execute().errors().verify();
    }

    @BeforeEach
    void donnees() {
        alerte("ALR-000001", "CRITIQUE", "2026-10-05T08:00:00+01:00");
        alerte("ALR-000002", "URGENT", "2026-11-17T09:00:00+01:00");
        alerte("ALR-000003", "CRITIQUE", "2026-11-17T09:30:00+01:00");
        fiche("ALR-000002", "CONSULTATION_URGENCE");
        fiche("ALR-000003", "AMBULANCE");
    }

    @Test
    void agregatsSurToutLHistorique() {
        graphql.document("""
                { patient(id: "TN-0012345") { historiqueSoins {
                    depuis jusqua nombreAlertes alertesParNiveau { niveau nombre }
                    nombreFichesUrgence nombreAmbulances traitements { medicament } } } }""")
                .execute().errors().verify()
                .path("patient.historiqueSoins.depuis").valueIsNull()
                .path("patient.historiqueSoins.nombreAlertes").entity(Integer.class).isEqualTo(3)
                .path("patient.historiqueSoins.alertesParNiveau[*].niveau").entityList(String.class).containsExactly("INFO", "URGENT", "CRITIQUE")
                .path("patient.historiqueSoins.alertesParNiveau[*].nombre").entityList(Integer.class).containsExactly(0, 1, 2)
                .path("patient.historiqueSoins.nombreFichesUrgence").entity(Integer.class).isEqualTo(2)
                .path("patient.historiqueSoins.nombreAmbulances").entity(Integer.class).isEqualTo(1)
                .path("patient.historiqueSoins.traitements[*].medicament").entityList(String.class).containsExactly("Bisoprolol", "Furosémide");
    }

    @Test
    void filtreParPeriode() {
        graphql.document("{ patient(id: \"TN-0012345\") { historiqueSoins(depuis: \"2026-11-01\") { depuis nombreAlertes } } }")
                .execute().path("patient.historiqueSoins.depuis").entity(String.class).isEqualTo("2026-11-01")
                .path("patient.historiqueSoins.nombreAlertes").entity(Integer.class).isEqualTo(2);
        graphql.document("{ patient(id: \"TN-0012345\") { historiqueSoins(depuis: \"2026-10-01\", jusqua: \"2026-10-31\") { nombreAlertes nombreFichesUrgence } } }")
                .execute().path("patient.historiqueSoins.nombreAlertes").entity(Integer.class).isEqualTo(1)
                .path("patient.historiqueSoins.nombreFichesUrgence").entity(Integer.class).isEqualTo(0);
        // traitements commencés le 2026-02-10 : absents d'une période qui se termine avant
        graphql.document("{ patient(id: \"TN-0012345\") { historiqueSoins(jusqua: \"2026-01-31\") { traitements { medicament } } } }")
                .execute().path("patient.historiqueSoins.traitements").entityList(Object.class).hasSize(0);
    }

    @Test
    void traitementTermineHorsPeriode() {
        // TN-0045678 : Prednisolone du 2026-09-12 au 2026-09-17
        graphql.document("{ patient(id: \"TN-0045678\") { historiqueSoins(depuis: \"2026-10-01\") { traitements { medicament } } } }")
                .execute().path("patient.historiqueSoins.traitements[*].medicament").entityList(String.class).containsExactly("Salbutamol");
    }

    @Test
    void seulLeChampDemandeEstRenvoye() {
        String json = graphql.document("{ patient(id: \"TN-0012345\") { historiqueSoins { nombreAlertes } } }")
                .execute().returnResponse().toString();
        assertThat(json).contains("nombreAlertes").doesNotContain("alertesParNiveau").doesNotContain("traitements");
    }

    @Test
    void periodeInversee() {
        graphql.document("{ patient(id: \"TN-0012345\") { historiqueSoins(depuis: \"2026-12-01\", jusqua: \"2026-01-01\") { nombreAlertes } } }")
                .execute().errors().satisfy(e -> assertThat(e.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
}
