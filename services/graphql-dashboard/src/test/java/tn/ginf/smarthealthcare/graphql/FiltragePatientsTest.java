package tn.ginf.smarthealthcare.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;

/**
 * P2.GQL.2 sur les 8 patients du seed : CARDIO (TN-0012345 ELEVE, TN-0023456 MOYEN, TN-0078901 FAIBLE),
 * PNEUMO (TN-0034567 ELEVE, TN-0045678 MOYEN, TN-0089012 FAIBLE), GERIA (TN-0056789 ELEVE, TN-0067890 MOYEN).
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@Import(PostgresTest.class)
class FiltragePatientsTest extends PostgresTest.BaseVide {

    @Autowired GraphQlTester graphql;

    GraphQlTester.Response patients(String filtres) {
        return graphql.document("{ patients" + filtres + " { id niveauRisque } }").execute();
    }

    @Test
    void sansFiltreTousLesPatientsDuPlusARisqueAuMoins() {
        patients("").errors().verify()
                .path("patients").entityList(Object.class).hasSize(8)
                .path("patients[*].niveauRisque").entityList(String.class)
                .containsExactly("ELEVE", "ELEVE", "ELEVE", "MOYEN", "MOYEN", "MOYEN", "FAIBLE", "FAIBLE");
    }

    @Test
    void parService() {
        patients("(filtres: { serviceCode: \"CARDIO\" })")
                .path("patients[*].id").entityList(String.class).containsExactly("TN-0012345", "TN-0023456", "TN-0078901");
    }

    @Test
    void parPathologieSansCasseNiAccents() {
        patients("(filtres: { pathologie: \"diabete\" })").path("patients[*].id").entityList(String.class).containsExactly("TN-0067890");
        patients("(filtres: { pathologie: \"CARDIAQUE\" })").path("patients[*].id").entityList(String.class).containsExactly("TN-0012345");
    }

    @Test
    void parNiveauxDeRisque() {
        patients("(filtres: { niveauRisque: [ELEVE] })")
                .path("patients[*].id").entityList(String.class).contains("TN-0012345", "TN-0034567", "TN-0056789").hasSize(3);
        patients("(filtres: { niveauRisque: [MOYEN, FAIBLE] })").path("patients").entityList(Object.class).hasSize(5);
    }

    @Test
    void criteresCombinesParEt() {
        patients("(filtres: { serviceCode: \"PNEUMO\", niveauRisque: [ELEVE, MOYEN] })")
                .path("patients[*].id").entityList(String.class).containsExactly("TN-0034567", "TN-0045678");
        patients("(filtres: { serviceCode: \"GERIA\", pathologie: \"asthme\" })").path("patients").entityList(Object.class).hasSize(0);
    }

    @Test
    void serviceInconnuListeVide() {
        patients("(filtres: { serviceCode: \"DENTAIRE\" })").errors().verify().path("patients").entityList(Object.class).hasSize(0);
    }

    @Test
    void lesChampsDuDossierSontDisponiblesDansLaListe() {
        graphql.document("{ patients(filtres: { serviceCode: \"GERIA\" }) { nom service { libelle } traitementsEnCours { medicament } } }")
                .execute().errors().verify()
                .path("patients[0].service.libelle").entity(String.class).isEqualTo("Gériatrie")
                .path("patients[*].traitementsEnCours[*].medicament").entityList(String.class)
                .contains("Calcium + vitamine D3", "Metformine").hasSize(2);
    }

    @Test
    void listeDesServices() {
        graphql.document("{ services { code libelle } }").execute().errors().verify()
                .path("services[*].libelle").entityList(String.class).containsExactly("Cardiologie", "Gériatrie", "Pneumologie", "Urgences");
    }

    @Test
    void niveauDeRisqueInconnuRejeteParLeSchema() {
        patients("(filtres: { niveauRisque: [TRES_ELEVE] })").errors().satisfy(e -> org.assertj.core.api.Assertions.assertThat(e).isNotEmpty());
    }
}
