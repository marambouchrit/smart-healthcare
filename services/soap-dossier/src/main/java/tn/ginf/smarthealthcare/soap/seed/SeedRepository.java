package tn.ginf.smarthealthcare.soap.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Données fictives chargées depuis contracts/seed (copiées dans le classpath sous seed/ par le pom).
 * Lecture seule, en mémoire : la persistance PostgreSQL arrive en P2.SOAP.5.
 */
@Component
public class SeedRepository {

    private static final Logger log = LoggerFactory.getLogger(SeedRepository.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PatientSeed(String id) {}

    public record CouvertureSeed(String patientId, String codeVignette, String filiere,
                                 LocalDate dateDebut, LocalDate dateFin, int tauxRemboursement) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MedecinSeed(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LettreCleSeed(String code, BigDecimal valeurUnitaireTND) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraitementSeed(String id, String patientId, String medecinId, String medicament, String dosage,
                                 LocalDate dateDebut) {}

    private final Set<String> patients;
    private final Set<String> medecins;
    private final List<TraitementSeed> traitements;
    private final Map<String, CouvertureSeed> couvertures;
    private final Map<String, BigDecimal> valeursLettresCles;

    public SeedRepository(ObjectMapper mapper) throws IOException {
        this.patients = lire(mapper, "seed/patients.json", new TypeReference<List<PatientSeed>>() {})
                .stream().map(PatientSeed::id).collect(Collectors.toUnmodifiableSet());
        this.medecins = lire(mapper, "seed/medecins.json", new TypeReference<List<MedecinSeed>>() {})
                .stream().map(MedecinSeed::id).collect(Collectors.toUnmodifiableSet());
        this.couvertures = lire(mapper, "seed/couvertures-cnam.json", new TypeReference<List<CouvertureSeed>>() {})
                .stream().collect(Collectors.toUnmodifiableMap(CouvertureSeed::patientId, Function.identity()));
        this.valeursLettresCles = lire(mapper, "seed/lettres-cles.json", new TypeReference<List<LettreCleSeed>>() {})
                .stream().collect(Collectors.toUnmodifiableMap(LettreCleSeed::code, LettreCleSeed::valeurUnitaireTND));
        this.traitements = List.copyOf(lire(mapper, "seed/traitements.json", new TypeReference<List<TraitementSeed>>() {}));
        log.info("Seed chargé : {} patients, {} médecins, {} couvertures CNAM, {} lettres-clés, {} traitements",
                patients.size(), medecins.size(), couvertures.size(), valeursLettresCles.size(), traitements.size());
    }

    private static <T> T lire(ObjectMapper mapper, String chemin, TypeReference<T> type) throws IOException {
        try (InputStream in = new ClassPathResource(chemin).getInputStream()) {
            return mapper.readValue(in, type);
        }
    }

    public boolean patientExiste(String identifiantPatient) {
        return patients.contains(identifiantPatient);
    }

    public Optional<CouvertureSeed> couverture(String identifiantPatient) {
        return Optional.ofNullable(couvertures.get(identifiantPatient));
    }

    public List<TraitementSeed> traitements(String patientId) {
        return traitements.stream().filter(t -> t.patientId().equals(patientId)).toList();
    }

    public boolean medecinExiste(String identifiantMedecin) {
        return medecins.contains(identifiantMedecin);
    }

    /** Valeur unitaire en dinars d'une lettre-clé (contracts/seed/lettres-cles.json). */
    public Optional<BigDecimal> valeurLettreCle(String code) {
        return Optional.ofNullable(valeursLettresCles.get(code));
    }
}
