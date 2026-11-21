package tn.ginf.smarthealthcare.rest.rendezvous;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.ginf.smarthealthcare.rest.creneau.Creneau;
import tn.ginf.smarthealthcare.rest.creneau.CreneauService;
import tn.ginf.smarthealthcare.rest.persistance.Depots;
import tn.ginf.smarthealthcare.rest.persistance.Entites.RendezVousEntite;
import tn.ginf.smarthealthcare.rest.rendezvous.RendezVous.Statut;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository;
import tn.ginf.smarthealthcare.rest.web.Problemes.AucunCreneauException;
import tn.ginf.smarthealthcare.rest.web.Problemes.ConflitException;
import tn.ginf.smarthealthcare.rest.web.Problemes.NonTrouveException;
import tn.ginf.smarthealthcare.rest.web.Problemes.RequeteInvalideException;

/**
 * Rendez-vous (P2.REST.2, D28) enregistrés dans PostgreSQL (P2.REST.7, D38).
 * Un créneau ne porte qu'un rendez-vous non annulé : vérifié ici et garanti par un index unique partiel en base ;
 * annuler ou supprimer un rendez-vous rend donc son créneau à nouveau disponible.
 */
@Service
public class RendezVousService {

    private static final Logger log = LoggerFactory.getLogger(RendezVousService.class);

    private final SeedRepository seed;
    private final CreneauService creneaux;
    private final Depots.RendezVous depot;
    private final Clock horloge;

    public RendezVousService(SeedRepository seed, CreneauService creneaux, Depots.RendezVous depot, Clock horloge) {
        this.seed = seed;
        this.creneaux = creneaux;
        this.depot = depot;
        this.horloge = horloge;
    }

    @Transactional(readOnly = true)
    public List<RendezVous> lister(String patientId, String medecinId, LocalDate date) {
        return depot.findAllByOrderByIdAsc().stream().map(this::versRecord)
                .filter(r -> patientId == null || r.patientId().equals(patientId))
                .filter(r -> medecinId == null || r.creneau().medecinId().equals(medecinId))
                .filter(r -> date == null || r.creneau().debut().toLocalDate().equals(date))
                .sorted(Comparator.comparing((RendezVous r) -> r.creneau().debut()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RendezVous obtenir(String id) {
        return versRecord(entite(id));
    }

    @Transactional
    public synchronized RendezVous creer(String patientId, String creneauId, String motif) {
        exigerPatient(patientId);
        exigerReservable(creneauId);
        return enregistrer(patientId, creneauId, motif, false);
    }

    /** Réservation d'urgence : premier créneau libre du service aujourd'hui (POST /api/urgences). */
    @Transactional
    public synchronized RendezVous creerUrgence(String patientId, String serviceCode, String motif) {
        exigerPatient(patientId);
        Creneau c = creneaux.premierLibreAujourdhui(serviceCode)
                .orElseThrow(() -> new AucunCreneauException("Aucun créneau libre aux urgences aujourd'hui."));
        return enregistrer(patientId, c.id(), motif, true);
    }

    /** PUT : remplacement complet ; changer de créneau libère l'ancien. Le patient ne change pas. */
    @Transactional
    public synchronized RendezVous remplacer(String id, String patientId, String creneauId, String motif) {
        RendezVousEntite e = entite(id);
        exigerModifiable(e);
        if (!e.patientId.equals(patientId)) {
            throw new RequeteInvalideException("Un rendez-vous ne change pas de patient (" + e.patientId + ").");
        }
        Long nouveau = CreneauService.numero(creneauId);
        if (!e.creneauId.equals(nouveau)) {
            exigerReservable(creneauId);
            log.info("Rendez-vous {} : créneau {} -> {}", id, CreneauService.identifiant(e.creneauId), creneauId);
            e.creneauId = nouveau;
        }
        e.motif = motif;
        return versRecord(depot.saveAndFlush(e));
    }

    /** PATCH : motif et/ou statut ; transitions CONFIRME → ANNULE | HONORE. */
    @Transactional
    public synchronized RendezVous modifier(String id, String motif, Statut statut) {
        if (motif == null && statut == null) {
            throw new RequeteInvalideException("Indiquer au moins un champ à modifier (motif, statut).");
        }
        RendezVousEntite e = entite(id);
        exigerModifiable(e);
        if (Statut.valueOf(e.statut) == Statut.HONORE && statut != null && statut != Statut.HONORE) {
            throw new ConflitException("transition-interdite", "Transition interdite", "Le rendez-vous " + id + " a déjà été honoré.");
        }
        if (statut != null) {
            log.info("Rendez-vous {} : statut {} -> {}", id, e.statut, statut);
            e.statut = statut.name();
        }
        if (motif != null) {
            e.motif = motif;
        }
        return versRecord(depot.saveAndFlush(e));
    }

    /** DELETE : supprime le rendez-vous (son créneau redevient disponible). */
    @Transactional
    public synchronized void supprimer(String id) {
        depot.delete(entite(id));
        log.info("Rendez-vous {} supprimé", id);
    }

    private RendezVous enregistrer(String patientId, String creneauId, String motif, boolean urgence) {
        try {
            RendezVousEntite e = depot.saveAndFlush(new RendezVousEntite(patientId, CreneauService.numero(creneauId), motif,
                    Statut.CONFIRME.name(), urgence, OffsetDateTime.now(horloge).withNano(0)));
            RendezVous r = versRecord(e);
            log.info("Rendez-vous {} créé : patient={} créneau {} ({}) à {}{}", r.id(), patientId, creneauId,
                    r.creneau().medecinId(), r.creneau().debut(), urgence ? " [urgence]" : "");
            return r;
        } catch (DataIntegrityViolationException ex) {
            // double réservation simultanée : l'index unique partiel de la base a refusé la seconde
            throw creneauIndisponible(creneauId);
        }
    }

    private RendezVous versRecord(RendezVousEntite e) {
        Set<Long> reserves = depot.creneauxReserves();
        Creneau c = creneaux.trouver(CreneauService.identifiant(e.creneauId)).orElseThrow();
        return new RendezVous("RDV-%06d".formatted(e.id), e.patientId,
                new Creneau(c.id(), c.medecinId(), c.serviceCode(), c.debut(), c.fin(), !reserves.contains(e.creneauId)),
                e.motif, Statut.valueOf(e.statut), e.urgence,
                e.creeLe.atZoneSameInstant(horloge.getZone()).toOffsetDateTime());
    }

    private RendezVousEntite entite(String id) {
        Long n = id != null && id.matches("RDV-\\d{6}") ? Long.valueOf(id.substring(4)) : null;
        return (n == null ? java.util.Optional.<RendezVousEntite>empty() : depot.findById(n))
                .orElseThrow(() -> new NonTrouveException("Aucun rendez-vous " + id + "."));
    }

    private void exigerPatient(String patientId) {
        seed.patient(patientId).orElseThrow(() -> new NonTrouveException("Aucun patient " + patientId + "."));
    }

    private void exigerReservable(String creneauId) {
        creneaux.trouver(creneauId).orElseThrow(() -> new NonTrouveException("Aucun créneau " + creneauId + "."));
        if (!creneaux.reservable(creneauId)) {
            throw creneauIndisponible(creneauId);
        }
    }

    private static ConflitException creneauIndisponible(String creneauId) {
        return new ConflitException("creneau-indisponible", "Créneau indisponible",
                "Le créneau " + creneauId + " est déjà réservé ou déjà commencé.");
    }

    private static void exigerModifiable(RendezVousEntite e) {
        if (Statut.valueOf(e.statut) == Statut.ANNULE) {
            throw new ConflitException("rendez-vous-annule", "Rendez-vous annulé",
                    "Le rendez-vous RDV-%06d est annulé et ne peut plus être modifié.".formatted(e.id));
        }
    }
}
