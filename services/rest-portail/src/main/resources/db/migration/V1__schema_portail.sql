-- Schéma du service REST « Portail patient & rendez-vous » (P2.REST.7).
-- Référentiel chargé depuis contracts/seed ; créneaux générés depuis plannings.json ; rendez-vous créés par l'API.

CREATE TABLE medecin (
    id           VARCHAR(7)   PRIMARY KEY CHECK (id ~ '^MED-[0-9]{3}$'),
    nom          VARCHAR(100) NOT NULL,
    prenom       VARCHAR(100) NOT NULL,
    service_code VARCHAR(20)  NOT NULL
);

-- Profil patient : seules les coordonnées (téléphone, email, adresse) sont modifiables par l'API
CREATE TABLE patient (
    id                  VARCHAR(10)  PRIMARY KEY CHECK (id ~ '^TN-[0-9]{7}$'),
    nom                 VARCHAR(100) NOT NULL,
    prenom              VARCHAR(100) NOT NULL,
    date_naissance      DATE         NOT NULL,
    sexe                VARCHAR(1)   NOT NULL CHECK (sexe IN ('M', 'F')),
    telephone           VARCHAR(30),
    email               VARCHAR(200),
    rue                 VARCHAR(200) NOT NULL,
    ville               VARCHAR(100) NOT NULL,
    code_postal         VARCHAR(4)   NOT NULL CHECK (code_postal ~ '^[0-9]{4}$'),
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

-- Créneau exposé sous la forme CRN-xxxxxx (identifiant de la base) ; un créneau n'existe qu'une fois par médecin et heure
CREATE TABLE creneau (
    id           BIGSERIAL   PRIMARY KEY,
    medecin_id   VARCHAR(7)  NOT NULL REFERENCES medecin (id),
    service_code VARCHAR(20) NOT NULL,
    debut        TIMESTAMPTZ NOT NULL,
    fin          TIMESTAMPTZ NOT NULL CHECK (fin > debut),
    UNIQUE (medecin_id, debut)
);

-- Rendez-vous exposé sous la forme RDV-xxxxxx
CREATE TABLE rendez_vous (
    id         BIGSERIAL    PRIMARY KEY,
    patient_id VARCHAR(10)  NOT NULL REFERENCES patient (id),
    creneau_id BIGINT       NOT NULL REFERENCES creneau (id),
    motif      VARCHAR(500),
    statut     VARCHAR(10)  NOT NULL CHECK (statut IN ('CONFIRME', 'ANNULE', 'HONORE')),
    urgence    BOOLEAN      NOT NULL,
    cree_le    TIMESTAMPTZ  NOT NULL
);

-- Un créneau ne porte qu'un seul rendez-vous non annulé : la base refuse toute double réservation (D28, D38)
CREATE UNIQUE INDEX rendez_vous_un_seul_actif_par_creneau ON rendez_vous (creneau_id) WHERE statut <> 'ANNULE';

CREATE INDEX idx_creneau_debut ON creneau (debut);
CREATE INDEX idx_rendez_vous_patient ON rendez_vous (patient_id);
CREATE INDEX idx_traitement_patient ON traitement (patient_id);
