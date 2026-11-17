package tn.ginf.smarthealthcare.soap.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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

    private final Set<String> patients;
    private final Map<String, CouvertureSeed> couvertures;

    public SeedRepository(ObjectMapper mapper) throws IOException {
        this.patients = lire(mapper, "seed/patients.json", new TypeReference<List<PatientSeed>>() {})
                .stream().map(PatientSeed::id).collect(Collectors.toUnmodifiableSet());
        this.couvertures = lire(mapper, "seed/couvertures-cnam.json", new TypeReference<List<CouvertureSeed>>() {})
                .stream().collect(Collectors.toUnmodifiableMap(CouvertureSeed::patientId, Function.identity()));
        log.info("Seed chargé : {} patients, {} couvertures CNAM", patients.size(), couvertures.size());
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
}
