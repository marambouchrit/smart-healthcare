package tn.ginf.smarthealthcare.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.xml.datatype.DatatypeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.ginf.smarthealthcare.soap.facturation.FacturationService;
import tn.ginf.smarthealthcare.soap.generated.ActeMedical;
import tn.ginf.smarthealthcare.soap.generated.LettreCle;
import tn.ginf.smarthealthcare.soap.persistance.Depots;
import tn.ginf.smarthealthcare.soap.seed.ChargementSeed;

/** P2.SOAP.5 : schéma Flyway, chargement du seed dans PostgreSQL, feuilles de soins enregistrées en base. */
@SpringBootTest
@Import(PostgresTest.class)
class PersistanceTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired FacturationService facturation;
    @Autowired ObjectMapper mapper;
    @Autowired Depots.Patients patients;
    @Autowired Depots.Medecins medecins;
    @Autowired Depots.Couvertures couvertures;
    @Autowired Depots.LettresCles lettres;
    @Autowired Depots.Traitements traitements;

    @BeforeEach
    void vider() {
        jdbc.execute("TRUNCATE acte_medical, feuille_de_soins RESTART IDENTITY");
    }

    int compter(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void schemaCreeParFlywayEtSeedCharge() {
        assertThat(jdbc.queryForObject("SELECT max(version) FROM flyway_schema_history", String.class)).isEqualTo("1");
        assertThat(Map.of("patient", compter("patient"), "medecin", compter("medecin"), "couverture", compter("couverture"),
                "lettre_cle", compter("lettre_cle"), "traitement", compter("traitement")))
                .isEqualTo(Map.of("patient", 8, "medecin", 4, "couverture", 7, "lettre_cle", 7, "traitement", 10));
        assertThat(jdbc.queryForObject("SELECT valeur_unitaire FROM lettre_cle WHERE code = 'K'", BigDecimal.class))
                .isEqualByComparingTo("2.5");
    }

    @Test
    void unSecondChargementNAjouteRien() throws Exception {
        new ChargementSeed(mapper, patients, medecins, couvertures, lettres, traitements);
        assertThat(compter("patient")).isEqualTo(8);
        assertThat(compter("couverture")).isEqualTo(7);
    }

    @Test
    void feuilleEtActesEnregistresEnBase() throws Exception {
        ActeMedical a = new ActeMedical();
        a.setLettreCle(LettreCle.CS);
        a.setCoefficient(BigDecimal.ONE);
        a.setLibelle("Consultation");
        a.setDateActe(DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-11-17"));
        a.setIdentifiantMedecin("MED-001");
        var f = facturation.emettre("TN-0012345", "VIG-2026-100001", List.of(a));

        Map<String, Object> ligne = jdbc.queryForMap("SELECT numero, montant_total, montant_rembourse, code_certification "
                + "FROM feuille_de_soins WHERE id = 1");
        assertThat(ligne.get("numero")).isEqualTo(f.getNumero()).asString().matches("FS-\\d{4}-000001");
        assertThat((BigDecimal) ligne.get("montant_rembourse")).isEqualByComparingTo("48.000");
        assertThat(ligne.get("code_certification")).isEqualTo(f.getCodeCertification());
        assertThat(jdbc.queryForObject("SELECT lettre_cle || ' ' || coefficient || ' = ' || montant FROM acte_medical", String.class))
                .isEqualTo("CS 1.00 = 60.000");
        // relue depuis la base, la feuille est identique à celle renvoyée à l'émission
        var relue = facturation.feuillesDuPatient("TN-0012345").get(0);
        assertThat(relue.getCodeCertification()).isEqualTo(f.getCodeCertification());
        assertThat(relue.getActe().get(0).getMontant()).isEqualByComparingTo("60.000");
    }

    @Test
    void lesContraintesDuSchemaProtegentLesDonnees() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("INSERT INTO patient (id) VALUES ('TN-12')"))
                .hasMessageContaining("patient_id_check");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        jdbc.update("UPDATE couverture SET taux_remboursement = 120 WHERE patient_id = 'TN-0012345'"))
                .hasMessageContaining("taux_remboursement");
    }
}
