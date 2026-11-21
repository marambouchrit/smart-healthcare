package tn.ginf.smarthealthcare.rest.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.ginf.smarthealthcare.rest.persistance.Depots;
import tn.ginf.smarthealthcare.rest.persistance.Entites.MedecinEntite;
import tn.ginf.smarthealthcare.rest.persistance.Entites.PatientEntite;
import tn.ginf.smarthealthcare.rest.persistance.Entites.TraitementEntite;

/**
 * Référentiel du portail (P2.REST.7) : contracts/seed est chargé dans PostgreSQL au démarrage (lignes absentes
 * seulement) ; patients, médecins et traitements sont ensuite lus en base. Les plannings restent lus dans le
 * fichier : ils servent seulement à générer les créneaux.
 */
@Component
public class SeedRepository {

    private static final Logger log = LoggerFactory.getLogger(SeedRepository.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Adresse(String rue, String ville, String codePostal) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Patient(String id, String nom, String prenom, LocalDate dateNaissance, String sexe, String telephone,
                          String email, Adresse adresse, String medecinTraitantId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Medecin(String id, String nom, String prenom, String serviceCode) {}

    public record Planning(String medecinId, List<String> jours, LocalTime debut, LocalTime fin, int dureeMinutes) {}

    public record Traitement(String id, String patientId, String medecinId, String medicament, String dosage,
                             String posologie, LocalDate dateDebut, LocalDate dateFin, String statut) {}

    private final Depots.Patients patients;
    private final Depots.Medecins medecins;
    private final Depots.Traitements traitements;
    private final List<Patient> patientsDuSeed;
    private final List<Planning> plannings;

    public SeedRepository(ObjectMapper mapper, Depots.Patients patients, Depots.Medecins medecins,
                          Depots.Traitements traitements) throws IOException {
        this.patients = patients;
        this.medecins = medecins;
        this.traitements = traitements;
        this.patientsDuSeed = lire(mapper, "patients", new TypeReference<List<Patient>>() {});
        this.plannings = List.copyOf(lire(mapper, "plannings", new TypeReference<List<Planning>>() {}));

        List<MedecinEntite> m = lire(mapper, "medecins", new TypeReference<List<Medecin>>() {}).stream()
                .filter(x -> !medecins.existsById(x.id()))
                .map(x -> new MedecinEntite(x.id(), x.nom(), x.prenom(), x.serviceCode())).toList();
        medecins.saveAll(m);
        List<PatientEntite> p = patientsDuSeed.stream().filter(x -> !patients.existsById(x.id())).map(SeedRepository::entite).toList();
        patients.saveAll(p);
        List<TraitementEntite> t = lire(mapper, "traitements", new TypeReference<List<Traitement>>() {}).stream()
                .filter(x -> !traitements.existsById(x.id()))
                .map(x -> new TraitementEntite(x.id(), x.patientId(), x.medecinId(), x.medicament(), x.dosage(), x.posologie(),
                        x.dateDebut(), x.dateFin(), x.statut())).toList();
        traitements.saveAll(t);
        log.info("Seed : {} médecin(s), {} patient(s), {} traitement(s) ajoutés (base : {} patients) ; {} plannings",
                m.size(), p.size(), t.size(), patients.count(), plannings.size());
    }

    private static <T> List<T> lire(ObjectMapper mapper, String fichier, TypeReference<List<T>> type) throws IOException {
        try (InputStream in = new ClassPathResource("seed/" + fichier + ".json").getInputStream()) {
            return mapper.readValue(in, type);
        }
    }

    private static PatientEntite entite(Patient x) {
        return new PatientEntite(x.id(), x.nom(), x.prenom(), x.dateNaissance(), x.sexe(), x.telephone(), x.email(),
                x.adresse().rue(), x.adresse().ville(), x.adresse().codePostal(), x.medecinTraitantId());
    }

    static Patient patient(PatientEntite e) {
        return new Patient(e.id, e.nom, e.prenom, e.dateNaissance, e.sexe, e.telephone, e.email,
                new Adresse(e.rue, e.ville, e.codePostal), e.medecinTraitantId);
    }

    /** Remet les coordonnées des patients à leur valeur du seed (tests, réinitialisation d'une démonstration). */
    @Transactional
    public void restaurerProfils() {
        patients.saveAll(patientsDuSeed.stream().map(SeedRepository::entite).toList());
    }

    public Optional<Patient> patient(String id) {
        return patients.findById(id).map(SeedRepository::patient);
    }

    public Optional<Medecin> medecin(String id) {
        return medecins.findById(id).map(e -> new Medecin(e.id, e.nom, e.prenom, e.serviceCode));
    }

    public List<Planning> plannings() {
        return plannings;
    }

    public List<Traitement> traitements() {
        return traitements.findAll().stream()
                .map(e -> new Traitement(e.id, e.patientId, e.medecinId, e.medicament, e.dosage, e.posologie,
                        e.dateDebut, e.dateFin, e.statut))
                .sorted(Comparator.comparing(Traitement::id)).toList();
    }
}
