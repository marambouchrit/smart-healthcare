"""P2.GRPC.3 : persistance des mesures et des alertes dans PostgreSQL.

Base : conteneur « postgres » de docker-compose.yml (docker compose up -d postgres). Chaque test travaille dans un
schéma temporaire supprimé à la fin : les données de développement ne sont pas touchées.
"""
import os
import threading
import time
import unittest
import uuid

import grpc
from google.protobuf.timestamp_pb2 import Timestamp

from telemetrie.depot import DepotPostgres
from telemetrie.gen import telemetrie_pb2 as pb
from telemetrie.gen import telemetrie_pb2_grpc as pb_grpc
from telemetrie.serveur import DB_URL_DEFAUT, demarrer

DSN = os.environ.get("TELEMETRIE_DB_URL_TEST", DB_URL_DEFAUT)


def base_disponible() -> bool:
    try:
        import psycopg
        psycopg.connect(DSN, connect_timeout=3).close()
        return True
    except Exception:
        return False


def mesure(patient, valeur, type_=pb.FREQUENCE_CARDIAQUE):
    ts = Timestamp()
    ts.FromJsonString("2026-11-17T09:30:00Z")
    return pb.Mesure(identifiant_patient=patient, identifiant_capteur=f"capteur-{patient}", type=type_, valeur=valeur,
                     horodatage=ts)


@unittest.skipUnless(base_disponible(), "PostgreSQL indisponible : lancer « docker compose up -d postgres »")
class TestDepotPostgres(unittest.TestCase):

    def setUp(self):
        self.schema = "test_" + uuid.uuid4().hex[:12]
        self.depot = DepotPostgres(DSN, schema=self.schema)

    def tearDown(self):
        self.depot.fermer()
        import psycopg
        with psycopg.connect(DSN, autocommit=True) as c:
            c.execute(f"DROP SCHEMA IF EXISTS {self.schema} CASCADE")

    def test_mesures_et_alerte_enregistrees_et_reliees(self):
        self.assertIsNone(self.depot.enregistrer(mesure("TN-0012345", 80)))
        alerte = pb.Alerte(identifiant_patient="TN-0012345", niveau=pb.CRITIQUE, type=pb.FREQUENCE_CARDIAQUE,
                           valeur=165, seuil=140, message="FC 165 > 140", horodatage=mesure("TN-0012345", 0).horodatage)
        enregistree = self.depot.enregistrer(mesure("TN-0012345", 165), alerte)
        self.assertEqual(enregistree.identifiant_alerte, "ALR-000001")
        self.assertEqual(self.depot.requete("SELECT count(*) FROM mesure")[0][0], 2)
        ligne = self.depot.requete("SELECT a.identifiant, a.niveau, m.valeur, m.capteur, m.horodatage::text "
                                   "FROM alerte a JOIN mesure m ON m.id = a.mesure_id")
        self.assertEqual(ligne[0][:4], ("ALR-000001", "CRITIQUE", 165.0, "capteur-TN-0012345"))
        self.assertTrue(ligne[0][4].startswith("2026-11-17"))

    def test_la_numerotation_continue_apres_un_redemarrage(self):
        a = pb.Alerte(identifiant_patient="TN-0012345", niveau=pb.URGENT, type=pb.FREQUENCE_CARDIAQUE, valeur=130, seuil=120,
                      message="m")
        self.assertEqual(self.depot.enregistrer(mesure("TN-0012345", 130), a).identifiant_alerte, "ALR-000001")
        self.depot.fermer()
        self.depot = DepotPostgres(DSN, schema=self.schema)  # « redémarrage » : nouvelle connexion, même base
        b = pb.Alerte(identifiant_patient="TN-0012345", niveau=pb.URGENT, type=pb.FREQUENCE_CARDIAQUE, valeur=131, seuil=120,
                      message="m")
        self.assertEqual(self.depot.enregistrer(mesure("TN-0012345", 131), b).identifiant_alerte, "ALR-000002")

    def test_reconnexion_apres_une_coupure_de_la_base(self):
        """Comme un redémarrage de PostgreSQL : la session du dépôt est tuée côté serveur, l'écriture suivante réussit."""
        import psycopg
        self.depot.enregistrer(mesure("TN-0012345", 80))
        pid = self.depot.requete("SELECT pg_backend_pid()")[0][0]
        with psycopg.connect(DSN, autocommit=True) as autre:
            self.assertTrue(autre.execute("SELECT pg_terminate_backend(%s)", (pid,)).fetchone()[0])
        alerte = pb.Alerte(identifiant_patient="TN-0012345", niveau=pb.CRITIQUE, type=pb.FREQUENCE_CARDIAQUE,
                           valeur=170, seuil=140, message="FC 170 > 140")
        with self.assertLogs("telemetrie", "WARNING") as journaux:
            self.assertEqual(self.depot.enregistrer(mesure("TN-0012345", 170), alerte).identifiant_alerte, "ALR-000001")
        self.assertIn("reconnexion", journaux.output[0])
        # nouvelle session, toujours dans le même schéma, sans doublon
        self.assertNotEqual(self.depot.requete("SELECT pg_backend_pid()")[0][0], pid)
        self.assertEqual(self.depot.requete("SELECT count(*) FROM mesure")[0][0], 2)
        self.assertEqual(self.depot.requete("SELECT count(*) FROM alerte")[0][0], 1)

    def test_le_service_enregistre_tout_le_flux(self):
        serveur, port = demarrer(port=0, depot=self.depot)
        try:
            with grpc.insecure_channel(f"localhost:{port}") as canal:
                stub = pb_grpc.TelemetrieStub(canal)
                recues, appel = [], stub.SuivreAlertes(pb.AbonnementAlertes())

                def lire():
                    try:
                        for x in appel:
                            recues.append(x)
                    except grpc.RpcError:
                        pass
                threading.Thread(target=lire, daemon=True).start()
                time.sleep(0.3)
                recap = stub.EnvoyerMesures(iter([mesure("TN-0012345", 78), mesure("TN-0012345", 160),
                                                  mesure("TN-0034567", 83, pb.SPO2), mesure("TN-0056789", 0, pb.CHUTE)]))
                time.sleep(0.3)
                appel.cancel()
        finally:
            serveur.stop(grace=None)
        self.assertEqual((recap.mesures_recues, recap.alertes_declenchees), (4, 2))
        self.assertEqual(self.depot.requete("SELECT count(*) FROM mesure")[0][0], 4)
        self.assertEqual([r[0] for r in self.depot.requete("SELECT identifiant FROM alerte ORDER BY numero")],
                         [a.identifiant_alerte for a in recues])
        self.assertEqual(self.depot.requete("SELECT type, count(*) FROM mesure GROUP BY type ORDER BY type"),
                         [("CHUTE", 1), ("FREQUENCE_CARDIAQUE", 2), ("SPO2", 1)])

    def test_mesure_d_un_patient_au_format_invalide_refusee_par_la_base(self):
        import psycopg
        with self.assertRaises(psycopg.errors.CheckViolation):
            self.depot.enregistrer(mesure("TN-12", 80))


if __name__ == "__main__":
    unittest.main()
