package tn.ginf.smarthealthcare.rest.creneau;

import java.time.OffsetDateTime;

/** Schéma « Creneau » du contrat OpenAPI. */
public record Creneau(String id, String medecinId, String serviceCode,
                      OffsetDateTime debut, OffsetDateTime fin, boolean disponible) {

    public Creneau reserve() {
        return new Creneau(id, medecinId, serviceCode, debut, fin, false);
    }
}
