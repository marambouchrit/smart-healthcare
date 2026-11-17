"""Tests de la génération des mesures (sans réseau). Usage : python -m unittest discover -s tests -t . -v"""
import unittest

from capteur.gen import telemetrie_pb2 as pb
from capteur.simulateur import FC_NORMALE, flux_mesures, valeurs


class TestSimulateur(unittest.TestCase):

    def test_valeur_critique_injectee_a_la_bonne_position(self):
        suite = valeurs(10, 5, 165, graine=1)
        self.assertEqual(len(suite), 10)
        self.assertEqual(suite[4], 165)
        autres = suite[:4] + suite[5:]
        self.assertTrue(all(FC_NORMALE[0] <= v <= FC_NORMALE[1] for v in autres))

    def test_sans_valeur_critique(self):
        suite = valeurs(6, None, 165, graine=1)
        self.assertTrue(all(v <= FC_NORMALE[1] for v in suite))

    def test_position_hors_flux_ignoree(self):
        self.assertNotIn(165, valeurs(3, 7, 165, graine=1))

    def test_graine_reproductible(self):
        self.assertEqual(valeurs(8, 2, 170, graine=42), valeurs(8, 2, 170, graine=42))

    def test_messages_conformes_au_contrat(self):
        mesures = list(flux_mesures("TN-0012345", [80.0, 165.0], intervalle=0))
        self.assertEqual(len(mesures), 2)
        m = mesures[1]
        self.assertEqual(m.identifiant_patient, "TN-0012345")
        self.assertEqual(m.type, pb.FREQUENCE_CARDIAQUE)
        self.assertEqual(m.valeur, 165.0)
        self.assertGreater(m.horodatage.seconds, 0)


if __name__ == "__main__":
    unittest.main()
