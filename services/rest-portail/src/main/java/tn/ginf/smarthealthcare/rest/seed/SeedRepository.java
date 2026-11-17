package tn.ginf.smarthealthcare.rest.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Données fictives chargées depuis contracts/seed (copiées sous seed/ dans le classpath par le pom).
 * En mémoire pour la phase 1.
 */
@Component
public class SeedRepository {

    private static final Logger log = LoggerFactory.getLogger(SeedRepository.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Adresse(String rue, String ville, String codePostal) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Patient(String id, String nom, String prenom, Adresse adresse) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Medecin(String id, String nom, String prenom, String serviceCode) {}

    public record Planning(String medecinId, List<String> jours, LocalTime debut, LocalTime fin, int dureeMinutes) {}

    private final Map<String, Patient> patients;
    private final Map<String, Medecin> medecins;
    private final List<Planning> plannings;

    public SeedRepository(ObjectMapper mapper) throws IOException {
        patients = parId(lire(mapper, "seed/patients.json", new TypeReference<List<Patient>>() {}), Patient::id);
        medecins = parId(lire(mapper, "seed/medecins.json", new TypeReference<List<Medecin>>() {}), Medecin::id);
        plannings = List.copyOf(lire(mapper, "seed/plannings.json", new TypeReference<List<Planning>>() {}));
        log.info("Seed chargé : {} patients, {} médecins, {} plannings", patients.size(), medecins.size(), plannings.size());
    }

    private static <T> T lire(ObjectMapper mapper, String chemin, TypeReference<T> type) throws IOException {
        try (InputStream in = new ClassPathResource(chemin).getInputStream()) {
            return mapper.readValue(in, type);
        }
    }

    private static <T> Map<String, T> parId(List<T> liste, Function<T, String> id) {
        return liste.stream().collect(Collectors.toUnmodifiableMap(id, Function.identity()));
    }

    public Optional<Patient> patient(String id) {
        return Optional.ofNullable(patients.get(id));
    }

    public Optional<Medecin> medecin(String id) {
        return Optional.ofNullable(medecins.get(id));
    }

    public List<Planning> plannings() {
        return plannings;
    }
}
