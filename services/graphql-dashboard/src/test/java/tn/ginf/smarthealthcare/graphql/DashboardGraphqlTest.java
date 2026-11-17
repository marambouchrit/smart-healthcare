package tn.ginf.smarthealthcare.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.DirtiesContext;

/** Requêtes exécutées par le moteur GraphQL du service, schéma chargé depuis contracts/graphql/dashboard.graphqls. */
@SpringBootTest
@AutoConfigureGraphQlTester
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DashboardGraphqlTest {

    @Autowired GraphQlTester graphql;

    static final String ALERTE = """
            mutation {
              enregistrerAlerte(alerte: {
                identifiantAlerte: "ALR-0001", identifiantPatient: "TN-0012345", niveau: CRITIQUE,
                type: FREQUENCE_CARDIAQUE, valeur: 165, seuil: 140,
                message: "Fréquence cardiaque 165 bpm > 140 bpm", horodatage: "2026-11-17T10:30:00+01:00"
              }) { id niveau horodatage }
            }""";

    static final String FICHE = """
            mutation {
              creerFicheUrgence(fiche: {
                identifiantPatient: "%s", identifiantAlerte: "%s",
                statutPriseEnCharge: PRISE_EN_CHARGE, action: CONSULTATION_URGENCE,
                identifiantRendezVous: "RDV-000001", dateRendezVous: "2026-11-17T11:00:00+01:00"
              }) { id action identifiantRendezVous dateRendezVous patient { nom } alerte { id message } }
            }""";

    @Test
    void patientMinimal() {
        graphql.document("""
                { patient(id: "TN-0012345") {
                    id nom prenom dateNaissance niveauRisque pathologie
                    service { code libelle } medecinTraitant { id nom }
                    traitementsEnCours { medicament } alertesRecentes { id } fichesUrgence { id } dernieresConstantes { valeur } } }""")
                .execute()
                .errors().verify()
                .path("patient.nom").entity(String.class).isEqualTo("Mansouri")
                .path("patient.dateNaissance").entity(String.class).isEqualTo("1952-03-14")
                .path("patient.niveauRisque").entity(String.class).isEqualTo("ELEVE")
                .path("patient.service.libelle").entity(String.class).isEqualTo("Cardiologie")
                .path("patient.medecinTraitant.id").entity(String.class).isEqualTo("MED-001")
                .path("patient.traitementsEnCours[*].medicament").entityList(String.class).containsExactly("Bisoprolol", "Furosémide")
                .path("patient.fichesUrgence").entityList(Object.class).hasSize(0);
    }

    @Test
    void patientInconnuRenvoieNull() {
        graphql.document("{ patient(id: \"TN-9999999\") { id } }").execute()
                .errors().verify()
                .path("patient").valueIsNull();
    }

    @Test
    void ficheUrgenceVisibleDansLeDossierDuPatient() {
        graphql.document(ALERTE).execute().errors().verify()
                .path("enregistrerAlerte.id").entity(String.class).isEqualTo("ALR-0001")
                .path("enregistrerAlerte.horodatage").entity(String.class).isEqualTo("2026-11-17T10:30:00+01:00");

        graphql.document(FICHE.formatted("TN-0012345", "ALR-0001")).execute().errors().verify()
                .path("creerFicheUrgence.id").entity(String.class).isEqualTo("FIC-000001")
                .path("creerFicheUrgence.action").entity(String.class).isEqualTo("CONSULTATION_URGENCE")
                .path("creerFicheUrgence.patient.nom").entity(String.class).isEqualTo("Mansouri")
                .path("creerFicheUrgence.alerte.message").entity(String.class).isEqualTo("Fréquence cardiaque 165 bpm > 140 bpm");

        graphql.document("""
                { patient(id: "TN-0012345") {
                    alertesRecentes { id niveau valeur seuil }
                    fichesUrgence { id statutPriseEnCharge identifiantRendezVous alerte { id } } } }""")
                .execute().errors().verify()
                .path("patient.alertesRecentes[0].niveau").entity(String.class).isEqualTo("CRITIQUE")
                .path("patient.fichesUrgence[0].id").entity(String.class).isEqualTo("FIC-000001")
                .path("patient.fichesUrgence[0].identifiantRendezVous").entity(String.class).isEqualTo("RDV-000001")
                .path("patient.fichesUrgence[0].alerte.id").entity(String.class).isEqualTo("ALR-0001");
    }

    @Test
    void ficheAvecAlerteInconnueRenvoieNotFound() {
        graphql.document(FICHE.formatted("TN-0012345", "ALR-9999")).execute()
                .errors().satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    assertThat(errors.get(0).getMessage()).contains("ALR-9999");
                });
    }

    @Test
    void fichePourPatientInconnuRenvoieNotFound() {
        graphql.document(FICHE.formatted("TN-9999999", "ALR-0001")).execute()
                .errors().satisfy(errors -> assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }

    @Test
    void dateTimeInvalideRejetee() {
        graphql.document(ALERTE.replace("2026-11-17T10:30:00+01:00", "hier soir")).execute()
                .errors().satisfy(errors -> assertThat(errors).isNotEmpty()
                        .anySatisfy(e -> assertThat(e.getMessage()).contains("DateTime")));
    }
}
