-- Schéma du service SOAP « Dossier patient & facturation CNAM » (P2.SOAP.5).
-- Référentiel chargé depuis contracts/seed au démarrage ; feuilles de soins écrites par emettreFeuilleDeSoins.

CREATE TABLE patient (
    id VARCHAR(10) PRIMARY KEY CHECK (id ~ '^TN-[0-9]{7}$')
);

CREATE TABLE medecin (
    id VARCHAR(7) PRIMARY KEY CHECK (id ~ '^MED-[0-9]{3}$')
);

CREATE TABLE couverture (
    patient_id         VARCHAR(10) PRIMARY KEY REFERENCES patient (id),
    code_vignette      VARCHAR(15) NOT NULL UNIQUE CHECK (code_vignette ~ '^VIG-[0-9]{4}-[0-9]{6}$'),
    filiere            VARCHAR(20) NOT NULL CHECK (filiere IN ('PUBLIQUE', 'PRIVEE', 'REMBOURSEMENT')),
    date_debut         DATE        NOT NULL,
    date_fin           DATE        NOT NULL,
    taux_remboursement INTEGER     NOT NULL CHECK (taux_remboursement BETWEEN 0 AND 100),
    CHECK (date_debut <= date_fin)
);

CREATE TABLE lettre_cle (
    code            VARCHAR(3)     PRIMARY KEY,
    libelle         VARCHAR(100)   NOT NULL,
    valeur_unitaire NUMERIC(10, 3) NOT NULL CHECK (valeur_unitaire > 0)
);

CREATE TABLE traitement (
    id          VARCHAR(7)   PRIMARY KEY,
    patient_id  VARCHAR(10)  NOT NULL REFERENCES patient (id),
    medecin_id  VARCHAR(7)   NOT NULL REFERENCES medecin (id),
    medicament  VARCHAR(100) NOT NULL,
    dosage      VARCHAR(50)  NOT NULL,
    date_debut  DATE         NOT NULL
);

CREATE TABLE feuille_de_soins (
    id                 BIGSERIAL      PRIMARY KEY,
    numero             VARCHAR(14)    UNIQUE,  -- FS-AAAA-xxxxxx, attribué après insertion (dépend de id)
    patient_id         VARCHAR(10)    NOT NULL REFERENCES patient (id),
    code_vignette      VARCHAR(15)    NOT NULL,
    date_emission      DATE           NOT NULL,
    montant_total      NUMERIC(12, 3) NOT NULL,
    taux_remboursement INTEGER        NOT NULL,
    montant_rembourse  NUMERIC(12, 3) NOT NULL,
    code_certification VARCHAR(80)
);

CREATE TABLE acte_medical (
    id          BIGSERIAL      PRIMARY KEY,
    feuille_id  BIGINT         NOT NULL REFERENCES feuille_de_soins (id) ON DELETE CASCADE,
    ordre       INTEGER        NOT NULL,
    lettre_cle  VARCHAR(3)     NOT NULL REFERENCES lettre_cle (code),
    coefficient NUMERIC(8, 2)  NOT NULL CHECK (coefficient > 0),
    libelle     VARCHAR(200)   NOT NULL,
    date_acte   DATE           NOT NULL,
    medecin_id  VARCHAR(7)     NOT NULL REFERENCES medecin (id),
    montant     NUMERIC(12, 3) NOT NULL
);

CREATE INDEX idx_feuille_patient ON feuille_de_soins (patient_id);
CREATE INDEX idx_acte_feuille ON acte_medical (feuille_id);
CREATE INDEX idx_traitement_patient ON traitement (patient_id);
