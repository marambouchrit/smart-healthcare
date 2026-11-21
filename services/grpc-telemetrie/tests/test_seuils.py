"""P2.GRPC.1 : seuils critiques (seuils.json) — règles seules puis via le réseau."""
import threading
import time
import unittest

import grpc
from google.protobuf.timestamp_pb2 import Timestamp

from telemetrie import seuils
from telemetrie.gen import telemetrie_pb2 as pb
from telemetrie.gen import telemetrie_pb2_grpc as pb_grpc
from telemetrie.serveur import demarrer

REGLES = seuils.charger()


def niveau(type_, valeur):
    r = seuils.evaluer(REGLES, type_, valeur)
    return None if r is None else pb.Niveau.Name(r.niveau)


class TestRegles(unittest.TestCase):

    def test_frequence_cardiaque_haute_et_basse(self):
        self.assertEqual(niveau(pb.FREQUENCE_CARDIAQUE, 141), "CRITIQUE")
        self.assertEqual(niveau(pb.FREQUENCE_CARDIAQUE, 39), "CRITIQUE")
        self.assertIsNone(niveau(pb.FREQUENCE_CARDIAQUE, 140))  # limite exclue
        self.assertIsNone(niveau(pb.FREQUENCE_CARDIAQUE, 40))

    def test_spo2(self):
        self.assertEqual(niveau(pb.SPO2, 84), "CRITIQUE")
        self.assertIsNone(niveau(pb.SPO2, 85))

    def test_chute(self):
        self.assertEqual(niveau(pb.CHUTE, 1), "CRITIQUE")
        self.assertIsNone(niveau(pb.CHUTE, 0))

    def test_messages(self):
        self.assertEqual(seuils.evaluer(REGLES, pb.FREQUENCE_CARDIAQUE, 35).message(35), "Fréquence cardiaque 35 bpm < 40 bpm")
        self.assertEqual(seuils.evaluer(REGLES, pb.SPO2, 82).message(82), "SpO2 82 % < 85 %")
        self.assertEqual(seuils.evaluer(REGLES, pb.CHUTE, 1).message(1), "Chute détectée")


class TestSeuilsReseau(unittest.TestCase):

    def setUp(self):
        self.serveur, port = demarrer(port=0)
        self.canal = grpc.insecure_channel(f"localhost:{port}")
        self.stub = pb_grpc.TelemetrieStub(self.canal)

    def tearDown(self):
        self.canal.close()
        self.serveur.stop(grace=None)

    def test_les_quatre_seuils_produisent_quatre_alertes(self):
        recues, appel = [], self.stub.SuivreAlertes(pb.AbonnementAlertes())

        def lire():
            try:
                for a in appel:
                    recues.append(a)
            except grpc.RpcError:
                pass  # annulation en fin de test
        threading.Thread(target=lire, daemon=True).start()
        time.sleep(0.3)
        ts = Timestamp()
        ts.FromJsonString("2026-11-17T10:30:00Z")
        mesures = [
            pb.Mesure(identifiant_patient="TN-0012345", type=pb.FREQUENCE_CARDIAQUE, valeur=165, horodatage=ts),
            pb.Mesure(identifiant_patient="TN-0023456", type=pb.FREQUENCE_CARDIAQUE, valeur=35, horodatage=ts),
            pb.Mesure(identifiant_patient="TN-0034567", type=pb.SPO2, valeur=82, horodatage=ts),
            pb.Mesure(identifiant_patient="TN-0056789", type=pb.CHUTE, valeur=1, horodatage=ts),
            pb.Mesure(identifiant_patient="TN-0056789", type=pb.CHUTE, valeur=0, horodatage=ts),
        ]
        recap = self.stub.EnvoyerMesures(iter(mesures))
        time.sleep(0.3)
        appel.cancel()
        self.assertEqual((recap.mesures_recues, recap.alertes_declenchees), (5, 4))
        self.assertEqual([(a.identifiant_patient, a.message) for a in recues], [
            ("TN-0012345", "Fréquence cardiaque 165 bpm > 140 bpm"),
            ("TN-0023456", "Fréquence cardiaque 35 bpm < 40 bpm"),
            ("TN-0034567", "SpO2 82 % < 85 %"),
            ("TN-0056789", "Chute détectée"),
        ])
        self.assertEqual([a.seuil for a in recues], [140, 40, 85, 1])


if __name__ == "__main__":
    unittest.main()
