package tn.ginf.smarthealthcare.rest.urgence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Schéma « DemandeUrgence » du contrat OpenAPI. */
public record DemandeUrgence(
        @NotNull @Pattern(regexp = "^TN-[0-9]{7}$") String patientId,
        @NotBlank String identifiantAlerte,
        @NotNull NiveauAlerte niveau,
        String motif,
        Boolean aRegulariser) {

    public enum NiveauAlerte { INFO, URGENT, CRITIQUE }
}
