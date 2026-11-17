package tn.ginf.smarthealthcare.rest.urgence;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.ginf.smarthealthcare.rest.creneau.CreneauService;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository;
import tn.ginf.smarthealthcare.rest.web.Problemes.AucunCreneauException;
import tn.ginf.smarthealthcare.rest.web.Problemes.NonTrouveException;

/**
 * POST /api/urgences (contrat : operationId reserverCreneauUrgence).
 * Réserve le premier créneau libre des URGENCES aujourd'hui ; 409 « aucun-creneau » sinon (l'orchestrateur
 * déclenchera alors une ambulance, D19).
 */
@RestController
@RequestMapping("/api/urgences")
public class UrgenceController {

    private static final Logger log = LoggerFactory.getLogger(UrgenceController.class);
    private static final String SERVICE_URGENCES = "URGENCES";

    private final SeedRepository seed;
    private final CreneauService creneaux;
    private final Clock horloge;
    // Stockage en mémoire pour la phase 1 ; le CRUD complet des rendez-vous arrive en P2.REST.2
    private final Map<String, RendezVous> rendezVous = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public UrgenceController(SeedRepository seed, CreneauService creneaux, Clock horloge) {
        this.seed = seed;
        this.creneaux = creneaux;
        this.horloge = horloge;
    }

    @PostMapping
    public ResponseEntity<RendezVous> reserverCreneauUrgence(@Valid @RequestBody DemandeUrgence demande) {
        log.info("POST /api/urgences patient={} alerte={} niveau={}",
                demande.patientId(), demande.identifiantAlerte(), demande.niveau());
        seed.patient(demande.patientId())
                .orElseThrow(() -> new NonTrouveException("Aucun patient " + demande.patientId() + "."));

        var creneau = creneaux.reserverPremierLibreAujourdhui(SERVICE_URGENCES)
                .orElseThrow(() -> new AucunCreneauException("Aucun créneau libre aux urgences aujourd'hui."));

        String id = "RDV-%06d".formatted(sequence.incrementAndGet());
        String motif = demande.motif() != null ? demande.motif() : "Urgence — alerte " + demande.identifiantAlerte();
        RendezVous rdv = new RendezVous(id, demande.patientId(), creneau, motif,
                RendezVous.Statut.CONFIRME, true, OffsetDateTime.now(horloge).withNano(0));
        rendezVous.put(id, rdv);
        log.info("Urgence patient={} -> rendez-vous {} créneau {} à {}", demande.patientId(), id, creneau.id(), creneau.debut());
        return ResponseEntity.created(URI.create("/api/rendez-vous/" + id)).body(rdv);
    }
}
