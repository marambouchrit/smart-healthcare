"""Tests du service gRPC via de vrais appels réseau (serveur démarré sur un port libre).

Usage : python -m unittest discover -s tests -v   (après python generer.py)
"""
import threading
import time
import unittest

import grpc
from google.protobuf.timestamp_pb2 import Timestamp

from telemetrie.gen import telemetrie_pb2 as pb
from telemetrie.gen import telemetrie_pb2_grpc as pb_grpc
from telemetrie.serveur import demarrer


def mesure(patient, valeur, type_=pb.FREQUENCE_CARDIAQUE):
    ts = Timestamp()
    ts.FromJsonString("2026-11-17T10:30:00Z")
    return pb.Mesure(identifiant_patient=patient, identifiant_capteur=f"capteur-{patient}",
                     type=type_, valeur=valeur, horodatage=ts)


class TestTelemetrie(unittest.TestCase):

    def setUp(self):
        self.serveur, port = demarrer(port=0)
        self.canal = grpc.insecure_channel(f"localhost:{port}")
        self.stub = pb_grpc.TelemetrieStub(self.canal)

    def tearDown(self):
        self.canal.close()
        self.serveur.stop(grace=None)

    def abonner(self, abonnement):
        """Lance SuivreAlertes dans un thread ; retourne (liste des alertes reçues, appel en cours)."""
        recues, appel = [], self.stub.SuivreAlertes(abonnement)

        def lire():
            try:
                for a in appel:
                    recues.append(a)
            except grpc.RpcError:
                pass  # annulation en fin de test
        threading.Thread(target=lire, daemon=True).start()
        time.sleep(0.3)  # laisse le temps à l'abonnement d'être enregistré côté serveur
        return recues, appel

    def test_valeur_normale_aucune_alerte(self):
        recap = self.stub.EnvoyerMesures(iter([mesure("TN-0012345", 80), mesure("TN-0012345", 140)]))
        self.assertEqual(recap.mesures_recues, 2)
        self.assertEqual(recap.alertes_declenchees, 0)

    def test_valeur_critique_diffusee_aux_abonnes(self):
        recues, appel = self.abonner(pb.AbonnementAlertes())
        recap = self.stub.EnvoyerMesures(iter([mesure("TN-0012345", 82), mesure("TN-0012345", 165)]))
        time.sleep(0.3)
        appel.cancel()
        self.assertEqual((recap.mesures_recues, recap.alertes_declenchees), (2, 1))
        self.assertEqual(len(recues), 1)
        a = recues[0]
        self.assertEqual(a.identifiant_patient, "TN-0012345")
        self.assertEqual(a.niveau, pb.CRITIQUE)
        self.assertEqual(a.type, pb.FREQUENCE_CARDIAQUE)
        self.assertEqual((a.valeur, a.seuil), (165, 140))
        self.assertRegex(a.identifiant_alerte, r"^ALR-\d{6}$")
        self.assertEqual(a.horodatage.ToJsonString(), "2026-11-17T10:30:00Z")

    def test_filtre_par_patient(self):
        autre, appel_autre = self.abonner(pb.AbonnementAlertes(identifiant_patient="TN-0023456"))
        lui, appel_lui = self.abonner(pb.AbonnementAlertes(identifiant_patient="TN-0012345"))
        self.stub.EnvoyerMesures(iter([mesure("TN-0012345", 170)]))
        time.sleep(0.3)
        appel_autre.cancel()
        appel_lui.cancel()
        self.assertEqual(len(lui), 1)
        self.assertEqual(len(autre), 0)

    def test_valeurs_normales_des_autres_types_sans_alerte(self):
        recap = self.stub.EnvoyerMesures(iter([mesure("TN-0056789", 0, pb.CHUTE), mesure("TN-0034567", 96, pb.SPO2)]))
        self.assertEqual((recap.mesures_recues, recap.alertes_declenchees), (2, 0))

    def test_patient_inconnu_refuse(self):
        with self.assertRaises(grpc.RpcError) as erreur:
            self.stub.EnvoyerMesures(iter([mesure("TN-9999999", 170)]))
        self.assertEqual(erreur.exception.code(), grpc.StatusCode.NOT_FOUND)


if __name__ == "__main__":
    unittest.main()
