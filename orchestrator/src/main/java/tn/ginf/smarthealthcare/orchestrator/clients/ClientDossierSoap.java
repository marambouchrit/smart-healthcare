package tn.ginf.smarthealthcare.orchestrator.clients;

import java.time.LocalDate;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.ginf.smarthealthcare.orchestrator.soap.DossierPortType;
import tn.ginf.smarthealthcare.orchestrator.soap.ErreurMetierFault;
import tn.ginf.smarthealthcare.orchestrator.soap.LettreCle;
import tn.ginf.smarthealthcare.orchestrator.soap.VerifierDroitsCNAM;
import tn.ginf.smarthealthcare.orchestrator.soap.VerifierDroitsCNAMResponse;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports;

/** Étape 2 : client SOAP généré depuis contracts/soap/dossier.wsdl. */
@Component
public class ClientDossierSoap implements Ports.Dossier {

    private final DossierPortType port;
    private final LettreCle lettreCle;
    private final DatatypeFactory datatypes;

    public ClientDossierSoap(@Value("${orchestrateur.dossier.url}") String url,
                             @Value("${orchestrateur.lettre-cle-urgence}") String lettreCle)
            throws DatatypeConfigurationException {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(DossierPortType.class);
        factory.setAddress(url);
        this.port = (DossierPortType) factory.create();
        this.lettreCle = LettreCle.fromValue(lettreCle);
        this.datatypes = DatatypeFactory.newInstance();
    }

    @Override
    public Ports.Droits verifierDroits(String patientId, LocalDate datePrestation) {
        VerifierDroitsCNAM requete = new VerifierDroitsCNAM();
        requete.setIdentifiantPatient(patientId);
        requete.setLettreCle(lettreCle);
        requete.setDatePrestation(datatypes.newXMLGregorianCalendar(datePrestation.toString()));
        try {
            VerifierDroitsCNAMResponse r = port.verifierDroitsCNAM(requete);
            Integer taux = r.getCouverture() == null ? null : r.getCouverture().getTauxRemboursement();
            return new Ports.Droits(r.getStatut().value(), r.isPriseEnCharge(), taux, r.getMotif());
        } catch (ErreurMetierFault e) {
            throw new IllegalStateException("SOAP " + e.getFaultInfo().getCode() + " : " + e.getFaultInfo().getMessage(), e);
        }
    }
}
