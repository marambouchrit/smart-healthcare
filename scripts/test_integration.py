"""Test d'intégration du scénario d'alerte complet (P5.4), contre la stack Docker, uniquement par la gateway.

Pour chaque patient testé :
  1. connexion à la gateway (POST /auth/login, compte « medecin ») ;
  2. abonnement au flux SSE /events (contracts/sse) ;
  3. vraie alerte : le simulateur IoT (conteneur) envoie 3 mesures dont une fréquence cardiaque critique (gRPC) ;
  4. le flux doit recevoir l'événement « alerte » puis « prise-en-charge » (TERMINE ou AMBULANCE) ;
  5. vérification croisée dans chaque service :
     - GraphQL : l'alerte et la fiche d'urgence (action, statut à régulariser) dans le dossier du patient ;
     - REST    : le rendez-vous d'urgence (TERMINE) ;
     - SOAP    : les droits CNAM cohérents avec « à régulariser » ;
     - trace   : l'instance de workflow et ses étapes (GET /orchestrator/workflows, D54).

Patients par défaut (contracts/seed) : TN-0034567 (couvert) et TN-0089012 (non affilié, P5.1).
Selon les créneaux d'urgence restants du jour, le résultat est un rendez-vous (TERMINE) ou une ambulance (P5.2) :
les deux sont acceptés et vérifiés.

Prérequis : `docker compose up -d` (stack complète). Bibliothèque standard Python uniquement.
Usage : python scripts/test_integration.py [--gateway http://localhost:8090] [--patients TN-0034567 TN-0089012]
Code de sortie 0 si tout est conforme, 1 sinon. Les données créées (alertes, rendez-vous, fiches) restent en base.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
NS = "http://hopital.tn/dossier"
ETAPES_RDV = ["ALERTE_RECUE", "DROITS_CNAM", "CRENEAU_URGENCE", "ALERTE_TABLEAU_DE_BORD", "COUVERTURE", "FICHE_URGENCE"]
ETAPES_AMBULANCE = ["ALERTE_RECUE", "DROITS_CNAM", "CRENEAU_URGENCE", "AMBULANCE", "ALERTE_TABLEAU_DE_BORD",
                    "COUVERTURE", "FICHE_URGENCE"]

echecs = []


def verifier(condition, libelle, detail=""):
    print(("  OK    " if condition else "  ÉCHEC ") + libelle + (f" — {detail}" if detail else ""))
    if not condition:
        echecs.append(libelle)
    return condition


def port_gateway():
    if os.environ.get("GATEWAY_PORT"):
        return os.environ["GATEWAY_PORT"]
    env = RACINE / ".env"
    if env.exists():
        for ligne in env.read_text(encoding="utf-8").splitlines():
            m = re.match(r"\s*GATEWAY_PORT\s*=\s*(\d+)", ligne)
            if m:
                return m.group(1)
    return "8080"


class Gateway:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.jeton = None

    def requete(self, methode, chemin, corps=None, entetes=None, brut=False):
        donnees = None
        h = dict(entetes or {})
        if self.jeton:
            h["Authorization"] = f"Bearer {self.jeton}"
        if corps is not None and not brut:
            donnees = json.dumps(corps).encode("utf-8")
            h.setdefault("Content-Type", "application/json")
        elif corps is not None:
            donnees = corps.encode("utf-8")
        req = urllib.request.Request(self.base + chemin, data=donnees, method=methode, headers=h)
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                texte = r.read().decode("utf-8")
                return r.status, texte
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8")

    def json(self, methode, chemin, corps=None):
        statut, texte = self.requete(methode, chemin, corps)
        return statut, (json.loads(texte) if texte else None)

    def connecter(self, identifiant, mot_de_passe):
        statut, r = self.json("POST", "/auth/login", {"identifiant": identifiant, "motDePasse": mot_de_passe})
        if statut == 200:
            self.jeton = r["jeton"]
        return statut


class FluxSse(threading.Thread):
    """Lit /events en continu et garde chaque événement (nom, données JSON)."""

    def __init__(self, base, jeton):
        super().__init__(daemon=True)
        self.url = f"{base}/events?access_token={jeton}"
        self.evenements = []
        self.connecte = threading.Event()
        self.erreur = None

    def run(self):
        try:
            with urllib.request.urlopen(self.url, timeout=120) as r:
                nom = None
                for brut in r:
                    ligne = brut.decode("utf-8").rstrip("\r\n")
                    if ligne.startswith(":connecte"):
                        self.connecte.set()
                    elif ligne.startswith("event:"):
                        nom = ligne[6:].strip()
                    elif ligne.startswith("data:") and nom:
                        self.evenements.append((nom, json.loads(ligne[5:])))
                        nom = None
        except Exception as e:  # fin du test ou coupure : signalée seulement si rien n'a été reçu
            self.erreur = e
            self.connecte.set()

    def attendre(self, predicat, delai):
        fin = time.time() + delai
        while time.time() < fin:
            for nom, donnees in list(self.evenements):
                if predicat(nom, donnees):
                    return donnees
            time.sleep(0.2)
        return None


def simuler_alerte(patient):
    commande = ["docker", "compose", "run", "--rm", "-e", "SCENARIO=", "iot-simulator", "python", "-m",
                "capteur.simulateur", "--patient", patient, "--mesures", "3", "--critique-a", "2", "--intervalle", "0.5"]
    r = subprocess.run(commande, cwd=RACINE, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
    recap = [l for l in (r.stdout + r.stderr).splitlines() if "Récapitulatif" in l]
    return r.returncode, (recap[-1].split("capteur : ")[-1] if recap else (r.stdout + r.stderr)[-300:])


def droits_soap(gw, patient):
    enveloppe = (f'<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:d="{NS}">'
                 f"<soapenv:Body><d:verifierDroitsCNAM><d:identifiantPatient>{patient}</d:identifiantPatient>"
                 f"<d:lettreCle>C</d:lettreCle><d:datePrestation>{time.strftime('%Y-%m-%d')}</d:datePrestation>"
                 f"</d:verifierDroitsCNAM></soapenv:Body></soapenv:Envelope>")
    statut, texte = gw.requete("POST", "/ws/dossier", enveloppe, {
        "Content-Type": "text/xml; charset=utf-8", "SOAPAction": f'"{NS}/verifierDroitsCNAM"'}, brut=True)
    m = re.search(r"<statut>([A-Z_]+)</statut>", texte)
    return statut, (m.group(1) if m else None)


def scenario(gw, patient):
    print(f"\n=== Scénario d'alerte : {patient}")
    flux = FluxSse(gw.base, gw.jeton)
    flux.start()
    verifier(flux.connecte.wait(15) and flux.erreur is None, "flux SSE ouvert par la gateway", str(flux.erreur or ""))

    code, recap = simuler_alerte(patient)
    verifier(code == 0 and "1 alerte" in recap, "capteur IoT → service gRPC : alerte déclenchée", recap)

    alerte = flux.attendre(lambda n, d: n == "alerte" and d["patientId"] == patient, 30)
    if not verifier(alerte is not None, "SSE « alerte » reçu"):
        return
    verifier(alerte["niveau"] == "CRITIQUE" and alerte["type"] == "FREQUENCE_CARDIAQUE",
             "alerte CRITIQUE fréquence cardiaque", f"{alerte['id']} {alerte['message']}")
    pec = flux.attendre(lambda n, d: n == "prise-en-charge" and d["alerteId"] == alerte["id"], 60)
    if not verifier(pec is not None, "SSE « prise-en-charge » reçu"):
        return
    etat = pec["etat"]
    verifier(etat in ("TERMINE", "AMBULANCE"), "workflow abouti", f"état {etat}, fiche {pec['ficheId']}, "
             f"rendez-vous {pec['rendezVousId']}, ambulance {pec['interventionId']}, à régulariser {pec['aRegulariser']}")

    # SOAP : droits cohérents avec « à régulariser »
    statut, droits = droits_soap(gw, patient)
    verifier(statut == 200 and droits is not None, "SOAP verifierDroitsCNAM", f"{droits}")
    verifier(pec["aRegulariser"] == (droits != "OUVERTS"), "« à régulariser » cohérent avec les droits CNAM",
             f"droits {droits}, aRegulariser {pec['aRegulariser']}")

    # REST : rendez-vous d'urgence ou ambulance
    if etat == "TERMINE":
        statut, rdv = gw.json("GET", f"/api/rendez-vous/{pec['rendezVousId']}")
        verifier(statut == 200 and rdv["patientId"] == patient and rdv["urgence"] and rdv["statut"] == "CONFIRME"
                 and rdv["creneau"]["serviceCode"] == "URGENCES", "REST rendez-vous d'urgence confirmé",
                 f"{rdv and rdv['id']} {rdv and rdv['creneau']['debut']}")
        verifier(("à régulariser" in rdv["motif"]) == pec["aRegulariser"], "REST motif « à régulariser » (P5.1)", rdv["motif"])
    else:
        verifier(re.fullmatch(r"AMB-\d{6}", pec["interventionId"] or "") is not None and pec["arriveeEstimee"],
                 "REST ambulance envoyée (P5.2)", f"{pec['interventionId']} arrivée {pec['arriveeEstimee']}")

    # GraphQL : dossier consolidé
    requete = ("query($id: ID!) { patient(id: $id) { alertesRecentes(limite: 5) { id niveau } "
               "fichesUrgence(limite: 5) { id action statutPriseEnCharge identifiantRendezVous alerte { id } } "
               "couverture { statut } } }")
    statut, r = gw.json("POST", "/graphql", {"query": requete, "variables": {"id": patient}})
    p = (r or {}).get("data", {}).get("patient") if statut == 200 else None
    if verifier(p is not None, "GraphQL dossier du patient lu"):
        verifier(any(a["id"] == alerte["id"] for a in p["alertesRecentes"]), "GraphQL alerte enregistrée", alerte["id"])
        fiche = next((f for f in p["fichesUrgence"] if f["id"] == pec["ficheId"]), None)
        attendu = "CONSULTATION_URGENCE" if etat == "TERMINE" else "AMBULANCE"
        statut_attendu = "A_REGULARISER" if pec["aRegulariser"] else "PRISE_EN_CHARGE"
        verifier(fiche is not None and fiche["alerte"]["id"] == alerte["id"] and fiche["action"] == attendu
                 and fiche["statutPriseEnCharge"] == statut_attendu
                 and fiche["identifiantRendezVous"] == pec["rendezVousId"],
                 "GraphQL fiche d'urgence liée à l'alerte", f"{fiche}")
        verifier(p["couverture"] is not None and p["couverture"]["statut"] == droits, "GraphQL couverture mise à jour",
                 f"{p['couverture']}")

    # Trace du workflow (P5.3)
    statut, liste = gw.json("GET", f"/orchestrator/workflows?patientId={patient}&limite=5")
    wf = next((w for w in (liste or []) if w["alerteId"] == alerte["id"]), None)
    if verifier(statut == 200 and wf is not None, "trace : instance du workflow trouvée", wf and wf["id"]):
        statut, detail = gw.json("GET", f"/orchestrator/workflows/{wf['id']}")
        noms = [e["nom"] for e in detail["etapes"]]
        verifier(detail["etat"] == etat and detail["ficheId"] == pec["ficheId"], "trace : état et fiche identiques au SSE",
                 f"{detail['etat']} en {detail['dureeMs']} ms")
        verifier(noms == (ETAPES_RDV if etat == "TERMINE" else ETAPES_AMBULANCE), "trace : étapes dans l'ordre", " → ".join(noms))
        verifier(all(e["statut"] in ("OK", "REFUS") for e in detail["etapes"]), "trace : aucune étape en échec",
                 ", ".join(f"{e['nom']}×{e['tentatives']} {e['statut']}" for e in detail["etapes"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--gateway", default=f"http://localhost:{port_gateway()}")
    parser.add_argument("--patients", nargs="+", default=["TN-0034567", "TN-0089012"])
    args = parser.parse_args()

    gw = Gateway(args.gateway)
    print(f"Gateway : {gw.base}")
    verifier(gw.requete("GET", "/api/creneaux")[0] == 401, "sans jeton : 401 (seule porte d'entrée sécurisée)")
    if not verifier(gw.connecter("medecin", "medecin_dev") == 200, "connexion « medecin » (JWT)"):
        return 1
    for patient in args.patients:
        scenario(gw, patient)

    print(f"\n{'RÉSULTAT : CONFORME' if not echecs else f'RÉSULTAT : {len(echecs)} contrôle(s) en échec'}")
    for e in echecs:
        print(f"  - {e}")
    return 0 if not echecs else 1


if __name__ == "__main__":
    sys.exit(main())
