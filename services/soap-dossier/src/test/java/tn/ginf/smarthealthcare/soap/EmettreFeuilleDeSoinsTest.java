package tn.ginf.smarthealthcare.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import javax.xml.datatype.DatatypeFactory;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.ginf.smarthealthcare.soap.generated.ActeMedical;
import tn.ginf.smarthealthcare.soap.generated.CodeErreur;
import tn.ginf.smarthealthcare.soap.generated.DossierPortType;
import tn.ginf.smarthealthcare.soap.generated.EmettreFeuilleDeSoins;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.FeuilleDeSoins;
import tn.ginf.smarthealthcare.soap.generated.LettreCle;

/**
 * emettreFeuilleDeSoins par de vrais appels SOAP (schéma validé en entrée et en sortie par le service).
 * Horloge fixée au 2026-11-17 (Tunis). Valeurs des lettres-clés : contracts/seed/lettres-cles.json.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTest.class)
class EmettreFeuilleDeSoinsTest {

    @TestConfiguration
    static class Horloge {
        @Bean @Primary
        Clock horlogeFixe() {
            return Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), ZoneId.of("Africa/Tunis"));
        }
    }

    @LocalServerPort int port;
    DossierPortType client;

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void client() {
        // chaque test part sans feuille de soins, numérotation à 1 (FS-2026-000001)
        jdbc.execute("TRUNCATE acte_medical, feuille_de_soins RESTART IDENTITY");
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(DossierPortType.class);
        factory.setAddress("http://localhost:" + port + "/ws/dossier");
        client = (DossierPortType) factory.create();
    }

    static ActeMedical acte(LettreCle lettre, String coefficient, String date, String medecin) throws Exception {
        ActeMedical a = new ActeMedical();
        a.setLettreCle(lettre);
        a.setCoefficient(new BigDecimal(coefficient));
        a.setLibelle("Acte " + lettre.value());
        a.setDateActe(DatatypeFactory.newInstance().newXMLGregorianCalendar(date));
        a.setIdentifiantMedecin(medecin);
        return a;
    }

    static EmettreFeuilleDeSoins demande(String patient, String vignette, ActeMedical... actes) {
        EmettreFeuilleDeSoins d = new EmettreFeuilleDeSoins();
        d.setIdentifiantPatient(patient);
        d.setCodeVignette(vignette);
        d.getActe().addAll(java.util.List.of(actes));
        return d;
    }

    @Test
    void consultationEtEcgRemboursesA80() throws Exception {
        FeuilleDeSoins f = client.emettreFeuilleDeSoins(demande("TN-0012345", "VIG-2026-100001",
                acte(LettreCle.CS, "1", "2026-11-17", "MED-001"),
                acte(LettreCle.K, "10", "2026-11-17", "MED-001"))).getFeuilleDeSoins();

        assertThat(f.getNumero()).isEqualTo("FS-2026-000001");
        assertThat(f.getDateEmission().toXMLFormat()).isEqualTo("2026-11-17");
        assertThat(f.getActe()).extracting(ActeMedical::getMontant)
                .containsExactly(new BigDecimal("60.000"), new BigDecimal("25.000"));
        assertThat(f.getMontantTotal()).isEqualByComparingTo("85.000");
        assertThat(f.getTauxRemboursement()).isEqualTo(80);
        assertThat(f.getMontantRembourse()).isEqualByComparingTo("68.000");
        assertThat(f.getCodeCertification()).matches("SHA256:[0-9a-f]{64}");
    }

    @Test
    void tauxDe50EtNumerotationSequentielle() throws Exception {
        client.emettreFeuilleDeSoins(demande("TN-0012345", "VIG-2026-100001", acte(LettreCle.C, "1", "2026-11-17", "MED-004")));
        FeuilleDeSoins f = client.emettreFeuilleDeSoins(demande("TN-0023456", "VIG-2026-100002",
                acte(LettreCle.B, "120", "2026-11-16", "MED-001"))).getFeuilleDeSoins();
        assertThat(f.getNumero()).isEqualTo("FS-2026-000002");
        assertThat(f.getMontantTotal()).isEqualByComparingTo("48.000");   // 120 × 0,4
        assertThat(f.getMontantRembourse()).isEqualByComparingTo("24.000"); // 50 %
    }

    @Test
    void vignetteExpireeRefusee() {
        assertThatThrownBy(() -> client.emettreFeuilleDeSoins(demande("TN-0067890", "VIG-2025-100006",
                acte(LettreCle.C, "1", "2026-11-17", "MED-003"))))
                .isInstanceOf(ErreurMetierFault.class)
                .satisfies(e -> assertThat(((ErreurMetierFault) e).getFaultInfo().getCode()).isEqualTo(CodeErreur.VIGNETTE_INVALIDE));
    }

    @Test
    void vignetteDUnAutrePatientRefusee() {
        assertThatThrownBy(() -> client.emettreFeuilleDeSoins(demande("TN-0012345", "VIG-2026-100002",
                acte(LettreCle.C, "1", "2026-11-17", "MED-001"))))
                .satisfies(e -> assertThat(((ErreurMetierFault) e).getFaultInfo().getCode()).isEqualTo(CodeErreur.VIGNETTE_INVALIDE));
    }

    @Test
    void patientNonAffilieRefuse() {
        assertThatThrownBy(() -> client.emettreFeuilleDeSoins(demande("TN-0089012", "VIG-2026-100008",
                acte(LettreCle.C, "1", "2026-11-17", "MED-002"))))
                .satisfies(e -> assertThat(((ErreurMetierFault) e).getFaultInfo().getCode()).isEqualTo(CodeErreur.VIGNETTE_INVALIDE));
    }

    @Test
    void patientInconnuRefuse() {
        assertThatThrownBy(() -> client.emettreFeuilleDeSoins(demande("TN-9999999", "VIG-2026-100001",
                acte(LettreCle.C, "1", "2026-11-17", "MED-001"))))
                .satisfies(e -> assertThat(((ErreurMetierFault) e).getFaultInfo().getCode()).isEqualTo(CodeErreur.PATIENT_INCONNU));
    }

    @Test
    void medecinInconnuRefuse() {
        assertThatThrownBy(() -> client.emettreFeuilleDeSoins(demande("TN-0012345", "VIG-2026-100001",
                acte(LettreCle.C, "1", "2026-11-17", "MED-999"))))
                .satisfies(e -> assertThat(((ErreurMetierFault) e).getFaultInfo().getCode()).isEqualTo(CodeErreur.DONNEES_INVALIDES));
    }

    @Test
    void certificationChangeSiLeContenuChange() throws Exception {
        String c1 = client.emettreFeuilleDeSoins(demande("TN-0012345", "VIG-2026-100001",
                acte(LettreCle.C, "1", "2026-11-17", "MED-001"))).getFeuilleDeSoins().getCodeCertification();
        String c2 = client.emettreFeuilleDeSoins(demande("TN-0012345", "VIG-2026-100001",
                acte(LettreCle.C, "2", "2026-11-17", "MED-001"))).getFeuilleDeSoins().getCodeCertification();
        assertThat(c1).isNotEqualTo(c2);
    }
}
