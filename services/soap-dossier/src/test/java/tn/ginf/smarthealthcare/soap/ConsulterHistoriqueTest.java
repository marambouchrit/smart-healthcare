package tn.ginf.smarthealthcare.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tn.ginf.smarthealthcare.soap.EmettreFeuilleDeSoinsTest.acte;
import static tn.ginf.smarthealthcare.soap.EmettreFeuilleDeSoinsTest.demande;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
import tn.ginf.smarthealthcare.soap.generated.CodeErreur;
import tn.ginf.smarthealthcare.soap.generated.ConsulterHistorique;
import tn.ginf.smarthealthcare.soap.generated.DossierPortType;
import tn.ginf.smarthealthcare.soap.generated.EntreeHistorique;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.Historique;
import tn.ginf.smarthealthcare.soap.generated.LettreCle;
import tn.ginf.smarthealthcare.soap.generated.TypeEvenement;

/** consulterHistorique par de vrais appels SOAP (D27). Horloge fixée au 2026-11-17 10:00 (Tunis). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTest.class)
class ConsulterHistoriqueTest {

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

    Historique historique(String patient, String debut, String fin) throws Exception {
        ConsulterHistorique r = new ConsulterHistorique();
        r.setIdentifiantPatient(patient);
        DatatypeFactory df = DatatypeFactory.newInstance();
        if (debut != null) r.setDateDebut(df.newXMLGregorianCalendar(debut));
        if (fin != null) r.setDateFin(df.newXMLGregorianCalendar(fin));
        return client.consulterHistorique(r).getHistorique();
    }

    @Test
    void prescriptionsDuMemeJourRegroupeesEnUneConsultation() throws Exception {
        Historique h = historique("TN-0012345", null, null);
        assertThat(h.getIdentifiantPatient()).isEqualTo("TN-0012345");
        assertThat(h.getDateCertification().toXMLFormat()).isEqualTo("2026-11-17T10:00:00+01:00");
        assertThat(h.getEntree()).hasSize(1);
        EntreeHistorique e = h.getEntree().get(0);
        assertThat(e.getDate().toXMLFormat()).isEqualTo("2026-02-10");
        assertThat(e.getType()).isEqualTo(TypeEvenement.CONSULTATION);
        assertThat(e.getIdentifiantMedecin()).isEqualTo("MED-001");
        assertThat(e.getDescription()).contains("Bisoprolol 5 mg").contains("Furosémide 40 mg");
        assertThat(e.getNumeroFeuilleDeSoins()).isNull();
    }

    @Test
    void actesFacturesAjoutesDansLOrdreChronologique() throws Exception {
        client.emettreFeuilleDeSoins(demande("TN-0012345", "VIG-2026-100001",
                acte(LettreCle.CS, "1", "2026-11-17", "MED-001"),
                acte(LettreCle.K, "10", "2026-11-17", "MED-001")));
        List<EntreeHistorique> e = historique("TN-0012345", null, null).getEntree();
        assertThat(e).extracting(EntreeHistorique::getType)
                .containsExactly(TypeEvenement.CONSULTATION, TypeEvenement.ACTE, TypeEvenement.ACTE);
        assertThat(e.get(1).getNumeroFeuilleDeSoins()).isEqualTo("FS-2026-000001");
        assertThat(e.get(1).getDescription()).isEqualTo("CS 1 — Acte CS (60.000 TND)");
        assertThat(e.get(2).getDescription()).isEqualTo("K 10 — Acte K (25.000 TND)");
    }

    @Test
    void filtreParPeriode() throws Exception {
        client.emettreFeuilleDeSoins(demande("TN-0012345", "VIG-2026-100001", acte(LettreCle.C, "1", "2026-11-17", "MED-001")));
        assertThat(historique("TN-0012345", "2026-11-01", null).getEntree())
                .extracting(EntreeHistorique::getType).containsExactly(TypeEvenement.ACTE);
        assertThat(historique("TN-0012345", null, "2026-03-01").getEntree())
                .extracting(EntreeHistorique::getType).containsExactly(TypeEvenement.CONSULTATION);
        assertThat(historique("TN-0012345", "2026-03-01", "2026-10-31").getEntree()).isEmpty();
    }

    @Test
    void deuxConsultationsADesDatesDifferentes() throws Exception {
        assertThat(historique("TN-0045678", null, null).getEntree())
                .extracting(x -> x.getDate().toXMLFormat()).containsExactly("2026-04-05", "2026-09-12");
    }

    @Test
    void patientInconnu() {
        assertThatThrownBy(() -> historique("TN-9999999", null, null))
                .satisfies(e -> assertThat(((ErreurMetierFault) e).getFaultInfo().getCode()).isEqualTo(CodeErreur.PATIENT_INCONNU));
    }

    @Test
    void periodeInversee() {
        assertThatThrownBy(() -> historique("TN-0012345", "2026-12-01", "2026-01-01"))
                .satisfies(e -> assertThat(((ErreurMetierFault) e).getFaultInfo().getCode()).isEqualTo(CodeErreur.DONNEES_INVALIDES));
    }
}
