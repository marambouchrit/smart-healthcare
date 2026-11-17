package tn.ginf.smarthealthcare.orchestrator.workflow;

import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.Droits;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.RendezVousUrgence;

/**
 * Workflow « prise en charge automatisée d'une alerte médicale à domicile » (sujet, partie A) :
 * 1. alerte reçue (gRPC) → 2. droits CNAM (SOAP) → 3. créneau d'urgence (REST) → 4. fiche d'urgence (GraphQL).
 * Orchestration codée (D9) ; aucun service n'appelle un autre service (D5).
 * Phase 1 : pas de nouvelle tentative ni d'ambulance (P5.2, P5.3).
 */
@Service
public class WorkflowAlerte {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAlerte.class);

    public enum Etat { TERMINE, AUCUN_CRENEAU, ECHEC }

    public record Resultat(Etat etat, String ficheId, boolean aRegulariser, String rendezVousId) {}

    private final Ports.Dossier dossier;
    private final Ports.Portail portail;
    private final Ports.TableauDeBord tableauDeBord;
    private final ZoneId fuseau;

    public WorkflowAlerte(Ports.Dossier dossier, Ports.Portail portail, Ports.TableauDeBord tableauDeBord,
                          @Value("${orchestrateur.fuseau}") String fuseau) {
        this.dossier = dossier;
        this.portail = portail;
        this.tableauDeBord = tableauDeBord;
        this.fuseau = ZoneId.of(fuseau);
    }

    public Resultat traiter(AlerteRecue alerte) {
        String id = alerte.id();
        try {
            log.info("[{}] Étape 1/4 gRPC : alerte {} reçue pour {} — {}", id, alerte.niveau(), alerte.patientId(), alerte.message());

            Droits droits = dossier.verifierDroits(alerte.patientId(), alerte.horodatage().atZoneSameInstant(fuseau).toLocalDate());
            boolean aRegulariser = !droits.priseEnCharge();
            log.info("[{}] Étape 2/4 SOAP : droits CNAM {} (taux {}) → {}", id, droits.statut(),
                    droits.tauxRemboursement() == null ? "-" : droits.tauxRemboursement() + " %",
                    aRegulariser ? "prise en charge À RÉGULARISER" : "prise en charge validée");

            Optional<RendezVousUrgence> rdv = portail.reserverUrgence(alerte, aRegulariser);
            if (rdv.isEmpty()) {
                log.warn("[{}] Étape 3/4 REST : aucun créneau d'urgence libre — envoi d'ambulance prévu en P5.2, workflow arrêté", id);
                return new Resultat(Etat.AUCUN_CRENEAU, null, aRegulariser, null);
            }
            log.info("[{}] Étape 3/4 REST : rendez-vous {} réservé à {}", id, rdv.get().id(), rdv.get().debut());

            tableauDeBord.enregistrerAlerte(alerte);
            String ficheId = tableauDeBord.creerFicheUrgence(alerte, aRegulariser, rdv.get());
            log.info("[{}] Étape 4/4 GraphQL : alerte enregistrée et fiche d'urgence {} créée", id, ficheId);

            log.info("[{}] Workflow terminé : patient {}, rendez-vous {}, fiche {}", id, alerte.patientId(), rdv.get().id(), ficheId);
            return new Resultat(Etat.TERMINE, ficheId, aRegulariser, rdv.get().id());
        } catch (RuntimeException e) {
            log.error("[{}] Workflow en échec : {}", id, e.getMessage());
            return new Resultat(Etat.ECHEC, null, false, null);
        }
    }
}
