package tn.ginf.smarthealthcare.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.xml.ws.WebServiceException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.xml.datatype.DatatypeFactory;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tn.ginf.smarthealthcare.soap.generated.CodeErreur;
import tn.ginf.smarthealthcare.soap.generated.DossierPortType;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.LettreCle;
import tn.ginf.smarthealthcare.soap.generated.StatutDroits;
import tn.ginf.smarthealthcare.soap.generated.VerifierDroitsCNAM;
import tn.ginf.smarthealthcare.soap.generated.VerifierDroitsCNAMResponse;

/** Appels SOAP réels (HTTP) sur le service démarré, avec un client généré depuis le même WSDL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VerifierDroitsCnamTest {

    @LocalServerPort
    int port;

    DossierPortType client;

    @BeforeEach
    void client() {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(DossierPortType.class);
        factory.setAddress("http://localhost:" + port + "/ws/dossier");
        client = (DossierPortType) factory.create();
    }

    private VerifierDroitsCNAM requete(String patientId) throws Exception {
        VerifierDroitsCNAM r = new VerifierDroitsCNAM();
        r.setIdentifiantPatient(patientId);
        r.setLettreCle(LettreCle.CS);
        r.setDatePrestation(DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-11-17"));
        return r;
    }

    @Test
    void patientCouvertDroitsOuverts() throws Exception {
        VerifierDroitsCNAMResponse r = client.verifierDroitsCNAM(requete("TN-0012345"));
        assertThat(r.getStatut()).isEqualTo(StatutDroits.OUVERTS);
        assertThat(r.isPriseEnCharge()).isTrue();
        assertThat(r.getCouverture().getCodeVignette()).isEqualTo("VIG-2026-100001");
        assertThat(r.getCouverture().getTauxRemboursement()).isEqualTo(80);
    }

    @Test
    void vignetteExpiree() throws Exception {
        VerifierDroitsCNAMResponse r = client.verifierDroitsCNAM(requete("TN-0067890"));
        assertThat(r.getStatut()).isEqualTo(StatutDroits.EXPIRES);
        assertThat(r.isPriseEnCharge()).isFalse();
        assertThat(r.getMotif()).contains("2025-12-31");
    }

    @Test
    void patientNonAffilie() throws Exception {
        VerifierDroitsCNAMResponse r = client.verifierDroitsCNAM(requete("TN-0089012"));
        assertThat(r.getStatut()).isEqualTo(StatutDroits.NON_AFFILIE);
        assertThat(r.isPriseEnCharge()).isFalse();
        assertThat(r.getCouverture()).isNull();
    }

    @Test
    void patientInconnuRenvoieUneSoapFault() {
        assertThatThrownBy(() -> client.verifierDroitsCNAM(requete("TN-9999999")))
                .isInstanceOf(ErreurMetierFault.class)
                .satisfies(e -> assertThat(((ErreurMetierFault) e).getFaultInfo().getCode())
                        .isEqualTo(CodeErreur.PATIENT_INCONNU));
    }

    @Test
    void identifiantMalFormeRejeteParLeSchema() {
        assertThatThrownBy(() -> client.verifierDroitsCNAM(requete("TN-12")))
                .isInstanceOf(WebServiceException.class)
                .hasMessageContaining("cvc-pattern-valid");
    }

    @Test
    void leServicePublieLeWsdlDuContrat() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String wsdl = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ws/dossier?wsdl")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertThat(wsdl).contains("targetNamespace=\"http://hopital.tn/dossier\"")
                .contains("verifierDroitsCNAM").contains("comparerGaranties");
    }
}
