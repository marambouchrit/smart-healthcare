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

    /** Dernière valeur d'un type de mesure, relayée vers le tableau de bord (P2.GRPC.5, D18). */
    public record ConstanteMesuree(String type, double valeur, OffsetDateTime horodatage) {}

    /** Étape 2 — SOAP : droits CNAM du patient. */
    public interface Dossier {
        Droits verifierDroits(String patientId, LocalDate datePrestation);
    }

    /** Étape 3 — REST : créneau d'urgence ; vide si aucun créneau libre (HTTP 409). */
    public interface Portail {
        Optional<RendezVousUrgence> reserverUrgence(AlerteRecue alerte, boolean aRegulariser);
    }

    /**
     * Résultat d'un workflow diffusé au client web (contracts/sse, événement « prise-en-charge ») ;
     * rendezVousId, debutRendezVous et ficheId sont null si etat ≠ TERMINE.
     */
    public record PriseEnCharge(String alerteId, String patientId, String etat, boolean aRegulariser,
                                String rendezVousId, OffsetDateTime debutRendezVous, String ficheId,
                                OffsetDateTime horodatage) {}

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

        /** commentaire : raison de la régularisation (P5.1), null si la prise en charge est validée. */
        String creerFicheUrgence(AlerteRecue alerte, boolean aRegulariser, String commentaire, RendezVousUrgence rendezVous);

        /** Dernières constantes d'un patient (mutation enregistrerConstantes). */
        void enregistrerConstantes(String patientId, java.util.List<ConstanteMesuree> constantes);
    }
}
