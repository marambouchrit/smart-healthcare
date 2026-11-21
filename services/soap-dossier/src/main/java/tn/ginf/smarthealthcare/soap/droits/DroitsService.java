package tn.ginf.smarthealthcare.soap.droits;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.soap.Erreurs;
import tn.ginf.smarthealthcare.soap.generated.CodeErreur;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.StatutDroits;
import tn.ginf.smarthealthcare.soap.seed.SeedRepository;
import tn.ginf.smarthealthcare.soap.seed.SeedRepository.CouvertureSeed;

/** Règle unique des droits CNAM, partagée par verifierDroitsCNAM et comparerGaranties. */
@Service
public class DroitsService {

    /** couverture est null si le patient n'est pas affilié. */
    public record Droits(StatutDroits statut, CouvertureSeed couverture, String motif) {
        public boolean ouverts() {
            return statut == StatutDroits.OUVERTS;
        }

        /** Taux effectivement applicable : 0 si les droits ne sont pas ouverts. */
        public int tauxApplicable() {
            return ouverts() ? couverture.tauxRemboursement() : 0;
        }
    }

    private final SeedRepository seed;

    public DroitsService(SeedRepository seed) {
        this.seed = seed;
    }

    public Droits evaluer(String patientId, LocalDate date) throws ErreurMetierFault {
        if (!seed.patientExiste(patientId)) {
            throw Erreurs.faute(CodeErreur.PATIENT_INCONNU, "Aucun dossier pour l'identifiant " + patientId);
        }
        CouvertureSeed c = seed.couverture(patientId).orElse(null);
        if (c == null) {
            return new Droits(StatutDroits.NON_AFFILIE, null, "Aucune affiliation CNAM pour ce patient");
        }
        if (date.isBefore(c.dateDebut()) || date.isAfter(c.dateFin())) {
            return new Droits(StatutDroits.EXPIRES, c, "Vignette valide du " + c.dateDebut() + " au " + c.dateFin());
        }
        return new Droits(StatutDroits.OUVERTS, c, null);
    }
}
