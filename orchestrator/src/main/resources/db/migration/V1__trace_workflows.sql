-- Trace de chaque instance du workflow de prise en charge (P5.3, D54 ; contrat contracts/orchestrateur/workflows-openapi.yaml)
-- Identifiant public WF- + 6 chiffres = identifiant de la base.

CREATE TABLE workflow (
    id              BIGSERIAL    PRIMARY KEY,
    alerte_id       VARCHAR(40)  NOT NULL,
    patient_id      VARCHAR(10)  NOT NULL,
    niveau          VARCHAR(10)  NOT NULL,
    message         VARCHAR(300) NOT NULL,
    debut           TIMESTAMPTZ  NOT NULL,
    fin             TIMESTAMPTZ,
    etat            VARCHAR(10)  NOT NULL CHECK (etat IN ('EN_COURS', 'TERMINE', 'AMBULANCE', 'ECHEC')),
    a_regulariser   BOOLEAN      NOT NULL DEFAULT FALSE,
    rendez_vous_id  VARCHAR(20),
    intervention_id VARCHAR(20),
    fiche_id        VARCHAR(20),
    erreur          VARCHAR(500)
);

CREATE TABLE workflow_etape (
    workflow_id BIGINT       NOT NULL REFERENCES workflow (id) ON DELETE CASCADE,
    ordre       INTEGER      NOT NULL,
    nom         VARCHAR(30)  NOT NULL,
    service     VARCHAR(10)  NOT NULL,
    debut       TIMESTAMPTZ  NOT NULL,
    duree_ms    BIGINT       NOT NULL,
    tentatives  INTEGER      NOT NULL CHECK (tentatives >= 1),
    statut      VARCHAR(10)  NOT NULL CHECK (statut IN ('OK', 'REFUS', 'ECHEC')),
    detail      VARCHAR(500),
    PRIMARY KEY (workflow_id, ordre)
);

CREATE INDEX idx_workflow_debut ON workflow (debut DESC);
CREATE INDEX idx_workflow_patient ON workflow (patient_id);
