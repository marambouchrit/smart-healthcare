"""Persistance des mesures et des alertes (P2.GRPC.3, D37).

DepotPostgres : base PostgreSQL du service (D8, D35), schéma créé par schema.sql au démarrage.
DepotMemoire  : même interface, en mémoire (tests unitaires qui n'ont pas besoin de base).
Une mesure et l'éventuelle alerte qu'elle déclenche sont écrites dans la même transaction.
"""
import itertools
import re
import threading
from datetime import datetime, timezone
from pathlib import Path

from .gen import telemetrie_pb2 as pb

SCHEMA = Path(__file__).resolve().parent / "schema.sql"


def _instant(ts) -> datetime:
    """Timestamp protobuf → datetime UTC (maintenant si le capteur n'a pas daté la mesure)."""
    if ts.seconds == 0 and ts.nanos == 0:
        return datetime.now(timezone.utc)
    return ts.ToDatetime(tzinfo=timezone.utc)


class DepotMemoire:
    """Dépôt en mémoire : numérotation des alertes à partir de 1, mesures et alertes gardées en listes."""

    def __init__(self):
        self._compteur = itertools.count(1)
        self._verrou = threading.Lock()
        self.mesures, self.alertes = [], []

    def enregistrer(self, mesure, alerte=None):
        """Enregistre la mesure (et l'alerte, dont l'identifiant est alors attribué) ; renvoie l'alerte ou None."""
        with self._verrou:
            self.mesures.append(mesure)
            if alerte is None:
                return None
            alerte.identifiant_alerte = f"ALR-{next(self._compteur):06d}"
            self.alertes.append(alerte)
            return alerte

    def fermer(self):
        pass


class DepotPostgres:
    """Dépôt PostgreSQL (psycopg 3). Une connexion partagée, protégée par un verrou (serveur gRPC multi-fils)."""

    def __init__(self, dsn: str, schema: str | None = None):
        import psycopg  # importé ici : le mode mémoire n'exige pas le pilote

        if schema is not None and not re.fullmatch(r"[a-z_][a-z0-9_]*", schema):
            raise ValueError(f"nom de schéma invalide : {schema}")
        self._verrou = threading.Lock()
        self._cnx = psycopg.connect(dsn, autocommit=True)
        with self._cnx.cursor() as c:
            if schema:
                c.execute(f"CREATE SCHEMA IF NOT EXISTS {schema}")
                c.execute(f"SET search_path TO {schema}")
            c.execute(SCHEMA.read_text(encoding="utf-8"))

    def enregistrer(self, mesure, alerte=None):
        with self._verrou, self._cnx.transaction(), self._cnx.cursor() as c:
            c.execute("INSERT INTO mesure (patient_id, capteur, type, valeur, horodatage) VALUES (%s, %s, %s, %s, %s) "
                      "RETURNING id",
                      (mesure.identifiant_patient, mesure.identifiant_capteur or None, pb.TypeMesure.Name(mesure.type),
                       mesure.valeur, _instant(mesure.horodatage)))
            mesure_id = c.fetchone()[0]
            if alerte is None:
                return None
            c.execute("INSERT INTO alerte (mesure_id, patient_id, niveau, type, valeur, seuil, message, horodatage) "
                      "VALUES (%s, %s, %s, %s, %s, %s, %s, %s) RETURNING identifiant",
                      (mesure_id, alerte.identifiant_patient, pb.Niveau.Name(alerte.niveau), pb.TypeMesure.Name(alerte.type),
                       alerte.valeur, alerte.seuil, alerte.message, _instant(alerte.horodatage)))
            alerte.identifiant_alerte = c.fetchone()[0]
            return alerte

    def requete(self, sql, params=None):
        """Lecture simple (tests et diagnostic)."""
        with self._verrou, self._cnx.cursor() as c:
            c.execute(sql, params)
            return c.fetchall()

    def fermer(self):
        self._cnx.close()
