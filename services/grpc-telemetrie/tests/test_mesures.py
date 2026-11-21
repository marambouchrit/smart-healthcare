"""P2.GRPC.5 : flux SuivreMesures (server streaming), via le réseau."""
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
    ts.FromJsonString("2026-11-17T09:30:00Z")
    return pb.Mesure(identifiant_patient=patient, type=type_, valeur=valeur, horodatage=ts)


class TestSuivreMesures(unittest.TestCase):

    def setUp(self):
        self.serveur, port = demarrer(port=0)
        self.canal = grpc.insecure_channel(f"localhost:{port}")
        self.stub = pb_grpc.TelemetrieStub(self.canal)

    def tearDown(self):
        self.canal.close()
        self.serveur.stop(grace=None)

    def abonner(self, abonnement):
        recues, appel = [], self.stub.SuivreMesures(abonnement)

        def lire():
            try:
                for m in appel:
                    recues.append(m)
            except grpc.RpcError:
                pass
        threading.Thread(target=lire, daemon=True).start()
        time.sleep(0.3)
        return recues, appel

    def test_toutes_les_mesures_normales_et_critiques_sont_diffusees(self):
        recues, appel = self.abonner(pb.AbonnementMesures())
        self.stub.EnvoyerMesures(iter([mesure("TN-0012345", 80), mesure("TN-0034567", 96, pb.SPO2), mesure("TN-0012345", 165)]))
        time.sleep(0.3)
        appel.cancel()
        self.assertEqual([(m.identifiant_patient, m.valeur) for m in recues],
                         [("TN-0012345", 80), ("TN-0034567", 96), ("TN-0012345", 165)])

    def test_filtre_par_patient(self):
        lui, a1 = self.abonner(pb.AbonnementMesures(identifiant_patient="TN-0034567"))
        self.stub.EnvoyerMesures(iter([mesure("TN-0012345", 80), mesure("TN-0034567", 95, pb.SPO2)]))
        time.sleep(0.3)
        a1.cancel()
        self.assertEqual([m.identifiant_patient for m in lui], ["TN-0034567"])

    def test_mesure_refusee_non_diffusee(self):
        recues, appel = self.abonner(pb.AbonnementMesures())
        with self.assertRaises(grpc.RpcError):
            self.stub.EnvoyerMesures(iter([mesure("TN-9999999", 80)]))
        time.sleep(0.2)
        appel.cancel()
        self.assertEqual(recues, [])


if __name__ == "__main__":
    unittest.main()
