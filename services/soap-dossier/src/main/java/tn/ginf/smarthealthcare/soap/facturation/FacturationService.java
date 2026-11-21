package tn.ginf.smarthealthcare.soap.facturation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.soap.Erreurs;
import tn.ginf.smarthealthcare.soap.generated.ActeMedical;
import tn.ginf.smarthealthcare.soap.generated.CodeErreur;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.FeuilleDeSoins;
import tn.ginf.smarthealthcare.soap.generated.LettreCle;
import tn.ginf.smarthealthcare.soap.seed.SeedRepository;
import tn.ginf.smarthealthcare.soap.seed.SeedRepository.CouvertureSeed;

/**
 * Émission des feuilles de soins (P2.SOAP.1, D26).
 * Montant d'un acte = coefficient × valeur de la lettre-clé ; remboursé = total × taux de la couverture.
 * Les feuilles émises sont conservées en mémoire (historique en P2.SOAP.2, PostgreSQL en P2.SOAP.5).
 */
@Service
public class FacturationService {

    private static final Logger log = LoggerFactory.getLogger(FacturationService.class);
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final SeedRepository seed;
    private final Clock horloge;
    private final DatatypeFactory datatypes;
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<FeuilleDeSoins> feuilles = new CopyOnWriteArrayList<>();

    public FacturationService(SeedRepository seed, Clock horloge) throws DatatypeConfigurationException {
        this.seed = seed;
        this.horloge = horloge;
        this.datatypes = DatatypeFactory.newInstance();
    }

    public FeuilleDeSoins emettre(String patientId, String codeVignette, List<ActeMedical> actes) throws ErreurMetierFault {
        if (!seed.patientExiste(patientId)) {
            throw Erreurs.faute(CodeErreur.PATIENT_INCONNU, "Aucun dossier pour l'identifiant " + patientId);
        }
        CouvertureSeed couverture = seed.couverture(patientId)
                .filter(c -> c.codeVignette().equals(codeVignette))
                .orElseThrow(() -> Erreurs.faute(CodeErreur.VIGNETTE_INVALIDE,
                        "La vignette " + codeVignette + " n'appartient pas au patient " + patientId));

        List<ActeMedical> actesCotes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ActeMedical acte : actes) {
            LocalDate date = versLocalDate(acte.getDateActe());
            if (date.isBefore(couverture.dateDebut()) || date.isAfter(couverture.dateFin())) {
                throw Erreurs.faute(CodeErreur.VIGNETTE_INVALIDE, "Vignette " + codeVignette + " valide du "
                        + couverture.dateDebut() + " au " + couverture.dateFin() + " : acte du " + date + " non couvert");
            }
            if (!seed.medecinExiste(acte.getIdentifiantMedecin())) {
                throw Erreurs.faute(CodeErreur.DONNEES_INVALIDES, "Médecin inconnu : " + acte.getIdentifiantMedecin());
            }
            BigDecimal montant = montantActe(acte.getLettreCle(), acte.getCoefficient());
            ActeMedical cote = copier(acte);
            cote.setMontant(montant);
            actesCotes.add(cote);
            total = total.add(montant);
        }

        LocalDate aujourdhui = LocalDate.now(horloge);
        FeuilleDeSoins f = new FeuilleDeSoins();
        f.setNumero("FS-%d-%06d".formatted(aujourdhui.getYear(), sequence.incrementAndGet()));
        f.setIdentifiantPatient(patientId);
        f.setCodeVignette(codeVignette);
        f.setDateEmission(versXml(aujourdhui));
        f.getActe().addAll(actesCotes);
        f.setMontantTotal(total.setScale(3, RoundingMode.HALF_UP));
        f.setTauxRemboursement(couverture.tauxRemboursement());
        f.setMontantRembourse(rembourse(total, couverture.tauxRemboursement()));
        f.setCodeCertification(certifier(f));
        feuilles.add(f);
        log.info("Feuille {} émise : patient={} {} acte(s) total={} TND remboursé={} TND ({} %)", f.getNumero(), patientId,
                actesCotes.size(), f.getMontantTotal(), f.getMontantRembourse(), f.getTauxRemboursement());
        return f;
    }

    /** Montant d'un acte : coefficient × valeur de la lettre-clé, arrondi au millime (D26). */
    public BigDecimal montantActe(LettreCle lettre, BigDecimal coefficient) throws ErreurMetierFault {
        BigDecimal valeur = seed.valeurLettreCle(lettre.value())
                .orElseThrow(() -> Erreurs.faute(CodeErreur.DONNEES_INVALIDES, "Lettre-clé sans valeur : " + lettre.value()));
        return coefficient.multiply(valeur).setScale(3, RoundingMode.HALF_UP);
    }

    /** Montant remboursé : montant × taux / 100, arrondi au millime. */
    public static BigDecimal rembourse(BigDecimal montant, int taux) {
        return montant.multiply(BigDecimal.valueOf(taux)).divide(CENT, 3, RoundingMode.HALF_UP);
    }

    /** Feuilles émises pour un patient (utilisé par l'historique, P2.SOAP.2). */
    public List<FeuilleDeSoins> feuillesDuPatient(String patientId) {
        return feuilles.stream().filter(f -> f.getIdentifiantPatient().equals(patientId)).toList();
    }

    /** Certification simulée : empreinte SHA-256 du contenu de la feuille, recalculable par un tiers. */
    static String certifier(FeuilleDeSoins f) {
        StringBuilder contenu = new StringBuilder()
                .append(f.getNumero()).append('|').append(f.getIdentifiantPatient()).append('|')
                .append(f.getCodeVignette()).append('|').append(f.getDateEmission());
        for (ActeMedical a : f.getActe()) {
            contenu.append('|').append(a.getLettreCle().value()).append(';').append(a.getCoefficient().toPlainString())
                    .append(';').append(a.getDateActe()).append(';').append(a.getIdentifiantMedecin())
                    .append(';').append(a.getMontant().toPlainString());
        }
        contenu.append('|').append(f.getMontantTotal().toPlainString()).append('|').append(f.getTauxRemboursement())
                .append('|').append(f.getMontantRembourse().toPlainString());
        try {
            byte[] empreinte = MessageDigest.getInstance("SHA-256").digest(contenu.toString().getBytes(StandardCharsets.UTF_8));
            return "SHA256:" + HexFormat.of().formatHex(empreinte);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ActeMedical copier(ActeMedical a) {
        ActeMedical c = new ActeMedical();
        c.setLettreCle(a.getLettreCle());
        c.setCoefficient(a.getCoefficient());
        c.setLibelle(a.getLibelle());
        c.setDateActe(a.getDateActe());
        c.setIdentifiantMedecin(a.getIdentifiantMedecin());
        return c;
    }

    private static LocalDate versLocalDate(XMLGregorianCalendar d) {
        return LocalDate.of(d.getYear(), d.getMonth(), d.getDay());
    }

    private XMLGregorianCalendar versXml(LocalDate d) {
        return datatypes.newXMLGregorianCalendar(d.toString());
    }
}
