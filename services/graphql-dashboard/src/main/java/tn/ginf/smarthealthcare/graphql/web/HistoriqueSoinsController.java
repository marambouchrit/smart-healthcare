package tn.ginf.smarthealthcare.graphql.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import tn.ginf.smarthealthcare.graphql.modele.Modele.ActionUrgence;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Alerte;
import tn.ginf.smarthealthcare.graphql.modele.Modele.FicheUrgence;
import tn.ginf.smarthealthcare.graphql.modele.Modele.NiveauAlerte;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Patient;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Traitement;
import tn.ginf.smarthealthcare.graphql.seed.VueConsolidee;

/**
 * P2.GQL.3 : Patient.historiqueSoins(depuis, jusqua), agrégé à la demande (D31).
 * Le champ renvoie seulement la période ; chaque agrégat a son propre résolveur et n'est donc calculé
 * (et transmis) que si le client le demande.
 */
@Controller
public class HistoriqueSoinsController {

    private static final Logger log = LoggerFactory.getLogger(HistoriqueSoinsController.class);

    /** Valeur intermédiaire : la période demandée pour un patient. */
    public record HistoriqueSoins(String patientId, LocalDate depuis, LocalDate jusqua) {
        boolean contient(LocalDate d) {
            return (depuis == null || !d.isBefore(depuis)) && (jusqua == null || !d.isAfter(jusqua));
        }
    }

    public record CompteParNiveau(NiveauAlerte niveau, int nombre) {}

    private final VueConsolidee vue;
    private final Clock horloge;

    public HistoriqueSoinsController(VueConsolidee vue, Clock horloge) {
        this.vue = vue;
        this.horloge = horloge;
    }

    @SchemaMapping(typeName = "Patient", field = "historiqueSoins")
    public HistoriqueSoins historiqueSoins(Patient p, @Argument LocalDate depuis, @Argument LocalDate jusqua) {
        if (depuis != null && jusqua != null && depuis.isAfter(jusqua)) {
            throw new Erreurs.Invalide("Période invalide : " + depuis + " est après " + jusqua);
        }
        return new HistoriqueSoins(p.id(), depuis, jusqua);
    }

    @SchemaMapping(typeName = "HistoriqueSoins")
    public int nombreAlertes(HistoriqueSoins h) {
        log.debug("calcul nombreAlertes {}", h.patientId());
        return alertes(h).size();
    }

    @SchemaMapping(typeName = "HistoriqueSoins")
    public List<CompteParNiveau> alertesParNiveau(HistoriqueSoins h) {
        log.debug("calcul alertesParNiveau {}", h.patientId());
        List<Alerte> a = alertes(h);
        return Arrays.stream(NiveauAlerte.values())
                .map(n -> new CompteParNiveau(n, (int) a.stream().filter(x -> x.niveau() == n).count()))
                .toList();
    }

    @SchemaMapping(typeName = "HistoriqueSoins")
    public int nombreFichesUrgence(HistoriqueSoins h) {
        return fiches(h).size();
    }

    @SchemaMapping(typeName = "HistoriqueSoins")
    public int nombreAmbulances(HistoriqueSoins h) {
        return (int) fiches(h).stream().filter(f -> f.action() == ActionUrgence.AMBULANCE).count();
    }

    /** Traitements actifs pendant la période (commencés avant sa fin, non terminés avant son début). */
    @SchemaMapping(typeName = "HistoriqueSoins")
    public List<Traitement> traitements(HistoriqueSoins h) {
        return vue.traitements(h.patientId()).stream()
                .filter(t -> h.jusqua() == null || !t.dateDebut().isAfter(h.jusqua()))
                .filter(t -> h.depuis() == null || t.dateFin() == null || !t.dateFin().isBefore(h.depuis()))
                .toList();
    }

    private List<Alerte> alertes(HistoriqueSoins h) {
        return vue.alertesRecentes(h.patientId(), Integer.MAX_VALUE).stream()
                .filter(a -> h.contient(jourLocal(a.horodatage()))).toList();
    }

    private List<FicheUrgence> fiches(HistoriqueSoins h) {
        return vue.fiches(h.patientId(), Integer.MAX_VALUE).stream()
                .filter(f -> h.contient(jourLocal(f.creeeLe()))).toList();
    }

    private LocalDate jourLocal(OffsetDateTime d) {
        return d.atZoneSameInstant(horloge.getZone()).toLocalDate();
    }
}
