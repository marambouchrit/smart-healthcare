package tn.ginf.smarthealthcare.soap.historique;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.soap.Erreurs;
import tn.ginf.smarthealthcare.soap.facturation.FacturationService;
import tn.ginf.smarthealthcare.soap.generated.ActeMedical;
import tn.ginf.smarthealthcare.soap.generated.CodeErreur;
import tn.ginf.smarthealthcare.soap.generated.EntreeHistorique;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.FeuilleDeSoins;
import tn.ginf.smarthealthcare.soap.generated.Historique;
import tn.ginf.smarthealthcare.soap.generated.TypeEvenement;
import tn.ginf.smarthealthcare.soap.seed.SeedRepository;
import tn.ginf.smarthealthcare.soap.seed.SeedRepository.TraitementSeed;

/**
 * Historique médical certifié (P2.SOAP.2, D27) :
 * CONSULTATION = prescriptions du seed regroupées par (médecin, date) ; ACTE = actes des feuilles de soins émises.
 */
@Service
public class HistoriqueService {

    private static final Logger log = LoggerFactory.getLogger(HistoriqueService.class);

    private record Entree(LocalDate date, TypeEvenement type, String description, String medecin, String feuille) {}

    private final SeedRepository seed;
    private final FacturationService facturation;
    private final Clock horloge;
    private final DatatypeFactory datatypes;

    public HistoriqueService(SeedRepository seed, FacturationService facturation, Clock horloge)
            throws DatatypeConfigurationException {
        this.seed = seed;
        this.facturation = facturation;
        this.horloge = horloge;
        this.datatypes = DatatypeFactory.newInstance();
    }

    public Historique consulter(String patientId, LocalDate debut, LocalDate fin) throws ErreurMetierFault {
        if (!seed.patientExiste(patientId)) {
            throw Erreurs.faute(CodeErreur.PATIENT_INCONNU, "Aucun dossier pour l'identifiant " + patientId);
        }
        if (debut != null && fin != null && debut.isAfter(fin)) {
            throw Erreurs.faute(CodeErreur.DONNEES_INVALIDES, "Période invalide : " + debut + " est après " + fin);
        }

        List<Entree> entrees = new ArrayList<>();
        // Consultations : une par (médecin, date de prescription)
        Map<String, List<TraitementSeed>> parConsultation = seed.traitements(patientId).stream()
                .collect(Collectors.groupingBy(t -> t.dateDebut() + "|" + t.medecinId(), LinkedHashMap::new, Collectors.toList()));
        parConsultation.values().forEach(groupe -> {
            TraitementSeed premier = groupe.get(0);
            String prescriptions = groupe.stream().map(t -> t.medicament() + " " + t.dosage()).collect(Collectors.joining(", "));
            entrees.add(new Entree(premier.dateDebut(), TypeEvenement.CONSULTATION,
                    "Consultation et prescription : " + prescriptions, premier.medecinId(), null));
        });
        // Actes facturés : un par acte de chaque feuille de soins émise
        for (FeuilleDeSoins f : facturation.feuillesDuPatient(patientId)) {
            for (ActeMedical a : f.getActe()) {
                entrees.add(new Entree(versLocalDate(a.getDateActe()), TypeEvenement.ACTE,
                        a.getLettreCle().value() + " " + a.getCoefficient().stripTrailingZeros().toPlainString()
                                + " — " + a.getLibelle() + " (" + a.getMontant().toPlainString() + " TND)",
                        a.getIdentifiantMedecin(), f.getNumero()));
            }
        }

        Historique h = new Historique();
        h.setIdentifiantPatient(patientId);
        h.setDateCertification(datatypes.newXMLGregorianCalendar(
                OffsetDateTime.now(horloge).withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        entrees.stream()
                .filter(e -> debut == null || !e.date().isBefore(debut))
                .filter(e -> fin == null || !e.date().isAfter(fin))
                .sorted(Comparator.comparing(Entree::date).thenComparing(Entree::type))
                .map(this::versXml)
                .forEach(h.getEntree()::add);
        log.info("consulterHistorique patient={} période={}..{} -> {} entrée(s)", patientId,
                debut == null ? "début" : debut, fin == null ? "aujourd'hui" : fin, h.getEntree().size());
        return h;
    }

    private EntreeHistorique versXml(Entree e) {
        EntreeHistorique x = new EntreeHistorique();
        x.setDate(datatypes.newXMLGregorianCalendar(e.date().toString()));
        x.setType(e.type());
        x.setDescription(e.description());
        x.setIdentifiantMedecin(e.medecin());
        x.setNumeroFeuilleDeSoins(e.feuille());
        return x;
    }

    private static LocalDate versLocalDate(XMLGregorianCalendar d) {
        return LocalDate.of(d.getYear(), d.getMonth(), d.getDay());
    }
}
