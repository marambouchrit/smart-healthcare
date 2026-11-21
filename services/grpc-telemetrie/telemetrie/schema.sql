-- Schéma du service gRPC « Télémétrie » (P2.GRPC.3), créé au démarrage s'il n'existe pas.
-- Toutes les mesures reçues sont conservées ; une alerte est rattachée à la mesure qui l'a déclenchée.
-- L'identifiant d'alerte ALR-xxxxxx vient d'une séquence de la base : il reste unique après un redémarrage.

CREATE TABLE IF NOT EXISTS mesure (
    id         BIGSERIAL        PRIMARY KEY,
    patient_id VARCHAR(10)      NOT NULL CHECK (patient_id ~ '^TN-[0-9]{7}$'),
    capteur    VARCHAR(100),
    type       VARCHAR(30)      NOT NULL CHECK (type IN ('FREQUENCE_CARDIAQUE', 'SPO2', 'CHUTE')),
    valeur     DOUBLE PRECISION NOT NULL,
    horodatage TIMESTAMPTZ      NOT NULL,
    recue_le   TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS alerte (
    numero      BIGSERIAL        PRIMARY KEY,
    identifiant VARCHAR(20)      GENERATED ALWAYS AS ('ALR-' || lpad(numero::text, 6, '0')) STORED UNIQUE,
    mesure_id   BIGINT           NOT NULL REFERENCES mesure (id),
    patient_id  VARCHAR(10)      NOT NULL,
    niveau      VARCHAR(10)      NOT NULL CHECK (niveau IN ('INFO', 'URGENT', 'CRITIQUE')),
    type        VARCHAR(30)      NOT NULL,
    valeur      DOUBLE PRECISION NOT NULL,
    seuil       DOUBLE PRECISION NOT NULL,
    message     VARCHAR(300)     NOT NULL,
    horodatage  TIMESTAMPTZ      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mesure_patient ON mesure (patient_id, type, horodatage DESC);
CREATE INDEX IF NOT EXISTS idx_alerte_patient ON alerte (patient_id, horodatage DESC);
