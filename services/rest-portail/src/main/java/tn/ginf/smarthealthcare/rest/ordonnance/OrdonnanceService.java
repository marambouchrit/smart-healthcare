package tn.ginf.smarthealthcare.rest.ordonnance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Traitement;
import tn.ginf.smarthealthcare.rest.web.Problemes.NonTrouveException;

/**
 * Ordonnances (P2.REST.3, D19) : traitements prescrits le même jour par le même médecin à un même patient.
 * Identifiants ORD-xxxxxx attribués une fois, dans l'ordre (date, médecin, patient), donc stables.
 */
@Service
public class OrdonnanceService {

    public record LignePrescription(String traitementId, String medicament, String dosage, String posologie,
                                    LocalDate dateDebut, LocalDate dateFin, String statut) {}

    public record Ordonnance(String id, String patientId, String medecinId, LocalDate date, List<LignePrescription> lignes) {}

    private final SeedRepository seed;
    private final List<Ordonnance> ordonnances;

    public OrdonnanceService(SeedRepository seed) {
        this.seed = seed;
        Map<String, List<Traitement>> groupes = seed.traitements().stream()
                .sorted(Comparator.comparing(Traitement::dateDebut).thenComparing(Traitement::medecinId)
                        .thenComparing(Traitement::patientId).thenComparing(Traitement::id))
                .collect(Collectors.groupingBy(t -> t.dateDebut() + "|" + t.medecinId() + "|" + t.patientId(),
                        LinkedHashMap::new, Collectors.toList()));
        List<Ordonnance> liste = new ArrayList<>();
        int n = 1;
        for (List<Traitement> g : groupes.values()) {
            Traitement t0 = g.get(0);
            liste.add(new Ordonnance("ORD-%06d".formatted(n++), t0.patientId(), t0.medecinId(), t0.dateDebut(),
                    g.stream().map(t -> new LignePrescription(t.id(), t.medicament(), t.dosage(), t.posologie(),
                            t.dateDebut(), t.dateFin(), t.statut())).toList()));
        }
        this.ordonnances = List.copyOf(liste);
    }

    /** Ordonnances d'un patient, plus récentes d'abord ; statut : garder celles qui ont au moins une ligne de ce statut. */
    public List<Ordonnance> duPatient(String patientId, String statut) {
        seed.patient(patientId).orElseThrow(() -> new NonTrouveException("Aucun patient " + patientId + "."));
        return ordonnances.stream()
                .filter(o -> o.patientId().equals(patientId))
                .filter(o -> statut == null || o.lignes().stream().anyMatch(l -> l.statut().equals(statut)))
                .sorted(Comparator.comparing(Ordonnance::date).reversed())
                .toList();
    }
}
