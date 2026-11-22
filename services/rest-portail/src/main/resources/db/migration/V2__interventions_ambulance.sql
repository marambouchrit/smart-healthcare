-- P5.2 : interventions d'ambulance déclenchées par l'orchestrateur quand aucun créneau d'urgence n'est libre (D53)
-- Identifiant public AMB- + 6 chiffres = identifiant de la base (comme RDV-, D19).
CREATE TABLE intervention_ambulance (
    id                 BIGSERIAL    PRIMARY KEY,
    patient_id         VARCHAR(10)  NOT NULL REFERENCES patient (id),
    identifiant_alerte VARCHAR(40)  NOT NULL,
    niveau             VARCHAR(10)  NOT NULL CHECK (niveau IN ('INFO', 'URGENT', 'CRITIQUE')),
    motif              VARCHAR(500),
    a_regulariser      BOOLEAN      NOT NULL,
    -- adresse du domicile au moment du déclenchement (les coordonnées du patient peuvent changer ensuite)
    rue                VARCHAR(200) NOT NULL,
    ville              VARCHAR(100) NOT NULL,
    code_postal        VARCHAR(4)   NOT NULL,
    declenchee_le      TIMESTAMPTZ  NOT NULL,
    arrivee_estimee    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_intervention_ambulance_patient ON intervention_ambulance (patient_id);
