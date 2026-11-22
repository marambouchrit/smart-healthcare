package tn.ginf.smarthealthcare.orchestrator.workflow;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.Droits;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.EtapeTrace;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.InterventionAmbulance;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.PriseEnCharge;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.RendezVousUrgence;

/**
 * Workflow « prise en charge automatisée d'une alerte médicale à domicile » (sujet, partie A) :
 * 1. alerte reçue (gRPC) → 2. droits CNAM (SOAP) → 3. créneau d'urgence ou, à défaut, ambulance (REST, P5.2)
 * → 4. fiche d'urgence (GraphQL).
 * Orchestration codée (D9) ; aucun service n'appelle un autre service (D5).
 * L'alerte puis le résultat sont diffusés en direct au client web (SSE, P3.5, contracts/sse).
 * Patient non couvert (P5.1) : la prise en charge continue, marquée « à régulariser » au REST et dans la fiche,
 * avec la raison donnée par le service SOAP en commentaire.
 * Robustesse (P5.3, D54) : chaque appel passe par {@link Tentatives} (délais d'attente dans les clients, nouveaux
 * essais sûrs) et chaque instance est tracée étape par étape ({@link Ports.Traces}).
 */
@Service
public class WorkflowAlerte {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAlerte.class);

    /** TERMINE : rendez-vous d'urgence ; AMBULANCE : aucun créneau libre, ambulance envoyée (D53) ; ECHEC. */
    public enum Etat { TERMINE, AMBULANCE, ECHEC }

    /** Rendez-vous renseigné si TERMINE, ambulance si AMBULANCE, fiche sauf si ECHEC. */
    public record Resultat(Etat etat, String ficheId, boolean aRegulariser, String rendezVousId,
                           OffsetDateTime debutRendezVous, String interventionId, OffsetDateTime arriveeEstimee) {
        static Resultat echec(boolean aRegulariser) {
            return new Resultat(Etat.ECHEC, null, aRegulariser, null, null, null, null);
        }
    }

    /** Étape en échec après les essais prévus : arrête le workflow (ECHEC). */
    static final class EtapeEchoueeException extends RuntimeException {
        EtapeEchoueeException(String nom, String service, int essais, RuntimeException cause) {
            super(nom + " (" + service + ")" + (essais > 1 ? " après " + essais + " essais" : "") + " : " + cause.getMessage(), cause);
        }
    }

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    /** Trace inactive (tests qui ne s'y intéressent pas). */
    static final Ports.Traces SANS_TRACE = new Ports.Traces() {
        public long demarrer(AlerteRecue alerte, OffsetDateTime debut) { return 0; }
        public void etape(long workflowId, EtapeTrace etape) {}
        public void terminer(long workflowId, PriseEnCharge resultat, String erreur, OffsetDateTime fin) {}
    };

    private final Ports.Dossier dossier;
    private final Ports.Portail portail;
    private final Ports.TableauDeBord tableauDeBord;
    private final Ports.Diffusion diffusion;
    private final Ports.Traces traces;
    private final Tentatives tentatives;
    private final ZoneId fuseau;
    private final Clock horloge;

    @Autowired
    public WorkflowAlerte(Ports.Dossier dossier, Ports.Portail portail, Ports.TableauDeBord tableauDeBord,
                          Ports.Diffusion diffusion, Ports.Traces traces,
                          @Value("${orchestrateur.appels.tentatives}") int maximumEssais,
                          @Value("${orchestrateur.appels.attente-initiale}") Duration attenteInitiale,
                          @Value("${orchestrateur.fuseau}") String fuseau) {
        this(dossier, portail, tableauDeBord, diffusion, traces,
                new Tentatives(maximumEssais, attenteInitiale, d -> Thread.sleep(d.toMillis())), fuseau, Clock.systemUTC());
    }

    WorkflowAlerte(Ports.Dossier dossier, Ports.Portail portail, Ports.TableauDeBord tableauDeBord,
                   Ports.Diffusion diffusion, Ports.Traces traces, Tentatives tentatives, String fuseau, Clock horloge) {
        this.dossier = dossier;
        this.portail = portail;
        this.tableauDeBord = tableauDeBord;
        this.diffusion = diffusion;
        this.traces = traces;
        this.tentatives = tentatives;
        this.fuseau = ZoneId.of(fuseau);
        this.horloge = horloge;
    }

    /** Sans trace et sans nouvel essai (tests des phases précédentes). */
    WorkflowAlerte(Ports.Dossier dossier, Ports.Portail portail, Ports.TableauDeBord tableauDeBord,
                   Ports.Diffusion diffusion, String fuseau, Clock horloge) {
        this(dossier, portail, tableauDeBord, diffusion, SANS_TRACE, Tentatives.sansAttente(1), fuseau, horloge);
    }

    public Resultat traiter(AlerteRecue alerte) {
        Instance instance = new Instance(alerte);
        Resultat resultat;
        String erreur = null;
        try {
            resultat = instance.executer();
        } catch (RuntimeException e) {
            erreur = e.getMessage();
            log.error("[{}] Workflow en échec : {}", alerte.id(), erreur);
            resultat = Resultat.echec(instance.aRegulariser);
        }
        PriseEnCharge evenement = evenement(alerte, resultat);
        instance.terminer(evenement, erreur);
        sansBloquer(alerte.id(), "Diffusion SSE", () -> diffusion.priseEnCharge(evenement));
        return resultat;
    }

    /** Une instance de workflow : ses étapes numérotées et sa trace. */
    private final class Instance {
        private final AlerteRecue alerte;
        private final String id;
        private Long workflowId;
        private int ordre;
        boolean aRegulariser;

        Instance(AlerteRecue alerte) {
            this.alerte = alerte;
            this.id = alerte.id();
            sansBloquer(id, "Trace", () -> workflowId = traces.demarrer(alerte, OffsetDateTime.now(horloge)));
        }

        Resultat executer() {
            log.info("[{}] Étape 1/4 gRPC : alerte {} reçue pour {} — {}", id, alerte.niveau(), alerte.patientId(), alerte.message());
            tracer("ALERTE_RECUE", "gRPC", OffsetDateTime.now(horloge), 0, 1, "OK", alerte.niveau() + " — " + alerte.message());
            sansBloquer(id, "Diffusion SSE", () -> diffusion.alerte(alerte));

            Droits droits = etape("DROITS_CNAM", "SOAP", false,
                    () -> dossier.verifierDroits(alerte.patientId(), alerte.horodatage().atZoneSameInstant(fuseau).toLocalDate()),
                    d -> d.statut() + (d.tauxRemboursement() == null ? "" : " " + d.tauxRemboursement() + " %"), null);
            aRegulariser = !droits.priseEnCharge();
            log.info("[{}] Étape 2/4 SOAP : droits CNAM {} (taux {}) → {}", id, droits.statut(),
                    droits.tauxRemboursement() == null ? "-" : droits.tauxRemboursement() + " %",
                    aRegulariser ? "prise en charge À RÉGULARISER" : "prise en charge validée");

            boolean regulariser = aRegulariser;
            Optional<RendezVousUrgence> rdv = etape("CRENEAU_URGENCE", "REST", true,
                    () -> portail.reserverUrgence(alerte, regulariser),
                    r -> r.map(x -> x.id() + " à " + heure(x.debut())).orElse("aucun créneau d'urgence libre (409)"),
                    Optional::isEmpty);
            InterventionAmbulance ambulance = null;
            if (rdv.isPresent()) {
                log.info("[{}] Étape 3/4 REST : rendez-vous {} réservé à {}", id, rdv.get().id(), rdv.get().debut());
            } else {
                // P5.2 : aucun créneau d'urgence libre aujourd'hui → ambulance au domicile du patient
                log.warn("[{}] Étape 3/4 REST : aucun créneau d'urgence libre → envoi d'une ambulance", id);
                ambulance = etape("AMBULANCE", "REST", true, () -> portail.envoyerAmbulance(alerte, regulariser),
                        a -> a.id() + ", arrivée estimée à " + heure(a.arriveeEstimee()), null);
                log.info("[{}] Étape 3/4 REST : ambulance {} envoyée, arrivée estimée {}", id, ambulance.id(), ambulance.arriveeEstimee());
            }

            etape("ALERTE_TABLEAU_DE_BORD", "GraphQL", false, () -> { tableauDeBord.enregistrerAlerte(alerte); return id; },
                    x -> x, null);
            etape("COUVERTURE", "GraphQL", false,
                    () -> { tableauDeBord.mettreAJourCouverture(alerte.patientId(), droits); return droits.statut(); }, x -> x, null);
            InterventionAmbulance envoyee = ambulance;
            String ficheId = etape("FICHE_URGENCE", "GraphQL", true,
                    () -> tableauDeBord.creerFicheUrgence(alerte, regulariser, commentaire(droits, envoyee), rdv.orElse(null), envoyee),
                    f -> f + (regulariser ? " (à régulariser)" : ""), null);
            log.info("[{}] Étape 4/4 GraphQL : alerte et couverture enregistrées, fiche d'urgence {} créée", id, ficheId);

            if (ambulance != null) {
                log.info("[{}] Workflow terminé : patient {}, ambulance {}, fiche {}", id, alerte.patientId(), ambulance.id(), ficheId);
                return new Resultat(Etat.AMBULANCE, ficheId, aRegulariser, null, null, ambulance.id(), ambulance.arriveeEstimee());
            }
            log.info("[{}] Workflow terminé : patient {}, rendez-vous {}, fiche {}", id, alerte.patientId(), rdv.get().id(), ficheId);
            return new Resultat(Etat.TERMINE, ficheId, aRegulariser, rdv.get().id(), rdv.get().debut(), null, null);
        }

        /** Appel d'un service avec la politique d'essais, tracé ; refus = réponse métier négative attendue (409). */
        <T> T etape(String nom, String service, boolean creeUneRessource, Supplier<T> appel,
                    Function<T, String> detail, Predicate<T> refus) {
            // instants précis dans la trace (durées exactes) ; l'API les affiche à la seconde (contrat)
            OffsetDateTime debut = OffsetDateTime.now(horloge);
            long t0 = horloge.millis();
            Tentatives.Issue<T> issue = tentatives.executer(creeUneRessource, appel);
            long duree = horloge.millis() - t0;
            if (issue.essais() > 1) {
                log.warn("[{}] {} ({}) : {} essais", id, nom, service, issue.essais());
            }
            if (!issue.reussie()) {
                tracer(nom, service, debut, duree, issue.essais(), "ECHEC", issue.erreur().getMessage());
                throw new EtapeEchoueeException(nom, service, issue.essais(), issue.erreur());
            }
            T valeur = issue.valeur();
            boolean estRefus = refus != null && refus.test(valeur);
            tracer(nom, service, debut, duree, issue.essais(), estRefus ? "REFUS" : "OK", detail.apply(valeur));
            return valeur;
        }

        void tracer(String nom, String service, OffsetDateTime debut, long dureeMs, int essais, String statut, String detail) {
            if (workflowId == null) {
                return;
            }
            EtapeTrace etape = new EtapeTrace(++ordre, nom, service, debut, dureeMs, essais, statut, tronquer(detail));
            sansBloquer(id, "Trace", () -> traces.etape(workflowId, etape));
        }

        void terminer(PriseEnCharge evenement, String erreur) {
            if (workflowId != null) {
                OffsetDateTime fin = OffsetDateTime.now(horloge);
                sansBloquer(id, "Trace", () -> traces.terminer(workflowId, evenement, tronquer(erreur), fin));
            }
        }
    }

    private PriseEnCharge evenement(AlerteRecue alerte, Resultat r) {
        return new PriseEnCharge(alerte.id(), alerte.patientId(), r.etat().name(), r.aRegulariser(), r.rendezVousId(),
                r.debutRendezVous(), r.ficheId(), r.interventionId(), r.arriveeEstimee(), maintenant());
    }

    /** Commentaire de la fiche : ambulance (P5.2) puis régularisation (P5.1) ; null si rien à signaler. */
    String commentaire(Droits droits, InterventionAmbulance ambulance) {
        String regularisation = commentaireRegularisation(droits);
        if (ambulance == null) {
            return regularisation;
        }
        String envoi = "Aucun créneau d'urgence libre : ambulance " + ambulance.id() + " envoyée au domicile, arrivée estimée à "
                + heure(ambulance.arriveeEstimee()) + ".";
        return regularisation == null ? envoi : envoi + " " + regularisation;
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

    private String heure(OffsetDateTime instant) {
        return instant.atZoneSameInstant(fuseau).format(HEURE);
    }

    private OffsetDateTime maintenant() {
        return OffsetDateTime.now(horloge).atZoneSameInstant(fuseau).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS);
    }

    private static String tronquer(String texte) {
        return texte == null || texte.length() <= 500 ? texte : texte.substring(0, 497) + "…";
    }

    /** Une panne de la diffusion ou de la trace n'interrompt jamais la prise en charge du patient. */
    private static void sansBloquer(String id, String quoi, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("[{}] {} impossible : {}", id, quoi, e.getMessage());
        }
    }
}
