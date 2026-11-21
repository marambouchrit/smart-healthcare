"""Règles de détection des seuils (P2.GRPC.1, D32), lues dans seuils.json.

Une règle : type de mesure, sens (haut / bas / egal), seuil, niveau d'alerte, libellé.
Pour une mesure, toutes les règles franchies sont considérées et la plus grave l'emporte (une alerte par mesure).
"""
import json
from dataclasses import dataclass
from pathlib import Path

from .gen import telemetrie_pb2 as pb

FICHIER_DEFAUT = Path(__file__).resolve().parent.parent / "seuils.json"


@dataclass(frozen=True)
class Regle:
    type: int        # pb.TypeMesure
    sens: str        # "haut" | "bas" | "egal"
    seuil: float
    niveau: int      # pb.Niveau
    libelle: str

    def franchie(self, valeur: float) -> bool:
        if self.sens == "haut":
            return valeur > self.seuil
        if self.sens == "bas":
            return valeur < self.seuil
        return valeur == self.seuil

    def message(self, valeur: float) -> str:
        return self.libelle.format(valeur=f"{valeur:g}", seuil=f"{self.seuil:g}")


def charger(fichier: Path = FICHIER_DEFAUT) -> list[Regle]:
    with open(fichier, encoding="utf-8") as f:
        brut = json.load(f)["regles"]
    regles = []
    for r in brut:
        if r["sens"] not in ("haut", "bas", "egal"):
            raise ValueError(f"sens invalide : {r['sens']}")
        regles.append(Regle(type=pb.TypeMesure.Value(r["type"]), sens=r["sens"], seuil=float(r["seuil"]),
                            niveau=pb.Niveau.Value(r["niveau"]), libelle=r["libelle"]))
    return regles


def evaluer(regles: list[Regle], type_mesure: int, valeur: float) -> Regle | None:
    """Règle franchie la plus grave pour cette mesure, ou None."""
    franchies = [r for r in regles if r.type == type_mesure and r.franchie(valeur)]
    return max(franchies, key=lambda r: r.niveau, default=None)
