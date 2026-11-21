"""P2.GRPC.4 : chargement et validation des scénarios, génération des séries (sans réseau)."""
import json
import random
import tempfile
import unittest
from pathlib import Path

from capteur import scenario

ICI = Path(__file__).resolve().parent.parent / "scenarios"


def ecrire(contenu: dict) -> Path:
    f = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8")
    json.dump(contenu, f)
    f.close()
    return Path(f.name)


class TestScenarios(unittest.TestCase):

    def test_les_scenarios_fournis_sont_valides(self):
        for fichier in sorted(ICI.glob("*.json")):
            with self.subTest(fichier=fichier.name):
                s = scenario.charger(fichier)
                self.assertTrue(s.capteurs)

    def test_scenario_normal_8_patients_2_mesures_sans_evenement(self):
        s = scenario.charger(ICI / "normal.json")
        self.assertEqual(len({c.patient for c in s.capteurs}), 8)
        self.assertEqual({c.type for c in s.capteurs}, {"FREQUENCE_CARDIAQUE", "SPO2"})
        self.assertTrue(all(not c.evenements for c in s.capteurs))

    def test_valeurs_par_defaut_et_surcharges(self):
        s = scenario.charger(ICI / "demo-alertes.json")
        fc = s.capteurs[0]
        self.assertEqual((fc.mesures, fc.intervalle, fc.normale), (10, 1.0, (65, 95)))
        rapide = next(c for c in s.capteurs if c.patient == "TN-0023456")
        self.assertEqual((rapide.mesures, rapide.intervalle), (20, 0.5))
        self.assertEqual(next(c for c in s.capteurs if c.type == "SPO2").normale, (95, 99))

    def test_series_avec_evenements_aux_bonnes_positions(self):
        s = scenario.charger(ICI / "demo-alertes.json")
        spo2 = next(c for c in s.capteurs if c.type == "SPO2")
        serie = spo2.serie(random.Random(1))
        self.assertEqual((serie[3], serie[5], serie[7]), (91, 88, 82))
        autres = [v for i, v in enumerate(serie, start=1) if i not in spo2.evenements]
        self.assertTrue(all(95 <= v <= 99 for v in autres))
        chute = next(c for c in s.capteurs if c.type == "CHUTE").serie(random.Random(1))
        self.assertEqual(chute, [0, 0, 0, 0, 0, 0, 1, 0, 0, 0])

    def test_erreurs_de_scenario(self):
        cas = {
            "identifiant patient invalide": {"capteurs": [{"patient": "TN-12", "type": "SPO2"}]},
            "type inconnu": {"capteurs": [{"patient": "TN-0012345", "type": "TENSION"}]},
            "hors de 1..3": {"mesures": 3, "capteurs": [{"patient": "TN-0012345", "type": "SPO2", "evenements": [{"a": 5, "valeur": 80}]}]},
            "aucun capteur": {"capteurs": []},
        }
        for attendu, contenu in cas.items():
            with self.subTest(attendu), self.assertRaisesRegex(ValueError, attendu):
                scenario.charger(ecrire(contenu))


if __name__ == "__main__":
    unittest.main()
