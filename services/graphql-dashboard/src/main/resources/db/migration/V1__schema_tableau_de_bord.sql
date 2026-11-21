-- Schéma de la vue consolidée du tableau de bord (P2.GQL.4).
-- Référentiel chargé depuis contracts/seed ; alertes, fiches, constantes et couvertures écrites par l'orchestrateur.

CREATE TABLE service_hospitalier (
    code    VARCHAR(20)  PRIMARY KEY,
    libelle VARCHAR(100) NOT NULL
);

CREATE TABLE medecin (
    id           VARCHAR(7)   PRIMARY KEY CHECK (id ~ '^MED-[0-9]{3}$'),
    nom          VARCHAR(100) NOT NULL,
    prenom       VARCHAR(100) NOT NULL,
    specialite   VARCHAR(100) NOT NULL,
    service_code VARCHAR(20)  NOT NULL REFERENCES service_hospitalier (code)
);

CREATE TABLE patient (
    id                  VARCHAR(10)  PRIMARY KEY CHECK (id ~ '^TN-[0-9]{7}$'),
    nom                 VARCHAR(100) NOT NULL,
    prenom              VARCHAR(100) NOT NULL,
    date_naissance      DATE         NOT NULL,
    sexe                CHAR(1)      NOT NULL CHECK (sexe IN ('M', 'F')),
    telephone           VARCHAR(30),
    service_code        VARCHAR(20)  NOT NULL REFERENCES service_hospitalier (code),
    pathologie          VARCHAR(200) NOT NULL,
    niveau_risque       VARCHAR(10)  NOT NULL CHECK (niveau_risque IN ('FAIBLE', 'MOYEN', 'ELEVE')),
    medecin_traitant_id VARCHAR(7)   REFERENCES medecin (id)
);

CREATE TABLE traitement (
    id         VARCHAR(7)   PRIMARY KEY,
    patient_id VARCHAR(10)  NOT NULL REFERENCES patient (id),
    medecin_id VARCHAR(7)   NOT NULL REFERENCES medecin (id),
    medicament VARCHAR(100) NOT NULL,
    dosage     VARCHAR(50)  NOT NULL,
    posologie  VARCHAR(200) NOT NULL,
    date_debut DATE         NOT NULL,
    date_fin   DATE,
    statut     VARCHAR(10)  NOT NULL CHECK (statut IN ('EN_COURS', 'TERMINE'))
);

CREATE TABLE alerte (
    id         VARCHAR(20)      PRIMARY KEY,           -- identifiant attribué par le service gRPC
    patient_id VARCHAR(10)      NOT NULL REFERENCES patient (id),
    niveau     VARCHAR(10)      NOT NULL CHECK (niveau IN ('INFO', 'URGENT', 'CRITIQUE')),
    type       VARCHAR(30)      NOT NULL CHECK (type IN ('FREQUENCE_CARDIAQUE', 'SPO2', 'CHUTE')),
    valeur     DOUBLE PRECISION NOT NULL,
    seuil      DOUBLE PRECISION NOT NULL,
    message    VARCHAR(300)     NOT NULL,
    horodatage TIMESTAMPTZ      NOT NULL
);

CREATE TABLE fiche_urgence (
    id                     BIGSERIAL    PRIMARY KEY,   -- exposé sous la forme FIC-xxxxxx
    patient_id             VARCHAR(10)  NOT NULL REFERENCES patient (id),
    alerte_id              VARCHAR(20)  NOT NULL REFERENCES alerte (id),
    statut_prise_en_charge VARCHAR(20)  NOT NULL CHECK (statut_prise_en_charge IN ('PRISE_EN_CHARGE', 'A_REGULARISER')),
    action                 VARCHAR(30)  NOT NULL CHECK (action IN ('CONSULTATION_URGENCE', 'AMBULANCE')),
    identifiant_rendez_vous VARCHAR(20),
    date_rendez_vous       TIMESTAMPTZ,
    commentaire            VARCHAR(500),
    creee_le               TIMESTAMPTZ  NOT NULL
);

-- Dernière valeur connue de chaque type de mesure, par patient (D18)
CREATE TABLE constante (
    patient_id VARCHAR(10)      NOT NULL REFERENCES patient (id),
    type       VARCHAR(30)      NOT NULL CHECK (type IN ('FREQUENCE_CARDIAQUE', 'SPO2', 'CHUTE')),
    valeur     DOUBLE PRECISION NOT NULL,
    horodatage TIMESTAMPTZ      NOT NULL,
    PRIMARY KEY (patient_id, type)
);

-- Dernière couverture CNAM transmise par l'orchestrateur
CREATE TABLE couverture (
    patient_id         VARCHAR(10) PRIMARY KEY REFERENCES patient (id),
    statut             VARCHAR(15) NOT NULL CHECK (statut IN ('OUVERTS', 'EXPIRES', 'NON_AFFILIE')),
    code_vignette      VARCHAR(15),
    taux_remboursement INTEGER     NOT NULL CHECK (taux_remboursement BETWEEN 0 AND 100),
    date_fin           DATE,
    mis_a_jour_le      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_alerte_patient ON alerte (patient_id, horodatage DESC);
CREATE INDEX idx_fiche_patient ON fiche_urgence (patient_id, creee_le DESC);
CREATE INDEX idx_traitement_patient ON traitement (patient_id);
CREATE INDEX idx_patient_service ON patient (service_code);
