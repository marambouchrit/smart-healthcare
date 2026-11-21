"""Capteurs IoT virtuels : envoient en streaming des mesures au service gRPC de télémétrie.

Mode simple (P1.5) : un patient, fréquence cardiaque, une valeur critique injectée à une position donnée.
    python -m capteur.simulateur --patient TN-0012345 --mesures 10 --critique-a 5 --valeur-critique 165
Mode scénario (P2.GRPC.4) : plusieurs patients et types de mesure en parallèle, décrits dans un fichier JSON.
    python -m capteur.simulateur --scenario scenarios/demo-alertes.json

Variables d'environnement équivalentes (pour Docker) : TELEMETRIE_ADRESSE, SCENARIO, PATIENT, NB_MESURES,
INTERVALLE, CRITIQUE_A, VALEUR_CRITIQUE.
"""
import argparse
import logging
import os
import random
import time

import grpc
from google.protobuf.timestamp_pb2 import Timestamp

from . import scenario
from .gen import telemetrie_pb2 as pb
from .gen import telemetrie_pb2_grpc as pb_grpc

log = logging.getLogger("capteur")

FC_NORMALE = (65, 95)  # plage des valeurs « normales » simulées, en bpm


def valeurs(nb_mesures: int, critique_a: int | None, valeur_critique: float, graine: int | None = None) -> list[float]:
    """Suite de fréquences cardiaques : valeurs normales, et valeur critique à la position critique_a (1 = première)."""
    hasard = random.Random(graine)
    suite = [float(hasard.randint(*FC_NORMALE)) for _ in range(nb_mesures)]
    if critique_a is not None and 1 <= critique_a <= nb_mesures:
        suite[critique_a - 1] = float(valeur_critique)
    return suite


def flux_mesures(patient: str, suite: list[float], intervalle: float):
    capteur = f"capteur-{patient}-cardio"
    for i, valeur in enumerate(suite, start=1):
        horodatage = Timestamp()
        horodatage.GetCurrentTime()
        log.info("[%d/%d] %s FC=%g bpm", i, len(suite), patient, valeur)
        yield pb.Mesure(identifiant_patient=patient, identifiant_capteur=capteur,
                        type=pb.FREQUENCE_CARDIAQUE, valeur=valeur, horodatage=horodatage)
        if i < len(suite):
            time.sleep(intervalle)


def main(argv=None):
    env = os.environ.get
    p = argparse.ArgumentParser(description="Capteurs IoT virtuels : un patient (mode simple) ou un scénario multi-patients")
    p.add_argument("--adresse", default=env("TELEMETRIE_ADRESSE", "localhost:50051"), help="hôte:port du service gRPC")
    p.add_argument("--scenario", default=env("SCENARIO"),
                   help="fichier de scénario multi-patients (ex. scenarios/demo-alertes.json) ; ignore les options du mode simple")
    p.add_argument("--patient", default=env("PATIENT", "TN-0012345"), help="identifiant TN-xxxxxxx (contracts/seed)")
    p.add_argument("--mesures", type=int, default=int(env("NB_MESURES", "10")), help="nombre de mesures envoyées")
    p.add_argument("--intervalle", type=float, default=float(env("INTERVALLE", "1.0")), help="secondes entre deux mesures")
    p.add_argument("--critique-a", type=int, default=int(env("CRITIQUE_A", "5")),
                   help="position de la valeur critique (0 = aucune)")
    p.add_argument("--valeur-critique", type=float, default=float(env("VALEUR_CRITIQUE", "165")), help="valeur injectée, en bpm")
    p.add_argument("--graine", type=int, default=None, help="graine aléatoire (valeurs reproductibles)")
    a = p.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s : %(message)s")
    if a.scenario:
        return executer_scenario(a.scenario, a.adresse)
    suite = valeurs(a.mesures, a.critique_a or None, a.valeur_critique, a.graine)
    log.info("Connexion à %s, patient %s, %d mesures toutes les %gs, valeur critique %s",
             a.adresse, a.patient, a.mesures, a.intervalle,
             f"{a.valeur_critique:g} bpm en position {a.critique_a}" if a.critique_a else "aucune")

    with grpc.insecure_channel(a.adresse) as canal:
        grpc.channel_ready_future(canal).result(timeout=30)
        try:
            recap = pb_grpc.TelemetrieStub(canal).EnvoyerMesures(flux_mesures(a.patient, suite, a.intervalle))
        except grpc.RpcError as e:
            log.error("Refus du service : %s %s", e.code().name, e.details())
            return 1
    log.info("Récapitulatif du service : %d mesure(s) reçue(s), %d alerte(s) déclenchée(s)",
             recap.mesures_recues, recap.alertes_declenchees)
    return 0


def executer_scenario(fichier: str, adresse: str) -> int:
    try:
        s = scenario.charger(fichier)
    except (ValueError, OSError) as e:
        log.error("Scénario invalide (%s) : %s", fichier, e)
        return 2
    log.info("Scénario %s : %s (%d capteurs en parallèle)", fichier, s.description, len(s.capteurs))
    resultats = scenario.executer(s, adresse)
    erreurs = 0
    for nom, r in sorted(resultats.items()):
        if isinstance(r, str):
            erreurs += 1
            log.error("%s : refus du service — %s", nom, r)
        else:
            log.info("%s : %d mesure(s), %d alerte(s)", nom, *r)
    total = [r for r in resultats.values() if not isinstance(r, str)]
    log.info("Total : %d mesure(s), %d alerte(s), %d capteur(s) en erreur",
             sum(r[0] for r in total), sum(r[1] for r in total), erreurs)
    return 1 if erreurs else 0


if __name__ == "__main__":
    raise SystemExit(main())
