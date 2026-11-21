"""Scénarios multi-patients (P2.GRPC.4, D34) : plusieurs capteurs virtuels émettent en parallèle,
chacun sur son propre flux gRPC EnvoyerMesures.

Format d'un fichier de scénario (voir scenarios/*.json) :
{
  "description": "…",
  "intervalle": 1.0,          # secondes entre deux mesures (défaut, modifiable par capteur)
  "mesures": 10,              # nombre de mesures par capteur (défaut, modifiable par capteur)
  "graine": 42,               # optionnelle : valeurs normales reproductibles
  "capteurs": [
    { "patient": "TN-0012345", "type": "FREQUENCE_CARDIAQUE",
      "normale": [65, 95],                       # optionnelle (défauts par type ci-dessous)
      "evenements": [ { "a": 5, "valeur": 165 } ] # valeur imposée à la mesure n°5 (1 = première)
    }
  ]
}
"""
import json
import logging
import random
import re
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path

import grpc
from google.protobuf.timestamp_pb2 import Timestamp

from .gen import telemetrie_pb2 as pb
from .gen import telemetrie_pb2_grpc as pb_grpc

log = logging.getLogger("scenario")

NORMALES = {"FREQUENCE_CARDIAQUE": (65, 95), "SPO2": (95, 99), "CHUTE": (0, 0)}
UNITES = {"FREQUENCE_CARDIAQUE": "bpm", "SPO2": "%", "CHUTE": ""}


@dataclass
class Capteur:
    patient: str
    type: str
    mesures: int
    intervalle: float
    normale: tuple[float, float]
    evenements: dict[int, float] = field(default_factory=dict)

    @property
    def nom(self) -> str:
        return f"capteur-{self.patient}-{self.type.lower()}"

    def serie(self, hasard: random.Random) -> list[float]:
        bas, haut = self.normale
        suite = [float(hasard.randint(int(bas), int(haut))) for _ in range(self.mesures)]
        for position, valeur in self.evenements.items():
            suite[position - 1] = float(valeur)
        return suite


@dataclass
class Scenario:
    description: str
    capteurs: list[Capteur]
    graine: int | None


def charger(fichier: Path) -> Scenario:
    """Lit et valide un scénario ; lève ValueError avec un message clair si le fichier est incorrect."""
    brut = json.loads(Path(fichier).read_text(encoding="utf-8"))
    intervalle = float(brut.get("intervalle", 1.0))
    mesures = int(brut.get("mesures", 10))
    capteurs = []
    for i, c in enumerate(brut.get("capteurs", []), start=1):
        if not re.fullmatch(r"TN-\d{7}", c.get("patient", "")):
            raise ValueError(f"capteur {i} : identifiant patient invalide « {c.get('patient')} »")
        if c.get("type") not in NORMALES:
            raise ValueError(f"capteur {i} : type inconnu « {c.get('type')} » (attendu : {', '.join(NORMALES)})")
        n = int(c.get("mesures", mesures))
        evenements = {}
        for e in c.get("evenements", []):
            if not 1 <= int(e["a"]) <= n:
                raise ValueError(f"capteur {i} : événement à la mesure {e['a']} hors de 1..{n}")
            evenements[int(e["a"])] = float(e["valeur"])
        capteurs.append(Capteur(patient=c["patient"], type=c["type"], mesures=n,
                                intervalle=float(c.get("intervalle", intervalle)),
                                normale=tuple(c.get("normale", NORMALES[c["type"]])), evenements=evenements))
    if not capteurs:
        raise ValueError("le scénario ne contient aucun capteur")
    return Scenario(description=brut.get("description", ""), capteurs=capteurs, graine=brut.get("graine"))


def _flux(capteur: Capteur, suite: list[float]):
    type_pb = pb.TypeMesure.Value(capteur.type)
    for i, valeur in enumerate(suite, start=1):
        horodatage = Timestamp()
        horodatage.GetCurrentTime()
        marque = "  <- événement" if i in capteur.evenements else ""
        log.info("[%s %s %d/%d] %g %s%s", capteur.patient, capteur.type, i, len(suite), valeur,
                 UNITES[capteur.type], marque)
        yield pb.Mesure(identifiant_patient=capteur.patient, identifiant_capteur=capteur.nom,
                        type=type_pb, valeur=valeur, horodatage=horodatage)
        if i < len(suite):
            time.sleep(capteur.intervalle)


def executer(scenario: Scenario, adresse: str) -> dict[str, tuple[int, int] | str]:
    """Lance tous les capteurs en parallèle ; renvoie {capteur: (mesures reçues, alertes) ou message d'erreur}."""
    hasard = random.Random(scenario.graine)
    series = {c.nom: c.serie(hasard) for c in scenario.capteurs}
    resultats: dict[str, tuple[int, int] | str] = {}
    with grpc.insecure_channel(adresse) as canal:
        grpc.channel_ready_future(canal).result(timeout=30)
        stub = pb_grpc.TelemetrieStub(canal)

        def emettre(c: Capteur):
            try:
                r = stub.EnvoyerMesures(_flux(c, series[c.nom]))
                resultats[c.nom] = (r.mesures_recues, r.alertes_declenchees)
            except grpc.RpcError as e:
                resultats[c.nom] = f"{e.code().name} {e.details()}"

        fils = [threading.Thread(target=emettre, args=(c,), name=c.nom) for c in scenario.capteurs]
        for f in fils:
            f.start()
        for f in fils:
            f.join()
    return resultats
