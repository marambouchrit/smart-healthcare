package tn.ginf.smarthealthcare.soap.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.ginf.smarthealthcare.soap.persistance.Depots;
import tn.ginf.smarthealthcare.soap.persistance.LettreCleEntite;

/**
 * Accès au référentiel du dossier (patients, médecins, couvertures CNAM, lettres-clés, traitements), stocké dans
 * PostgreSQL (P2.SOAP.5) et chargé depuis contracts/seed par {@link ChargementSeed}.
 * L'API est restée celle de la phase 1 : les services métier n'ont pas changé.
 */
@Component
@Transactional(readOnly = true)
public class SeedRepository {

    public record CouvertureSeed(String patientId, String codeVignette, String filiere,
                                 LocalDate dateDebut, LocalDate dateFin, int tauxRemboursement) {}

    public record TraitementSeed(String id, String patientId, String medecinId, String medicament, String dosage,
                                 LocalDate dateDebut) {}

    private final Depots.Patients patients;
    private final Depots.Medecins medecins;
    private final Depots.Couvertures couvertures;
    private final Depots.LettresCles lettresCles;
    private final Depots.Traitements traitements;

    public SeedRepository(Depots.Patients patients, Depots.Medecins medecins, Depots.Couvertures couvertures,
                          Depots.LettresCles lettresCles, Depots.Traitements traitements) {
        this.patients = patients;
        this.medecins = medecins;
        this.couvertures = couvertures;
        this.lettresCles = lettresCles;
        this.traitements = traitements;
    }

    public boolean patientExiste(String identifiantPatient) {
        return patients.existsById(identifiantPatient);
    }

    public Optional<CouvertureSeed> couverture(String identifiantPatient) {
        return couvertures.findById(identifiantPatient).map(c -> new CouvertureSeed(c.getPatientId(), c.getCodeVignette(),
                c.getFiliere(), c.getDateDebut(), c.getDateFin(), c.getTauxRemboursement()));
    }

    public List<TraitementSeed> traitements(String patientId) {
        return traitements.findByPatientIdOrderByDateDebutAscIdAsc(patientId).stream()
                .map(t -> new TraitementSeed(t.getId(), t.getPatientId(), t.getMedecinId(), t.getMedicament(),
                        t.getDosage(), t.getDateDebut()))
                .toList();
    }

    public boolean medecinExiste(String identifiantMedecin) {
        return medecins.existsById(identifiantMedecin);
    }

    /** Valeur unitaire en dinars d'une lettre-clé. */
    public Optional<BigDecimal> valeurLettreCle(String code) {
        return lettresCles.findById(code).map(LettreCleEntite::getValeurUnitaire);
    }
}
