package tn.ginf.smarthealthcare.orchestrator.evenements;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.PriseEnCharge;

/**
 * Diffusion SSE des alertes et des résultats de workflow à tous les clients abonnés à /events
 * (P3.5, D6, D45 ; format : contracts/sse/README.md).
 */
@Component
public class DiffusionSse implements Ports.Diffusion {

    private static final Logger log = LoggerFactory.getLogger(DiffusionSse.class);

    /** Durée maximale d'une connexion ; EventSource se reconnecte seul ensuite. */
    static final Duration DUREE_CONNEXION = Duration.ofMinutes(30);

    /** Données des événements : dates au format ISO 8601 avec décalage, à la seconde (contracts/sse). */
    record EvenementAlerte(String id, String patientId, String niveau, String type, double valeur, double seuil,
                           String message, String horodatage) {}

    record EvenementPriseEnCharge(String alerteId, String patientId, String etat, boolean aRegulariser,
                                  String rendezVousId, String debutRendezVous, String ficheId, String horodatage) {}

    private final List<SseEmitter> abonnes = new CopyOnWriteArrayList<>();
    private final ObjectMapper json;

    public DiffusionSse(ObjectMapper json) {
        this.json = json;
    }

    /** Nouvel abonné ; le commentaire « connecte » envoie tout de suite les en-têtes de la réponse. */
    public SseEmitter abonner() {
        SseEmitter emetteur = new SseEmitter(DUREE_CONNEXION.toMillis());
        emetteur.onCompletion(() -> abonnes.remove(emetteur));
        emetteur.onTimeout(() -> abonnes.remove(emetteur));
        emetteur.onError(e -> abonnes.remove(emetteur));
        abonnes.add(emetteur);
        envoyer(emetteur, SseEmitter.event().comment("connecte"));
        log.info("Nouvel abonné au flux SSE ({} abonné(s))", abonnes.size());
        return emetteur;
    }

    int nombreAbonnes() {
        return abonnes.size();
    }

    @Override
    public void alerte(AlerteRecue a) {
        diffuser("alerte", a.id(), new EvenementAlerte(a.id(), a.patientId(), a.niveau(), a.type(), a.valeur(),
                a.seuil(), a.message(), date(a.horodatage())));
    }

    @Override
    public void priseEnCharge(PriseEnCharge p) {
        diffuser("prise-en-charge", p.alerteId(), new EvenementPriseEnCharge(p.alerteId(), p.patientId(), p.etat(),
                p.aRegulariser(), p.rendezVousId(), date(p.debutRendezVous()), p.ficheId(), date(p.horodatage())));
    }

    /** Commentaire périodique : garde la connexion ouverte à travers la gateway et détecte les clients partis. */
    @Scheduled(fixedRate = 20_000, initialDelay = 20_000)
    public void battement() {
        abonnes.forEach(e -> envoyer(e, SseEmitter.event().comment("ping")));
    }

    private void diffuser(String nom, String id, Object donnees) {
        String contenu;
        try {
            contenu = json.writeValueAsString(donnees);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Événement SSE non sérialisable", e);
        }
        abonnes.forEach(e -> envoyer(e, SseEmitter.event().name(nom).id(id).data(contenu)));
        log.info("[{}] Événement SSE « {} » diffusé à {} abonné(s)", id, nom, abonnes.size());
    }

    /** Envois sérialisés par abonné (plusieurs workflows et le battement peuvent écrire en même temps). */
    private void envoyer(SseEmitter emetteur, SseEmitter.SseEventBuilder evenement) {
        synchronized (emetteur) {
            try {
                emetteur.send(evenement);
            } catch (IOException | IllegalStateException e) {
                // client parti ou connexion déjà terminée
                abonnes.remove(emetteur);
            }
        }
    }

    private static String date(OffsetDateTime d) {
        return d == null ? null : d.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
