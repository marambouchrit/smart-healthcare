package tn.ginf.smarthealthcare.rest.urgence;

import jakarta.validation.Valid;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.ginf.smarthealthcare.rest.rendezvous.RendezVous;
import tn.ginf.smarthealthcare.rest.rendezvous.RendezVousService;

/**
 * POST /api/urgences (contrat : operationId reserverCreneauUrgence).
 * Réserve le premier créneau libre des URGENCES aujourd'hui ; 409 « aucun-creneau » sinon (l'orchestrateur
 * déclenchera alors une ambulance, D19). Le rendez-vous créé est ensuite géré par le CRUD /api/rendez-vous.
 */
@RestController
@RequestMapping("/api/urgences")
public class UrgenceController {

    private static final Logger log = LoggerFactory.getLogger(UrgenceController.class);
    private static final String SERVICE_URGENCES = "URGENCES";

    private final RendezVousService rendezVous;

    public UrgenceController(RendezVousService rendezVous) {
        this.rendezVous = rendezVous;
    }

    @PostMapping
    public ResponseEntity<RendezVous> reserverCreneauUrgence(@Valid @RequestBody DemandeUrgence demande) {
        log.info("POST /api/urgences patient={} alerte={} niveau={}",
                demande.patientId(), demande.identifiantAlerte(), demande.niveau());
        String motif = demande.motif() != null ? demande.motif() : "Urgence — alerte " + demande.identifiantAlerte();
        RendezVous rdv = rendezVous.creerUrgence(demande.patientId(), SERVICE_URGENCES, motif);
        return ResponseEntity.created(URI.create("/api/rendez-vous/" + rdv.id())).body(rdv);
    }
}
