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

    public record Droits(String statut, boolean priseEnCharge, Integer tauxRemboursement, String motif) {}

    public record RendezVousUrgence(String id, OffsetDateTime debut) {}

    /** Étape 2 — SOAP : droits CNAM du patient. */
    public interface Dossier {
        Droits verifierDroits(String patientId, LocalDate datePrestation);
    }

    /** Étape 3 — REST : créneau d'urgence ; vide si aucun créneau libre (HTTP 409). */
    public interface Portail {
        Optional<RendezVousUrgence> reserverUrgence(AlerteRecue alerte, boolean aRegulariser);
    }

    /** Étape 4 — GraphQL : vue consolidée du tableau de bord. */
    public interface TableauDeBord {
        void enregistrerAlerte(AlerteRecue alerte);

        String creerFicheUrgence(AlerteRecue alerte, boolean aRegulariser, RendezVousUrgence rendezVous);
    }
}
