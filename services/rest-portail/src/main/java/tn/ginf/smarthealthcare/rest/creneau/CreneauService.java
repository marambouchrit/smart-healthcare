package tn.ginf.smarthealthcare.rest.creneau;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.ginf.smarthealthcare.rest.persistance.Depots;
import tn.ginf.smarthealthcare.rest.persistance.Entites.CreneauEntite;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Planning;

/**
 * Créneaux (D20, D22, D38) : générés au démarrage depuis contracts/seed/plannings.json sur un horizon de N jours et
 * enregistrés en base (seulement ceux qui manquent : un redémarrage garde les identifiants CRN-xxxxxx).
 * Un créneau est indisponible s'il porte un rendez-vous non annulé (pas d'indicateur stocké).
 */
@Service
public class CreneauService {

    private static final Logger log = LoggerFactory.getLogger(CreneauService.class);
    private static final List<String> JOURS =
            List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE");

    private final Clock horloge;
    private final SeedRepository seed;
    private final Depots.Creneaux creneaux;
    private final Depots.RendezVous rendezVous;

    public CreneauService(Clock horloge, SeedRepository seed, Depots.Creneaux creneaux, Depots.RendezVous rendezVous,
                          @Value("${portail.horizon-jours}") int horizonJours) {
        this.horloge = horloge;
        this.seed = seed;
        this.creneaux = creneaux;
        this.rendezVous = rendezVous;
        generer(LocalDate.now(horloge), horizonJours);
    }

    private void generer(LocalDate debut, int horizonJours) {
        ZoneId zone = horloge.getZone();
        List<CreneauEntite> liste = new ArrayList<>();
        for (int j = 0; j < horizonJours; j++) {
            LocalDate jour = debut.plusDays(j);
            for (Planning p : seed.plannings()) {
                if (!p.jours().contains(JOURS.get(jour.getDayOfWeek().getValue() - 1))) {
                    continue;
                }
                String service = seed.medecin(p.medecinId()).orElseThrow().serviceCode();
                for (LocalTime h = p.debut(); !h.plusMinutes(p.dureeMinutes()).isAfter(p.fin()); h = h.plusMinutes(p.dureeMinutes())) {
                    OffsetDateTime d = ZonedDateTime.of(jour, h, zone).toOffsetDateTime();
                    liste.add(new CreneauEntite(p.medecinId(), service, d, d.plusMinutes(p.dureeMinutes())));
                }
            }
        }
        liste.sort(Comparator.comparing((CreneauEntite c) -> c.debut).thenComparing(c -> c.medecinId));
        List<CreneauEntite> nouveaux = liste.stream().filter(c -> !creneaux.existsByMedecinIdAndDebut(c.medecinId, c.debut)).toList();
        creneaux.saveAll(nouveaux);
        log.info("Créneaux du {} sur {} jours : {} ajoutés, {} en base", debut, horizonJours, nouveaux.size(), creneaux.count());
    }

    Creneau versRecord(CreneauEntite e, Set<Long> reserves) {
        return new Creneau(identifiant(e.id), e.medecinId, e.serviceCode, local(e.debut), local(e.fin), !reserves.contains(e.id));
    }

    private OffsetDateTime local(OffsetDateTime d) {
        return d.atZoneSameInstant(horloge.getZone()).toOffsetDateTime();
    }

    public static String identifiant(Long id) {
        return "CRN-%06d".formatted(id);
    }

    /** CRN-000042 → 42 ; null si le format est inattendu. */
    public static Long numero(String identifiant) {
        return identifiant != null && identifiant.matches("CRN-\\d{6}") ? Long.valueOf(identifiant.substring(4)) : null;
    }

    /**
     * Créneaux filtrés (P2.REST.1). disponible = true : libres et pas encore commencés ;
     * disponible = false : déjà réservés. Filtres optionnels médecin, service, jour (heure locale).
     */
    @Transactional(readOnly = true)
    public List<Creneau> lister(String medecinId, String serviceCode, LocalDate date, boolean disponible) {
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);
        Set<Long> reserves = rendezVous.creneauxReserves();
        List<CreneauEntite> source = date == null ? creneaux.findAllByOrderByDebutAscMedecinIdAsc()
                : creneaux.findByDebutGreaterThanEqualAndDebutLessThanOrderByDebutAscMedecinIdAsc(
                        date.atStartOfDay(horloge.getZone()).toOffsetDateTime(),
                        date.plusDays(1).atStartOfDay(horloge.getZone()).toOffsetDateTime());
        return source.stream()
                .filter(c -> medecinId == null || c.medecinId.equals(medecinId))
                .filter(c -> serviceCode == null || c.serviceCode.equals(serviceCode))
                .filter(c -> disponible ? !reserves.contains(c.id) && c.debut.isAfter(maintenant) : reserves.contains(c.id))
                .map(c -> versRecord(c, reserves))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Creneau> trouver(String id) {
        Long n = numero(id);
        return n == null ? Optional.empty() : creneaux.findById(n).map(c -> versRecord(c, rendezVous.creneauxReserves()));
    }

    /** Vrai si le créneau existe, n'a pas commencé et ne porte aucun rendez-vous non annulé. */
    @Transactional(readOnly = true)
    public boolean reservable(String id) {
        Long n = numero(id);
        return n != null && creneaux.findById(n)
                .filter(c -> c.debut.isAfter(OffsetDateTime.now(horloge)))
                .filter(c -> !rendezVous.existsByCreneauIdAndStatutNot(c.id, "ANNULE"))
                .isPresent();
    }

    /** Premier créneau libre du service aujourd'hui qui n'a pas encore commencé (P1.2, D22). */
    @Transactional(readOnly = true)
    public Optional<Creneau> premierLibreAujourdhui(String serviceCode) {
        return lister(null, serviceCode, LocalDate.now(horloge), true).stream().findFirst();
    }
}
