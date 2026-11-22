package tn.ginf.smarthealthcare.rest.urgence;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.ginf.smarthealthcare.rest.patient.ProfilService;
import tn.ginf.smarthealthcare.rest.persistance.Depots;
import tn.ginf.smarthealthcare.rest.persistance.Entites.InterventionAmbulanceEntite;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Adresse;

/**
 * Envoi simulé d'une ambulance au domicile du patient (P5.2, D53) : l'orchestrateur l'appelle quand aucun créneau
 * d'urgence n'est libre. Adresse = coordonnées actuelles du patient ; arrivée estimée = déclenchement + délai configuré.
 */
@Service
public class AmbulanceService {

    private static final Logger log = LoggerFactory.getLogger(AmbulanceService.class);

    private final ProfilService profils;
    private final Depots.InterventionsAmbulance depot;
    private final Clock horloge;
    private final Duration delai;

    public AmbulanceService(ProfilService profils, Depots.InterventionsAmbulance depot, Clock horloge,
                            @Value("${portail.ambulance.delai}") Duration delai) {
        this.profils = profils;
        this.depot = depot;
        this.horloge = horloge;
        this.delai = delai;
    }

    @Transactional
    public InterventionAmbulance envoyer(DemandeUrgence demande) {
        Adresse domicile = profils.obtenir(demande.patientId()).adresse();   // 404 si patient inconnu
        OffsetDateTime maintenant = OffsetDateTime.now(horloge).withNano(0);
        InterventionAmbulanceEntite e = depot.save(new InterventionAmbulanceEntite(demande.patientId(),
                demande.identifiantAlerte(), demande.niveau().name(), demande.motif(),
                Boolean.TRUE.equals(demande.aRegulariser()), domicile.rue(), domicile.ville(), domicile.codePostal(),
                maintenant, maintenant.plus(delai)));
        InterventionAmbulance intervention = enContrat(e);
        log.info("Ambulance {} envoyée pour {} (alerte {}) : {}, {} {}, arrivée estimée {}", intervention.id(),
                e.patientId, e.identifiantAlerte, e.rue, e.codePostal, e.ville, intervention.arriveeEstimee());
        return intervention;
    }

    private InterventionAmbulance enContrat(InterventionAmbulanceEntite e) {
        return new InterventionAmbulance("AMB-%06d".formatted(e.id), e.patientId, e.identifiantAlerte,
                new Adresse(e.rue, e.ville, e.codePostal),
                e.declencheeLe.atZoneSameInstant(horloge.getZone()).toOffsetDateTime(),
                e.arriveeEstimee.atZoneSameInstant(horloge.getZone()).toOffsetDateTime());
    }
}
