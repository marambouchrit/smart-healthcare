package tn.ginf.smarthealthcare.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;

/** P2.GQL.1 : dossier consolidé en une requête (profil, dernières constantes, alertes récentes, traitement, couverture). */
@SpringBootTest
@AutoConfigureGraphQlTester
@Import(PostgresTest.class)
class DossierConsolideTest extends PostgresTest.BaseVide {

    @Autowired GraphQlTester graphql;

    static final String CONSTANTES = """
            mutation($c: [ConstanteInput!]!) {
              enregistrerConstantes(identifiantPatient: "TN-0012345", constantes: $c) { type valeur horodatage }
            }""";

    void constantes(String json) throws Exception {
        graphql.document(CONSTANTES).variable("c", new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, java.util.List.class))
                .execute().errors().verify();
    }

    @Test
    void dossierCompletEnUneSeuleRequete() throws Exception {
        constantes("""
                [{"type":"FREQUENCE_CARDIAQUE","valeur":82,"horodatage":"2026-11-17T10:25:00+01:00"},
                 {"type":"SPO2","valeur":96,"horodatage":"2026-11-17T10:25:00+01:00"}]""");
        graphql.document("""
                mutation { enregistrerAlerte(alerte: { identifiantAlerte: "ALR-000001", identifiantPatient: "TN-0012345",
                  niveau: CRITIQUE, type: FREQUENCE_CARDIAQUE, valeur: 165, seuil: 140, message: "FC 165 > 140",
                  horodatage: "2026-11-17T10:30:00+01:00" }) { id } }""").execute().errors().verify();
        graphql.document("""
                mutation { mettreAJourCouverture(couverture: { identifiantPatient: "TN-0012345", statut: OUVERTS,
                  codeVignette: "VIG-2026-100001", tauxRemboursement: 80, dateFin: "2026-12-31" }) { statut misAJourLe } }""")
                .execute().errors().verify();

        graphql.document("""
                { patient(id: "TN-0012345") {
                    nom niveauRisque
                    dernieresConstantes { type valeur horodatage }
                    alertesRecentes(limite: 3) { id niveau valeur }
                    traitementsEnCours { medicament }
                    couverture { statut codeVignette tauxRemboursement dateFin } } }""")
                .execute().errors().verify()
                .path("patient.nom").entity(String.class).isEqualTo("Mansouri")
                .path("patient.dernieresConstantes[*].type").entityList(String.class).containsExactly("FREQUENCE_CARDIAQUE", "SPO2")
                .path("patient.dernieresConstantes[0].valeur").entity(Double.class).isEqualTo(82.0)
                .path("patient.dernieresConstantes[0].horodatage").entity(String.class).isEqualTo("2026-11-17T10:25:00+01:00")
                .path("patient.alertesRecentes[0].id").entity(String.class).isEqualTo("ALR-000001")
                .path("patient.traitementsEnCours[*].medicament").entityList(String.class).containsExactly("Bisoprolol", "Furosémide")
                .path("patient.couverture.statut").entity(String.class).isEqualTo("OUVERTS")
                .path("patient.couverture.tauxRemboursement").entity(Integer.class).isEqualTo(80)
                .path("patient.couverture.dateFin").entity(String.class).isEqualTo("2026-12-31");
    }

    @Test
    void laDerniereMesureDeChaqueTypeEstConservee() throws Exception {
        constantes("[{\"type\":\"FREQUENCE_CARDIAQUE\",\"valeur\":80,\"horodatage\":\"2026-11-17T10:00:00+01:00\"}]");
        constantes("[{\"type\":\"FREQUENCE_CARDIAQUE\",\"valeur\":91,\"horodatage\":\"2026-11-17T10:05:00+01:00\"}]");
        // mesure plus ancienne arrivée en retard : ignorée
        constantes("[{\"type\":\"FREQUENCE_CARDIAQUE\",\"valeur\":70,\"horodatage\":\"2026-11-17T09:00:00+01:00\"}]");
        graphql.document("{ patient(id: \"TN-0012345\") { dernieresConstantes { type valeur } } }").execute()
                .path("patient.dernieresConstantes").entityList(Object.class).hasSize(1)
                .path("patient.dernieresConstantes[0].valeur").entity(Double.class).isEqualTo(91.0);
    }

    @Test
    void sansDonneesLaCouvertureEstNullEtLesConstantesVides() {
        graphql.document("{ patient(id: \"TN-0023456\") { couverture { statut } dernieresConstantes { type } } }").execute()
                .errors().verify()
                .path("patient.couverture").valueIsNull()
                .path("patient.dernieresConstantes").entityList(Object.class).hasSize(0);
    }

    @Test
    void erreurs() {
        graphql.document("mutation { enregistrerConstantes(identifiantPatient: \"TN-9999999\", constantes: []) { type } }").execute()
                .errors().satisfy(e -> assertThat(e.get(0).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        graphql.document("mutation { enregistrerConstantes(identifiantPatient: \"TN-0012345\", constantes: []) { type } }").execute()
                .errors().satisfy(e -> assertThat(e.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        graphql.document("""
                mutation { mettreAJourCouverture(couverture: { identifiantPatient: "TN-0012345", statut: OUVERTS,
                  tauxRemboursement: 150 }) { statut } }""").execute()
                .errors().satisfy(e -> assertThat(e.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
}
