package tn.ginf.smarthealthcare.orchestrator.workflow;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Ce dont le workflow a besoin, indépendamment des protocoles : chaque interface est implémentée par un client
 * (SOAP, REST, GraphQL) dans le paquet « clients ». Les tests les remplacent par des faux services.
 */
public final class Ports {

    private Ports() {}

    /** Alerte reçue du service de télémétrie (gRPC), convertie en types Java. */
    public record AlerteRecue(String id, String patientId, String niveau, String type, double valeur, double seuil,
                              String message, OffsetDateTime horodatage) {}

    /** codeVignette, tauxRemboursement et dateFin sont null si le patient n'est pas affilié. */
    public record Droits(String statut, boolean priseEnCharge, Integer tauxRemboursement, String motif,
                         String codeVignette, LocalDate dateFin) {}

    public record RendezVousUrgence(String id, OffsetDateTime debut) {}

    /** Ambulance envoyée au domicile (POST /api/ambulances, P5.2). */
    public record InterventionAmbulance(String id, OffsetDateTime arriveeEstimee) {}

    /** Dernière valeur d'un type de mesure, relayée vers le tableau de bord (P2.GRPC.5, D18). */
    public record ConstanteMesuree(String type, double valeur, OffsetDateTime horodatage) {}

    /** Étape 2 — SOAP : droits CNAM du patient. */
    public interface Dossier {
        Droits verifierDroits(String patientId, LocalDate datePrestation);
    }

    /** Étape 3 — REST : créneau d'urgence ; vide si aucun créneau libre (HTTP 409) → ambulance (P5.2). */
    public interface Portail {
        Optional<RendezVousUrgence> reserverUrgence(AlerteRecue alerte, boolean aRegulariser);

        InterventionAmbulance envoyerAmbulance(AlerteRecue alerte, boolean aRegulariser);
    }

    /**
     * Résultat d'un workflow diffusé au client web (contracts/sse, événement « prise-en-charge ») ;
     * rendez-vous renseigné si etat = TERMINE, ambulance si etat = AMBULANCE, fiche sauf si ECHEC (D53).
     */
    public record PriseEnCharge(String alerteId, String patientId, String etat, boolean aRegulariser,
                                String rendezVousId, OffsetDateTime debutRendezVous, String ficheId,
                                String interventionId, OffsetDateTime arriveeEstimee, OffsetDateTime horodatage) {}

    /** Alertes et résultats poussés en direct au client web (SSE, P3.5, D6) ; ne doit jamais bloquer le workflow. */
    public interface Diffusion {
        void alerte(AlerteRecue alerte);

        void priseEnCharge(PriseEnCharge resultat);
    }

    /** Étape 4 — GraphQL : vue consolidée du tableau de bord. */
    public interface TableauDeBord {
        void enregistrerAlerte(AlerteRecue alerte);

        /** Dernier résultat de la vérification CNAM, affiché dans le dossier consolidé. */
        void mettreAJourCouverture(String patientId, Droits droits);

        /**
         * Fiche d'urgence : soit un rendez-vous (CONSULTATION_URGENCE), soit une ambulance (AMBULANCE, P5.2) ;
         * l'autre vaut null. commentaire : ambulance et/ou régularisation (P5.1), null sinon.
         */
        String creerFicheUrgence(AlerteRecue alerte, boolean aRegulariser, String commentaire,
                                 RendezVousUrgence rendezVous, InterventionAmbulance ambulance);

        /** Dernières constantes d'un patient (mutation enregistrerConstantes). */
        void enregistrerConstantes(String patientId, java.util.List<ConstanteMesuree> constantes);
    }

    /** Une étape tracée (contrat contracts/orchestrateur/workflows-openapi.yaml, schéma Etape). */
    public record EtapeTrace(int ordre, String nom, String service, OffsetDateTime debut, long dureeMs, int tentatives,
                             String statut, String detail) {}

    /**
     * Trace de chaque instance de workflow (P5.3, D54), écrite dans la base de l'orchestrateur ; comme la diffusion,
     * une panne de la trace n'interrompt jamais la prise en charge.
     */
    public interface Traces {
        /** Nouvelle instance EN_COURS ; renvoie son identifiant interne. */
        long demarrer(AlerteRecue alerte, OffsetDateTime debut);

        void etape(long workflowId, EtapeTrace etape);

        /** Fin de l'instance (instant précis, pour la durée) : état final, résultats et cause de l'échec éventuel. */
        void terminer(long workflowId, PriseEnCharge resultat, String erreur, OffsetDateTime fin);
    }
}
