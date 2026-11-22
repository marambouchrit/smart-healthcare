package tn.ginf.smarthealthcare.rest.urgence;

import java.time.OffsetDateTime;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Adresse;

/** Schéma « InterventionAmbulance » du contrat OpenAPI. */
public record InterventionAmbulance(String id, String patientId, String identifiantAlerte, Adresse adresse,
                                    OffsetDateTime declencheeLe, OffsetDateTime arriveeEstimee) {}
