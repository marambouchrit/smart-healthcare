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

    def test_paliers_frequence_cardiaque(self):
        # P2.GRPC.2 : INFO > 100, URGENT > 120, CRITIQUE > 140 ; INFO < 60, URGENT < 50, CRITIQUE < 40 (limites exclues)
        attendu = {100: None, 101: "INFO", 120: "INFO", 121: "URGENT", 140: "URGENT", 141: "CRITIQUE",
                   60: None, 59: "INFO", 50: "INFO", 49: "URGENT", 40: "URGENT", 39: "CRITIQUE", 75: None}
        for valeur, n in attendu.items():
            self.assertEqual(niveau(pb.FREQUENCE_CARDIAQUE, valeur), n, f"FC {valeur}")

    def test_spo2(self):
        attendu = {84: "CRITIQUE", 85: "URGENT", 89: "URGENT", 90: "INFO", 93: "INFO", 94: None, 98: None}
        for valeur, n in attendu.items():
            self.assertEqual(niveau(pb.SPO2, valeur), n, f"SpO2 {valeur}")

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



class TestPriorisation(unittest.TestCase):
    """P2.GRPC.2 : file d'un abonné priorisée, et filtrage par niveau minimal."""

    def alerte(self, numero, niveau):
        return pb.Alerte(identifiant_alerte=f"ALR-{numero:06d}", identifiant_patient="TN-0012345", niveau=niveau)

    def test_critique_avant_urgent_avant_info_puis_ordre_d_arrivee(self):
        from telemetrie.serveur import Diffuseur
        d = Diffuseur()
        file = d.abonner(pb.AbonnementAlertes())
        for numero, n in [(1, pb.INFO), (2, pb.URGENT), (3, pb.CRITIQUE), (4, pb.INFO), (5, pb.CRITIQUE)]:
            d.publier(self.alerte(numero, n))
        ordre = [file.get(timeout=1).identifiant_alerte for _ in range(5)]
        self.assertEqual(ordre, ["ALR-000003", "ALR-000005", "ALR-000002", "ALR-000001", "ALR-000004"])

    def test_niveau_minimum_de_l_abonnement(self):
        from telemetrie.serveur import Diffuseur
        d = Diffuseur()
        urgent = d.abonner(pb.AbonnementAlertes(niveau_minimum=pb.URGENT))
        tout = d.abonner(pb.AbonnementAlertes())
        for numero, n in [(1, pb.INFO), (2, pb.URGENT), (3, pb.CRITIQUE)]:
            d.publier(self.alerte(numero, n))
        self.assertEqual(sorted(urgent.get(timeout=1).identifiant_alerte for _ in range(2)), ["ALR-000002", "ALR-000003"])
        with self.assertRaises(Exception):
            urgent.get(timeout=0.2)  # l'INFO n'a pas été transmise
        self.assertEqual(len([tout.get(timeout=1) for _ in range(3)]), 3)


class TestNiveauxReseau(unittest.TestCase):

    def test_trois_niveaux_produits_par_le_service(self):
        serveur, port = demarrer(port=0)
        try:
            with grpc.insecure_channel(f"localhost:{port}") as canal:
                stub = pb_grpc.TelemetrieStub(canal)
                recues, appel = [], stub.SuivreAlertes(pb.AbonnementAlertes())

                def lire():
                    try:
                        for a in appel:
                            recues.append(a)
                    except grpc.RpcError:
                        pass
                threading.Thread(target=lire, daemon=True).start()
                time.sleep(0.3)
                ts = Timestamp()
                ts.FromJsonString("2026-11-17T10:30:00Z")
                valeurs = [(pb.FREQUENCE_CARDIAQUE, 110), (pb.FREQUENCE_CARDIAQUE, 130), (pb.SPO2, 88), (pb.SPO2, 92)]
                stub.EnvoyerMesures(iter(pb.Mesure(identifiant_patient="TN-0012345", type=t, valeur=v, horodatage=ts)
                                         for t, v in valeurs))
                time.sleep(0.3)
                appel.cancel()
            self.assertEqual([(pb.Niveau.Name(a.niveau), a.seuil) for a in recues],
                             [("INFO", 100), ("URGENT", 120), ("URGENT", 90), ("INFO", 94)])
        finally:
            serveur.stop(grace=None)


if __name__ == "__main__":
    unittest.main()
