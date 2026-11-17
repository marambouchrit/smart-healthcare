"""Service gRPC « Télémétrie médicale & alertes temps réel » (contrat : contracts/grpc/telemetrie.proto).

Phase 1 (P1.4) : un seul seuil, fréquence cardiaque > TELEMETRIE_SEUIL_FC (défaut 140 bpm) → alerte CRITIQUE.
Seuils SpO2 / chute, niveaux et persistance : phase 2 (P2.GRPC.1 à 3).

Lancement : python -m telemetrie.serveur
Variables : TELEMETRIE_PORT (50051), TELEMETRIE_SEUIL_FC (140), SEED_DIR (../../contracts/seed).
"""
import itertools
import json
import logging
import os
import queue
import threading
from concurrent import futures
from pathlib import Path

import grpc

from .gen import telemetrie_pb2 as pb
from .gen import telemetrie_pb2_grpc as pb_grpc

log = logging.getLogger("telemetrie")

ICI = Path(__file__).resolve().parent
SEED_DIR_DEFAUT = ICI.parent.parent.parent / "contracts" / "seed"


def charger_patients(seed_dir: Path) -> set[str]:
    with open(seed_dir / "patients.json", encoding="utf-8") as f:
        return {p["id"] for p in json.load(f)}


class Diffuseur:
    """Distribue chaque alerte à tous les abonnés de SuivreAlertes (une file par abonné)."""

    def __init__(self):
        self._verrou = threading.Lock()
        self._abonnes: list[tuple[queue.Queue, pb.AbonnementAlertes]] = []

    def abonner(self, abonnement):
        file = queue.Queue()
        with self._verrou:
            self._abonnes.append((file, abonnement))
        return file

    def desabonner(self, file):
        with self._verrou:
            self._abonnes = [(f, a) for f, a in self._abonnes if f is not file]

    def publier(self, alerte):
        with self._verrou:
            abonnes = list(self._abonnes)
        for file, abonnement in abonnes:
            if abonnement.niveau_minimum and alerte.niveau < abonnement.niveau_minimum:
                continue
            if abonnement.identifiant_patient and alerte.identifiant_patient != abonnement.identifiant_patient:
                continue
            file.put(alerte)

    def nombre_abonnes(self):
        with self._verrou:
            return len(self._abonnes)


class TelemetrieService(pb_grpc.TelemetrieServicer):

    def __init__(self, patients: set[str], seuil_fc: float, diffuseur: Diffuseur):
        self.patients = patients
        self.seuil_fc = seuil_fc
        self.diffuseur = diffuseur
        self._compteur = itertools.count(1)

    def detecter(self, mesure):
        """Retourne une Alerte si la mesure franchit un seuil, sinon None."""
        if mesure.type == pb.FREQUENCE_CARDIAQUE and mesure.valeur > self.seuil_fc:
            return pb.Alerte(
                identifiant_alerte=f"ALR-{next(self._compteur):06d}",
                identifiant_patient=mesure.identifiant_patient,
                niveau=pb.CRITIQUE,
                type=mesure.type,
                valeur=mesure.valeur,
                seuil=self.seuil_fc,
                message=f"Fréquence cardiaque {mesure.valeur:g} bpm > {self.seuil_fc:g} bpm",
                horodatage=mesure.horodatage,
            )
        return None

    def EnvoyerMesures(self, request_iterator, context):
        recues = alertes = 0
        for mesure in request_iterator:
            if mesure.identifiant_patient not in self.patients:
                context.abort(grpc.StatusCode.NOT_FOUND, f"Patient inconnu : {mesure.identifiant_patient}")
            recues += 1
            alerte = self.detecter(mesure)
            log.info("Mesure patient=%s type=%s valeur=%g%s", mesure.identifiant_patient,
                     pb.TypeMesure.Name(mesure.type), mesure.valeur, " -> ALERTE" if alerte else "")
            if alerte:
                alertes += 1
                log.warning("Alerte %s patient=%s %s : %s (%d abonné(s))", alerte.identifiant_alerte,
                            alerte.identifiant_patient, pb.Niveau.Name(alerte.niveau), alerte.message,
                            self.diffuseur.nombre_abonnes())
                self.diffuseur.publier(alerte)
        log.info("Fin du flux : %d mesure(s), %d alerte(s)", recues, alertes)
        return pb.RecapitulatifEnvoi(mesures_recues=recues, alertes_declenchees=alertes)

    def SuivreAlertes(self, request, context):
        file = self.diffuseur.abonner(request)
        log.info("Nouvel abonné aux alertes (niveau_min=%s, patient=%s)",
                 pb.Niveau.Name(request.niveau_minimum), request.identifiant_patient or "tous")
        try:
            while context.is_active():
                try:
                    yield file.get(timeout=0.5)
                except queue.Empty:
                    continue
        finally:
            self.diffuseur.desabonner(file)
            log.info("Abonné déconnecté")


def demarrer(port: int = 50051, seuil_fc: float = 140.0, seed_dir: Path = SEED_DIR_DEFAUT):
    """Démarre le serveur et le retourne (port 0 = port libre choisi par le système)."""
    service = TelemetrieService(charger_patients(seed_dir), seuil_fc, Diffuseur())
    serveur = grpc.server(futures.ThreadPoolExecutor(max_workers=16))
    pb_grpc.add_TelemetrieServicer_to_server(service, serveur)
    port_reel = serveur.add_insecure_port(f"[::]:{port}")
    serveur.start()
    log.info("Service Télémétrie gRPC sur le port %d (seuil FC > %g bpm, %d patients)",
             port_reel, seuil_fc, len(service.patients))
    return serveur, port_reel


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s : %(message)s")
    srv, _ = demarrer(
        port=int(os.environ.get("TELEMETRIE_PORT", "50051")),
        seuil_fc=float(os.environ.get("TELEMETRIE_SEUIL_FC", "140")),
        seed_dir=Path(os.environ.get("SEED_DIR", SEED_DIR_DEFAUT)),
    )
    srv.wait_for_termination()
