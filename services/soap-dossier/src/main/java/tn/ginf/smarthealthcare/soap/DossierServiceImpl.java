package tn.ginf.smarthealthcare.soap;

import jakarta.jws.WebService;
import java.time.LocalDate;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.soap.droits.DroitsService;
import tn.ginf.smarthealthcare.soap.droits.DroitsService.Droits;
import tn.ginf.smarthealthcare.soap.garanties.GarantiesService;
import tn.ginf.smarthealthcare.soap.generated.ComparerGaranties;
import tn.ginf.smarthealthcare.soap.generated.ComparerGarantiesResponse;
import tn.ginf.smarthealthcare.soap.generated.ConsulterHistorique;
import tn.ginf.smarthealthcare.soap.generated.ConsulterHistoriqueResponse;
import tn.ginf.smarthealthcare.soap.generated.Couverture;
import tn.ginf.smarthealthcare.soap.generated.DossierPortType;
import tn.ginf.smarthealthcare.soap.generated.EmettreFeuilleDeSoins;
import tn.ginf.smarthealthcare.soap.generated.EmettreFeuilleDeSoinsResponse;
import tn.ginf.smarthealthcare.soap.facturation.FacturationService;
import tn.ginf.smarthealthcare.soap.historique.HistoriqueService;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.Filiere;
import tn.ginf.smarthealthcare.soap.generated.VerifierDroitsCNAM;
import tn.ginf.smarthealthcare.soap.generated.VerifierDroitsCNAMResponse;
import tn.ginf.smarthealthcare.soap.seed.SeedRepository.CouvertureSeed;

/**
 * Implémentation de l'interface générée depuis contracts/soap/dossier.wsdl (approche top-down, D4).
 * Les 4 opérations du contrat : verifierDroitsCNAM (P1.1), emettreFeuilleDeSoins (P2.SOAP.1),
 * consulterHistorique (P2.SOAP.2), comparerGaranties (P2.SOAP.3).
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

    private final DroitsService droits;
    private final FacturationService facturation;
    private final HistoriqueService historique;
    private final GarantiesService garanties;
    private final DatatypeFactory datatypes;

    public DossierServiceImpl(DroitsService droits, FacturationService facturation, HistoriqueService historique,
                              GarantiesService garanties) throws DatatypeConfigurationException {
        this.droits = droits;
        this.facturation = facturation;
        this.historique = historique;
        this.garanties = garanties;
        this.datatypes = DatatypeFactory.newInstance();
    }

    @Override
    public VerifierDroitsCNAMResponse verifierDroitsCNAM(VerifierDroitsCNAM requete) throws ErreurMetierFault {
        String patientId = requete.getIdentifiantPatient();
        LocalDate date = versLocalDate(requete.getDatePrestation());
        log.info("verifierDroitsCNAM patient={} prestation={} date={}", patientId, requete.getLettreCle(), date);

        Droits d = droits.evaluer(patientId, date);
        VerifierDroitsCNAMResponse reponse = new VerifierDroitsCNAMResponse();
        reponse.setStatut(d.statut());
        reponse.setPriseEnCharge(d.ouverts());
        if (d.couverture() != null) {
            reponse.setCouverture(versCouverture(d.couverture()));
        }
        reponse.setMotif(d.motif());
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
    public ConsulterHistoriqueResponse consulterHistorique(ConsulterHistorique requete) throws ErreurMetierFault {
        ConsulterHistoriqueResponse reponse = new ConsulterHistoriqueResponse();
        reponse.setHistorique(historique.consulter(requete.getIdentifiantPatient(),
                requete.getDateDebut() == null ? null : versLocalDate(requete.getDateDebut()),
                requete.getDateFin() == null ? null : versLocalDate(requete.getDateFin())));
        return reponse;
    }

    @Override
    public ComparerGarantiesResponse comparerGaranties(ComparerGaranties requete) throws ErreurMetierFault {
        return garanties.comparer(requete.getPriseEnChargeA(), requete.getPriseEnChargeB());
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
