package tn.ginf.smarthealthcare.graphql.seed;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Alerte;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Constante;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Couverture;
import tn.ginf.smarthealthcare.graphql.modele.Modele.TypeMesure;
import tn.ginf.smarthealthcare.graphql.modele.Modele.FicheUrgence;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Medecin;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Patient;
import tn.ginf.smarthealthcare.graphql.modele.Modele.ServiceHospitalier;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Traitement;

/**
 * Vue consolidée propre au tableau de bord (D5) : données initiales de contracts/seed, puis alimentée
 * par l'orchestrateur via les mutations. En mémoire pour la phase 1 (PostgreSQL en P2.GQL.4).
 */
@Component
public class VueConsolidee {

    private static final Logger log = LoggerFactory.getLogger(VueConsolidee.class);

    private final Map<String, Patient> patients;
    private final Map<String, Medecin> medecins;
    private final Map<String, ServiceHospitalier> services;
    private final List<Traitement> traitements;
    private final Map<String, Alerte> alertes = new ConcurrentHashMap<>();
    private final List<FicheUrgence> fiches = new CopyOnWriteArrayList<>();
    /** Dernière valeur connue de chaque type de mesure, par patient (alimenté par enregistrerConstantes, D18). */
    private final Map<String, Map<TypeMesure, Constante>> constantes = new ConcurrentHashMap<>();
    /** Dernière couverture CNAM transmise par l'orchestrateur, par patient. */
    private final Map<String, Couverture> couvertures = new ConcurrentHashMap<>();

    public VueConsolidee(ObjectMapper mapper) throws IOException {
        ObjectMapper m = mapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        patients = parId(lire(m, "seed/patients.json", Patient.class), Patient::id);
        medecins = parId(lire(m, "seed/medecins.json", Medecin.class), Medecin::id);
        services = parId(lire(m, "seed/services-hospitaliers.json", ServiceHospitalier.class), ServiceHospitalier::code);
        traitements = List.copyOf(lire(m, "seed/traitements.json", Traitement.class));
        log.info("Vue consolidée initialisée : {} patients, {} médecins, {} services, {} traitements",
                patients.size(), medecins.size(), services.size(), traitements.size());
    }

    private static <T> List<T> lire(ObjectMapper m, String chemin, Class<T> type) throws IOException {
        CollectionType liste = m.getTypeFactory().constructCollectionType(List.class, type);
        try (InputStream in = new ClassPathResource(chemin).getInputStream()) {
            return m.readValue(in, liste);
        }
    }

    private static <T> Map<String, T> parId(List<T> l, Function<T, String> id) {
        return l.stream().collect(Collectors.toUnmodifiableMap(id, Function.identity()));
    }

    public Optional<Patient> patient(String id) { return Optional.ofNullable(patients.get(id)); }
    public Optional<Medecin> medecin(String id) { return Optional.ofNullable(medecins.get(id)); }
    public Optional<ServiceHospitalier> service(String code) { return Optional.ofNullable(services.get(code)); }
    public Optional<Alerte> alerte(String id) { return Optional.ofNullable(alertes.get(id)); }

    public List<Patient> patients() { return List.copyOf(patients.values()); }

    /** Services triés par libellé (listes déroulantes du client). */
    public List<ServiceHospitalier> services() {
        return services.values().stream().sorted(Comparator.comparing(ServiceHospitalier::libelle)).toList();
    }

    public List<Traitement> traitements(String patientId) {
        return traitements.stream().filter(t -> t.patientId().equals(patientId)).toList();
    }

    public List<Alerte> alertesRecentes(String patientId, int limite) {
        return alertes.values().stream().filter(a -> a.patientId().equals(patientId))
                .sorted(Comparator.comparing(Alerte::horodatage).reversed()).limit(limite).toList();
    }

    public List<FicheUrgence> fiches(String patientId, int limite) {
        List<FicheUrgence> l = new ArrayList<>(fiches.stream().filter(f -> f.patientId().equals(patientId)).toList());
        l.sort(Comparator.comparing(FicheUrgence::creeeLe).reversed());
        return l.stream().limit(limite).toList();
    }

    public void enregistrer(Alerte a) { alertes.put(a.id(), a); }

    public int nombreFiches() { return fiches.size(); }

    public void enregistrer(FicheUrgence f) { fiches.add(f); }

    /** Garde, pour chaque type, la mesure la plus récente (une mesure plus ancienne arrivée en retard est ignorée). */
    public void enregistrer(Constante c) {
        constantes.computeIfAbsent(c.patientId(), k -> new ConcurrentHashMap<>())
                .merge(c.type(), c, (ancienne, nouvelle) -> nouvelle.horodatage().isBefore(ancienne.horodatage()) ? ancienne : nouvelle);
    }

    public List<Constante> dernieresConstantes(String patientId) {
        return constantes.getOrDefault(patientId, Map.of()).values().stream()
                .sorted(Comparator.comparing(Constante::type)).toList();
    }

    public void enregistrer(Couverture c) { couvertures.put(c.patientId(), c); }

    public Optional<Couverture> couverture(String patientId) { return Optional.ofNullable(couvertures.get(patientId)); }
}
