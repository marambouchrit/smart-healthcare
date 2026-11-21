package tn.ginf.smarthealthcare.soap;

import jakarta.jws.WebService;
import java.time.LocalDate;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.soap.generated.CodeErreur;
import tn.ginf.smarthealthcare.soap.generated.ComparerGaranties;
import tn.ginf.smarthealthcare.soap.generated.ComparerGarantiesResponse;
import tn.ginf.smarthealthcare.soap.generated.ConsulterHistorique;
import tn.ginf.smarthealthcare.soap.generated.ConsulterHistoriqueResponse;
import tn.ginf.smarthealthcare.soap.generated.Couverture;
import tn.ginf.smarthealthcare.soap.generated.DossierPortType;
import tn.ginf.smarthealthcare.soap.generated.EmettreFeuilleDeSoins;
import tn.ginf.smarthealthcare.soap.generated.EmettreFeuilleDeSoinsResponse;
import tn.ginf.smarthealthcare.soap.facturation.FacturationService;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.Filiere;
import tn.ginf.smarthealthcare.soap.generated.StatutDroits;
import tn.ginf.smarthealthcare.soap.generated.VerifierDroitsCNAM;
import tn.ginf.smarthealthcare.soap.generated.VerifierDroitsCNAMResponse;
import tn.ginf.smarthealthcare.soap.seed.SeedRepository;
import tn.ginf.smarthealthcare.soap.seed.SeedRepository.CouvertureSeed;

/**
 * Implémentation de l'interface générée depuis contracts/soap/dossier.wsdl (approche top-down, D4).
 * Implémentées : verifierDroitsCNAM (P1.1), emettreFeuilleDeSoins (P2.SOAP.1).
 * consulterHistorique et comparerGaranties arrivent en P2.SOAP.2 et P2.SOAP.3.
 */
@Service
@WebService(
        serviceName = "DossierService",
        portName = "DossierPort",
        targetNamespace = "http://hopital.tn/dossier",
        endpointInterface = "tn.ginf.smarthealthcare.soap.generated.DossierPortType",
        wsdlLocation = "classpath:wsdl/dossier.wsdl")
public class DossierServiceImpl implements DossierPortType {

    private static final Logger log = LoggerFactory.getLogger(DossierServiceImpl.class);

    private final SeedRepository seed;
    private final FacturationService facturation;
    private final DatatypeFactory datatypes;

    public DossierServiceImpl(SeedRepository seed, FacturationService facturation) throws DatatypeConfigurationException {
        this.seed = seed;
        this.facturation = facturation;
        this.datatypes = DatatypeFactory.newInstance();
    }

    @Override
    public VerifierDroitsCNAMResponse verifierDroitsCNAM(VerifierDroitsCNAM requete) throws ErreurMetierFault {
        String patientId = requete.getIdentifiantPatient();
        LocalDate date = versLocalDate(requete.getDatePrestation());
        log.info("verifierDroitsCNAM patient={} prestation={} date={}", patientId, requete.getLettreCle(), date);

        if (!seed.patientExiste(patientId)) {
            throw Erreurs.faute(CodeErreur.PATIENT_INCONNU, "Aucun dossier pour l'identifiant " + patientId);
        }

        VerifierDroitsCNAMResponse reponse = new VerifierDroitsCNAMResponse();
        CouvertureSeed c = seed.couverture(patientId).orElse(null);
        if (c == null) {
            reponse.setStatut(StatutDroits.NON_AFFILIE);
            reponse.setPriseEnCharge(false);
            reponse.setMotif("Aucune affiliation CNAM pour ce patient");
        } else if (date.isBefore(c.dateDebut()) || date.isAfter(c.dateFin())) {
            reponse.setStatut(StatutDroits.EXPIRES);
            reponse.setPriseEnCharge(false);
            reponse.setCouverture(versCouverture(c));
            reponse.setMotif("Vignette valide du " + c.dateDebut() + " au " + c.dateFin());
        } else {
            reponse.setStatut(StatutDroits.OUVERTS);
            reponse.setPriseEnCharge(true);
            reponse.setCouverture(versCouverture(c));
        }
        log.info("verifierDroitsCNAM patient={} -> {}", patientId, reponse.getStatut());
        return reponse;
    }

    @Override
    public EmettreFeuilleDeSoinsResponse emettreFeuilleDeSoins(EmettreFeuilleDeSoins requete) throws ErreurMetierFault {
        log.info("emettreFeuilleDeSoins patient={} vignette={} actes={}", requete.getIdentifiantPatient(),
                requete.getCodeVignette(), requete.getActe().size());
        EmettreFeuilleDeSoinsResponse reponse = new EmettreFeuilleDeSoinsResponse();
        reponse.setFeuilleDeSoins(facturation.emettre(requete.getIdentifiantPatient(), requete.getCodeVignette(), requete.getActe()));
        return reponse;
    }

    @Override
    public ConsulterHistoriqueResponse consulterHistorique(ConsulterHistorique requete) {
        throw new UnsupportedOperationException("consulterHistorique : prévu en P2.SOAP.2");
    }

    @Override
    public ComparerGarantiesResponse comparerGaranties(ComparerGaranties requete) {
        throw new UnsupportedOperationException("comparerGaranties : prévu en P2.SOAP.3");
    }

    private Couverture versCouverture(CouvertureSeed c) {
        Couverture couverture = new Couverture();
        couverture.setIdentifiantPatient(c.patientId());
        couverture.setCodeVignette(c.codeVignette());
        couverture.setFiliere(Filiere.fromValue(c.filiere()));
        couverture.setDateDebut(versXml(c.dateDebut()));
        couverture.setDateFin(versXml(c.dateFin()));
        couverture.setTauxRemboursement(c.tauxRemboursement());
        return couverture;
    }

    private static LocalDate versLocalDate(XMLGregorianCalendar d) {
        return LocalDate.of(d.getYear(), d.getMonth(), d.getDay());
    }

    private XMLGregorianCalendar versXml(LocalDate d) {
        return datatypes.newXMLGregorianCalendar(d.toString());
    }
}
