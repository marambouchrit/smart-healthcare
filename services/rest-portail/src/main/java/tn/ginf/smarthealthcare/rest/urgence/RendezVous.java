package tn.ginf.smarthealthcare.rest.urgence;

import java.time.OffsetDateTime;
import tn.ginf.smarthealthcare.rest.creneau.Creneau;

/** Schéma « RendezVous » du contrat OpenAPI. */
public record RendezVous(String id, String patientId, Creneau creneau, String motif,
                         Statut statut, boolean urgence, OffsetDateTime creeLe) {

    public enum Statut { CONFIRME, ANNULE, HONORE }
}
