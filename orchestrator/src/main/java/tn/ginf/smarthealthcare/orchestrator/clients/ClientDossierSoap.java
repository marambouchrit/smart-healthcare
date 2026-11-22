package tn.ginf.smarthealthcare.orchestrator.clients;

import jakarta.xml.ws.WebServiceException;
import java.time.Duration;
import java.time.LocalDate;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.ginf.smarthealthcare.orchestrator.soap.DossierPortType;
import tn.ginf.smarthealthcare.orchestrator.soap.ErreurMetierFault;
import tn.ginf.smarthealthcare.orchestrator.soap.LettreCle;
import tn.ginf.smarthealthcare.orchestrator.soap.VerifierDroitsCNAM;
import tn.ginf.smarthealthcare.orchestrator.soap.VerifierDroitsCNAMResponse;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports;

/**
 * Étape 2 : client SOAP généré depuis contracts/soap/dossier.wsdl ; délais d'attente et pannes classées (P5.3, D54).
 */
@Component
public class ClientDossierSoap implements Ports.Dossier {

    private final DossierPortType port;
    private final LettreCle lettreCle;
    private final DatatypeFactory datatypes;

    public ClientDossierSoap(@Value("${orchestrateur.dossier.url}") String url,
                             @Value("${orchestrateur.lettre-cle-urgence}") String lettreCle,
                             @Value("${orchestrateur.appels.delai-connexion}") Duration delaiConnexion,
                             @Value("${orchestrateur.appels.delai-reponse}") Duration delaiReponse)
            throws DatatypeConfigurationException {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(DossierPortType.class);
        factory.setAddress(url);
        this.port = (DossierPortType) factory.create();
        HTTPClientPolicy politique = new HTTPClientPolicy();
        politique.setConnectionTimeout(delaiConnexion.toMillis());
        politique.setReceiveTimeout(delaiReponse.toMillis());
        ((HTTPConduit) ClientProxy.getClient(port).getConduit()).setClient(politique);
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
            var c = r.getCouverture();
            return new Ports.Droits(r.getStatut().value(), r.isPriseEnCharge(),
                    c == null ? null : c.getTauxRemboursement(), r.getMotif(),
                    c == null ? null : c.getCodeVignette(),
                    c == null ? null : LocalDate.parse(c.getDateFin().toXMLFormat()));
        } catch (ErreurMetierFault e) {
            // erreur métier (ex. PATIENT_INCONNU) : jamais réessayée
            throw new IllegalStateException("SOAP " + e.getFaultInfo().getCode() + " : " + e.getFaultInfo().getMessage(), e);
        } catch (WebServiceException e) {
            throw Pannes.traduire("SOAP", e);
        }
    }
}
