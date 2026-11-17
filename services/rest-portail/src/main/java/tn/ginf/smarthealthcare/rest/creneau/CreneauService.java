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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Planning;

/**
 * Génère les créneaux à partir de contracts/seed/plannings.json (D20) sur un horizon de N jours,
 * et gère leur réservation. En mémoire pour la phase 1.
 */
@Service
public class CreneauService {

    private static final Logger log = LoggerFactory.getLogger(CreneauService.class);
    private static final List<String> JOURS =
            List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE");

    private final Clock horloge;
    private final SeedRepository seed;
    private final Map<String, Creneau> creneaux = new LinkedHashMap<>();

    public CreneauService(Clock horloge, SeedRepository seed, @Value("${portail.horizon-jours}") int horizonJours) {
        this.horloge = horloge;
        this.seed = seed;
        generer(LocalDate.now(horloge), horizonJours);
    }

    private void generer(LocalDate debut, int horizonJours) {
        ZoneId zone = horloge.getZone();
        List<Creneau> liste = new ArrayList<>();
        for (int j = 0; j < horizonJours; j++) {
            LocalDate jour = debut.plusDays(j);
            for (Planning p : seed.plannings()) {
                if (!p.jours().contains(nomJour(jour.getDayOfWeek()))) {
                    continue;
                }
                String service = seed.medecin(p.medecinId()).orElseThrow().serviceCode();
                for (LocalTime h = p.debut(); !h.plusMinutes(p.dureeMinutes()).isAfter(p.fin()); h = h.plusMinutes(p.dureeMinutes())) {
                    OffsetDateTime d = ZonedDateTime.of(jour, h, zone).toOffsetDateTime();
                    liste.add(new Creneau(null, p.medecinId(), service, d, d.plusMinutes(p.dureeMinutes()), true));
                }
            }
        }
        liste.sort(Comparator.comparing(Creneau::debut).thenComparing(Creneau::medecinId));
        int n = 1;
        for (Creneau c : liste) {
            String id = "CRN-%06d".formatted(n++);
            creneaux.put(id, new Creneau(id, c.medecinId(), c.serviceCode(), c.debut(), c.fin(), true));
        }
        log.info("{} créneaux générés du {} sur {} jours", creneaux.size(), debut, horizonJours);
    }

    private static String nomJour(DayOfWeek d) {
        return JOURS.get(d.getValue() - 1);
    }

    /** Réserve le premier créneau libre du service, aujourd'hui, qui n'a pas encore commencé. */
    public synchronized Optional<Creneau> reserverPremierLibreAujourdhui(String serviceCode) {
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);
        LocalDate aujourdhui = LocalDate.now(horloge);
        Optional<Creneau> libre = creneaux.values().stream()
                .filter(c -> c.disponible() && c.serviceCode().equals(serviceCode))
                .filter(c -> c.debut().isAfter(maintenant))
                .filter(c -> c.debut().atZoneSameInstant(horloge.getZone()).toLocalDate().equals(aujourdhui))
                .findFirst();
        libre.ifPresent(c -> creneaux.put(c.id(), c.reserve()));
        return libre.map(Creneau::reserve);
    }
}
