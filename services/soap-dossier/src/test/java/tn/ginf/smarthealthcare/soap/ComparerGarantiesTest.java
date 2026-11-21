package tn.ginf.smarthealthcare.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import javax.xml.datatype.DatatypeFactory;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tn.ginf.smarthealthcare.soap.generated.Avantage;
import tn.ginf.smarthealthcare.soap.generated.CodeErreur;
import tn.ginf.smarthealthcare.soap.generated.ComparerGaranties;
import tn.ginf.smarthealthcare.soap.generated.ComparerGarantiesResponse;
import tn.ginf.smarthealthcare.soap.generated.DossierPortType;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.LettreCle;
import tn.ginf.smarthealthcare.soap.generated.PriseEnCharge;
import tn.ginf.smarthealthcare.soap.generated.StatutDroits;

/** comparerGaranties par de vrais appels SOAP (D16). Valeurs : CS = 60 TND, K = 2,5 TND. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ComparerGarantiesTest {

    @LocalServerPort int port;
    DossierPortType client;

    @BeforeEach
    void client() {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(DossierPortType.class);
        factory.setAddress("http://localhost:" + port + "/ws/dossier");
        client = (DossierPortType) factory.create();
    }

    static PriseEnCharge pec(String patient, LettreCle lettre, String coef, String date) throws Exception {
        PriseEnCharge p = new PriseEnCharge();
        p.setIdentifiantPatient(patient);
        p.setLettreCle(lettre);
        p.setCoefficient(new BigDecimal(coef));
        p.setDatePrestation(DatatypeFactory.newInstance().newXMLGregorianCalendar(date));
        return p;
    }

    ComparerGarantiesResponse comparer(PriseEnCharge a, PriseEnCharge b) throws Exception {
        ComparerGaranties r = new ComparerGaranties();
        r.setPriseEnChargeA(a);
        r.setPriseEnChargeB(b);
        return client.comparerGaranties(r);
    }

    @Test
    void memeActeCouvertA80ContreA50() throws Exception {
        var r = comparer(pec("TN-0012345", LettreCle.CS, "1", "2026-11-17"), pec("TN-0023456", LettreCle.CS, "1", "2026-11-17"));
        assertThat(r.getGarantieA().getTauxRemboursement()).isEqualTo(80);
        assertThat(r.getGarantieA().getMontantActe()).isEqualByComparingTo("60.000");
        assertThat(r.getGarantieA().getMontantRembourse()).isEqualByComparingTo("48.000");
        assertThat(r.getGarantieB().getTauxRemboursement()).isEqualTo(50);
        assertThat(r.getGarantieB().getMontantRembourse()).isEqualByComparingTo("30.000");
        assertThat(r.getEcartMontantRembourse()).isEqualByComparingTo("18.000");
        assertThat(r.getPlusAvantageuse()).isEqualTo(Avantage.A);
        assertThat(r.getGarantieA().getPriseEnCharge().getIdentifiantPatient()).isEqualTo("TN-0012345");
    }

    @Test
    void droitsExpiresTauxZeroEtEcartNegatif() throws Exception {
        var r = comparer(pec("TN-0067890", LettreCle.K, "20", "2026-11-17"), pec("TN-0012345", LettreCle.K, "20", "2026-11-17"));
        assertThat(r.getGarantieA().getStatut()).isEqualTo(StatutDroits.EXPIRES);
        assertThat(r.getGarantieA().getTauxRemboursement()).isZero();
        assertThat(r.getGarantieA().getMontantActe()).isEqualByComparingTo("50.000");
        assertThat(r.getGarantieA().getMontantRembourse()).isEqualByComparingTo("0.000");
        assertThat(r.getEcartMontantRembourse()).isEqualByComparingTo("-40.000");
        assertThat(r.getPlusAvantageuse()).isEqualTo(Avantage.B);
    }

    @Test
    void memePatientDeuxDatesUneAvantLaVignette() throws Exception {
        var r = comparer(pec("TN-0012345", LettreCle.C, "1", "2025-12-31"), pec("TN-0012345", LettreCle.C, "1", "2026-01-02"));
        assertThat(r.getGarantieA().getStatut()).isEqualTo(StatutDroits.EXPIRES);
        assertThat(r.getGarantieB().getStatut()).isEqualTo(StatutDroits.OUVERTS);
        assertThat(r.getPlusAvantageuse()).isEqualTo(Avantage.B);
    }

    @Test
    void egalite() throws Exception {
        var r = comparer(pec("TN-0012345", LettreCle.V, "1", "2026-11-17"), pec("TN-0034567", LettreCle.V, "1", "2026-11-17"));
        assertThat(r.getEcartMontantRembourse()).isEqualByComparingTo("0");
        assertThat(r.getPlusAvantageuse()).isEqualTo(Avantage.EGALE);
    }

    @Test
    void nonAffilieStatutNonAffilie() throws Exception {
        var r = comparer(pec("TN-0089012", LettreCle.C, "1", "2026-11-17"), pec("TN-0012345", LettreCle.C, "1", "2026-11-17"));
        assertThat(r.getGarantieA().getStatut()).isEqualTo(StatutDroits.NON_AFFILIE);
        assertThat(r.getGarantieA().getTauxRemboursement()).isZero();
    }

    @Test
    void patientInconnuDansLUneDesPrisesEnCharge() {
        assertThatThrownBy(() -> comparer(pec("TN-0012345", LettreCle.C, "1", "2026-11-17"), pec("TN-9999999", LettreCle.C, "1", "2026-11-17")))
                .satisfies(e -> assertThat(((ErreurMetierFault) e).getFaultInfo().getCode()).isEqualTo(CodeErreur.PATIENT_INCONNU));
    }
}
