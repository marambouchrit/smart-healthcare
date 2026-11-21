package tn.ginf.smarthealthcare.rest.rendezvous;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.rest.creneau.Creneau;
import tn.ginf.smarthealthcare.rest.creneau.CreneauService;
import tn.ginf.smarthealthcare.rest.rendezvous.RendezVous.Statut;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository;
import tn.ginf.smarthealthcare.rest.web.Problemes.AucunCreneauException;
import tn.ginf.smarthealthcare.rest.web.Problemes.ConflitException;
import tn.ginf.smarthealthcare.rest.web.Problemes.NonTrouveException;
import tn.ginf.smarthealthcare.rest.web.Problemes.RequeteInvalideException;

/**
 * Rendez-vous (P2.REST.2) : création, lecture, remplacement, modification partielle, suppression.
 * Règles (D28) : un créneau ne porte qu'un rendez-vous confirmé ; annuler ou supprimer libère le créneau ;
 * un rendez-vous annulé ne se modifie plus ; transitions de statut CONFIRME → ANNULE | HONORE uniquement.
 * Stockage en mémoire (méthodes synchronisées : réservation atomique).
 */
@Service
public class RendezVousService {

    private static final Logger log = LoggerFactory.getLogger(RendezVousService.class);

    private final SeedRepository seed;
    private final CreneauService creneaux;
    private final Clock horloge;
    private final Map<String, RendezVous> stock = new LinkedHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public RendezVousService(SeedRepository seed, CreneauService creneaux, Clock horloge) {
        this.seed = seed;
        this.creneaux = creneaux;
        this.horloge = horloge;
    }

    public synchronized List<RendezVous> lister(String patientId, String medecinId, LocalDate date) {
        return stock.values().stream()
                .filter(r -> patientId == null || r.patientId().equals(patientId))
                .filter(r -> medecinId == null || r.creneau().medecinId().equals(medecinId))
                .filter(r -> date == null || r.creneau().debut().atZoneSameInstant(horloge.getZone()).toLocalDate().equals(date))
                .sorted(Comparator.comparing((RendezVous r) -> r.creneau().debut()))
                .toList();
    }

    public synchronized RendezVous obtenir(String id) {
        RendezVous r = stock.get(id);
        if (r == null) {
            throw new NonTrouveException("Aucun rendez-vous " + id + ".");
        }
        return r;
    }

    public synchronized RendezVous creer(String patientId, String creneauId, String motif) {
        exigerPatient(patientId);
        Creneau c = reserverOuConflit(creneauId);
        return enregistrer(nouveau(patientId, c, motif, false));
    }

    /** Réservation d'urgence : premier créneau libre du service aujourd'hui (utilisé par POST /api/urgences). */
    public synchronized RendezVous creerUrgence(String patientId, String serviceCode, String motif) {
        exigerPatient(patientId);
        Creneau c = creneaux.reserverPremierLibreAujourdhui(serviceCode)
                .orElseThrow(() -> new AucunCreneauException("Aucun créneau libre aux urgences aujourd'hui."));
        return enregistrer(nouveau(patientId, c, motif, true));
    }

    /** PUT : remplacement complet ; changer de créneau libère l'ancien. Le patient ne change pas. */
    public synchronized RendezVous remplacer(String id, String patientId, String creneauId, String motif) {
        RendezVous actuel = obtenir(id);
        exigerModifiable(actuel);
        if (!actuel.patientId().equals(patientId)) {
            throw new RequeteInvalideException("Un rendez-vous ne change pas de patient (" + actuel.patientId() + ").");
        }
        Creneau c = actuel.creneau();
        if (!c.id().equals(creneauId)) {
            c = reserverOuConflit(creneauId);
            creneaux.liberer(actuel.creneau().id());
        }
        RendezVous r = new RendezVous(id, patientId, c, motif, actuel.statut(), actuel.urgence(), actuel.creeLe());
        stock.put(id, r);
        log.info("Rendez-vous {} remplacé : créneau {} -> {}", id, actuel.creneau().id(), c.id());
        return r;
    }

    /** PATCH : motif et/ou statut. */
    public synchronized RendezVous modifier(String id, String motif, Statut statut) {
        if (motif == null && statut == null) {
            throw new RequeteInvalideException("Indiquer au moins un champ à modifier (motif, statut).");
        }
        RendezVous actuel = obtenir(id);
        exigerModifiable(actuel);
        Statut nouveau = statut == null ? actuel.statut() : statut;
        if (actuel.statut() == Statut.HONORE && nouveau != Statut.HONORE) {
            throw new ConflitException("transition-interdite", "Transition interdite",
                    "Le rendez-vous " + id + " a déjà été honoré.");
        }
        if (nouveau == Statut.ANNULE) {
            creneaux.liberer(actuel.creneau().id());
        }
        RendezVous r = new RendezVous(id, actuel.patientId(), nouveau == Statut.ANNULE ? libre(actuel.creneau()) : actuel.creneau(),
                motif == null ? actuel.motif() : motif, nouveau, actuel.urgence(), actuel.creeLe());
        stock.put(id, r);
        log.info("Rendez-vous {} modifié : statut {} -> {}", id, actuel.statut(), nouveau);
        return r;
    }

    /** DELETE : supprime le rendez-vous et libère son créneau s'il était confirmé. */
    public synchronized void supprimer(String id) {
        RendezVous r = obtenir(id);
        if (r.statut() == Statut.CONFIRME) {
            creneaux.liberer(r.creneau().id());
        }
        stock.remove(id);
        log.info("Rendez-vous {} supprimé", id);
    }

    private void exigerPatient(String patientId) {
        seed.patient(patientId).orElseThrow(() -> new NonTrouveException("Aucun patient " + patientId + "."));
    }

    private static void exigerModifiable(RendezVous r) {
        if (r.statut() == Statut.ANNULE) {
            throw new ConflitException("rendez-vous-annule", "Rendez-vous annulé",
                    "Le rendez-vous " + r.id() + " est annulé et ne peut plus être modifié.");
        }
    }

    private Creneau reserverOuConflit(String creneauId) {
        creneaux.trouver(creneauId).orElseThrow(() -> new NonTrouveException("Aucun créneau " + creneauId + "."));
        return creneaux.reserver(creneauId).orElseThrow(() -> new ConflitException("creneau-indisponible",
                "Créneau indisponible", "Le créneau " + creneauId + " est déjà réservé ou déjà commencé."));
    }

    private static Creneau libre(Creneau c) {
        return new Creneau(c.id(), c.medecinId(), c.serviceCode(), c.debut(), c.fin(), true);
    }

    private RendezVous nouveau(String patientId, Creneau c, String motif, boolean urgence) {
        return new RendezVous("RDV-%06d".formatted(sequence.incrementAndGet()), patientId, c, motif,
                Statut.CONFIRME, urgence, OffsetDateTime.now(horloge).withNano(0));
    }

    private RendezVous enregistrer(RendezVous r) {
        stock.put(r.id(), r);
        log.info("Rendez-vous {} créé : patient={} créneau {} ({}) à {}{}", r.id(), r.patientId(), r.creneau().id(),
                r.creneau().medecinId(), r.creneau().debut(), r.urgence() ? " [urgence]" : "");
        return r;
    }
}
