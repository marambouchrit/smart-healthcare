package tn.ginf.smarthealthcare.orchestrator.workflow;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.Droits;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.PriseEnCharge;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.RendezVousUrgence;

/**
 * Workflow « prise en charge automatisée d'une alerte médicale à domicile » (sujet, partie A) :
 * 1. alerte reçue (gRPC) → 2. droits CNAM (SOAP) → 3. créneau d'urgence (REST) → 4. fiche d'urgence (GraphQL).
 * Orchestration codée (D9) ; aucun service n'appelle un autre service (D5).
 * L'alerte puis le résultat sont diffusés en direct au client web (SSE, P3.5, contracts/sse).
 * Patient non couvert (P5.1) : la prise en charge continue, marquée « à régulariser » au REST et dans la fiche,
 * avec la raison donnée par le service SOAP en commentaire.
 */
@Service
public class WorkflowAlerte {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAlerte.class);

    public enum Etat { TERMINE, AUCUN_CRENEAU, ECHEC }

    /** rendezVousId, debutRendezVous et ficheId sont null si etat ≠ TERMINE. */
    public record Resultat(Etat etat, String ficheId, boolean aRegulariser, String rendezVousId,
                           OffsetDateTime debutRendezVous) {}

    private final Ports.Dossier dossier;
    private final Ports.Portail portail;
    private final Ports.TableauDeBord tableauDeBord;
    private final Ports.Diffusion diffusion;
    private final ZoneId fuseau;
    private final Clock horloge;

    @Autowired
    public WorkflowAlerte(Ports.Dossier dossier, Ports.Portail portail, Ports.TableauDeBord tableauDeBord,
                          Ports.Diffusion diffusion, @Value("${orchestrateur.fuseau}") String fuseau) {
        this(dossier, portail, tableauDeBord, diffusion, fuseau, Clock.systemUTC());
    }

    WorkflowAlerte(Ports.Dossier dossier, Ports.Portail portail, Ports.TableauDeBord tableauDeBord,
                   Ports.Diffusion diffusion, String fuseau, Clock horloge) {
        this.dossier = dossier;
        this.portail = portail;
        this.tableauDeBord = tableauDeBord;
        this.diffusion = diffusion;
        this.fuseau = ZoneId.of(fuseau);
        this.horloge = horloge;
    }

    public Resultat traiter(AlerteRecue alerte) {
        Resultat resultat = executer(alerte);
        diffuser(alerte, resultat);
        return resultat;
    }

    private Resultat executer(AlerteRecue alerte) {
        String id = alerte.id();
        try {
            log.info("[{}] Étape 1/4 gRPC : alerte {} reçue pour {} — {}", id, alerte.niveau(), alerte.patientId(), alerte.message());
            diffuserSansBloquer(id, () -> diffusion.alerte(alerte));

            Droits droits = dossier.verifierDroits(alerte.patientId(), alerte.horodatage().atZoneSameInstant(fuseau).toLocalDate());
            boolean aRegulariser = !droits.priseEnCharge();
            log.info("[{}] Étape 2/4 SOAP : droits CNAM {} (taux {}) → {}", id, droits.statut(),
                    droits.tauxRemboursement() == null ? "-" : droits.tauxRemboursement() + " %",
                    aRegulariser ? "prise en charge À RÉGULARISER" : "prise en charge validée");

            Optional<RendezVousUrgence> rdv = portail.reserverUrgence(alerte, aRegulariser);
            if (rdv.isEmpty()) {
                log.warn("[{}] Étape 3/4 REST : aucun créneau d'urgence libre — envoi d'ambulance prévu en P5.2, workflow arrêté", id);
                return new Resultat(Etat.AUCUN_CRENEAU, null, aRegulariser, null, null);
            }
            log.info("[{}] Étape 3/4 REST : rendez-vous {} réservé à {}", id, rdv.get().id(), rdv.get().debut());

            tableauDeBord.enregistrerAlerte(alerte);
            tableauDeBord.mettreAJourCouverture(alerte.patientId(), droits);
            String ficheId = tableauDeBord.creerFicheUrgence(alerte, aRegulariser, commentaireRegularisation(droits), rdv.get());
            log.info("[{}] Étape 4/4 GraphQL : alerte et couverture enregistrées, fiche d'urgence {} créée", id, ficheId);

            log.info("[{}] Workflow terminé : patient {}, rendez-vous {}, fiche {}", id, alerte.patientId(), rdv.get().id(), ficheId);
            return new Resultat(Etat.TERMINE, ficheId, aRegulariser, rdv.get().id(), rdv.get().debut());
        } catch (RuntimeException e) {
            log.error("[{}] Workflow en échec : {}", id, e.getMessage());
            return new Resultat(Etat.ECHEC, null, false, null, null);
        }
    }

    /**
     * P5.1 : patient non couvert (droits EXPIRES ou NON_AFFILIE) → les soins sont assurés, la fiche explique ce qui
     * reste à régulariser à partir de la réponse SOAP ; null si la prise en charge est validée.
     */
    static String commentaireRegularisation(Droits droits) {
        if (droits.priseEnCharge()) {
            return null;
        }
        String raison = switch (droits.statut()) {
            case "EXPIRES" -> "droits CNAM expirés" + (droits.codeVignette() == null ? "" : " (vignette " + droits.codeVignette() + ")");
            case "NON_AFFILIE" -> "patient non affilié à la CNAM";
            default -> "droits CNAM " + droits.statut();
        };
        String motif = droits.motif() == null || droits.motif().isBlank() ? "" : " — " + droits.motif();
        return "À régulariser : " + raison + motif + ". Soins assurés, dossier administratif à compléter.";
    }

    private void diffuser(AlerteRecue alerte, Resultat resultat) {
        PriseEnCharge evenement = new PriseEnCharge(alerte.id(), alerte.patientId(), resultat.etat().name(),
                resultat.aRegulariser(), resultat.rendezVousId(), resultat.debutRendezVous(), resultat.ficheId(),
                OffsetDateTime.now(horloge).atZoneSameInstant(fuseau).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS));
        diffuserSansBloquer(alerte.id(), () -> diffusion.priseEnCharge(evenement));
    }

    /** Une panne de la diffusion n'interrompt jamais la prise en charge du patient. */
    private static void diffuserSansBloquer(String id, Runnable envoi) {
        try {
            envoi.run();
        } catch (RuntimeException e) {
            log.warn("[{}] Diffusion SSE impossible : {}", id, e.getMessage());
        }
    }
}
