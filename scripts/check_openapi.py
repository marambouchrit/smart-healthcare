# Valide contracts/rest/portail-openapi.yaml (OpenAPI 3) et vérifie sa cohérence avec contracts/seed/.
# Prérequis (outil de vérification) : pip install openapi-spec-validator==0.9.0
# Usage : python scripts/check_openapi.py [fichier.yaml]   (par défaut : le contrat du projet)
import json, sys
from pathlib import Path
import yaml
from openapi_spec_validator import validate
from openapi_schema_validator import OAS30Validator
from jsonschema import FormatChecker

racine = Path(__file__).resolve().parent.parent
fichier = Path(sys.argv[1]) if len(sys.argv) > 1 else racine / "contracts/rest/portail-openapi.yaml"
spec = yaml.safe_load(fichier.read_text(encoding="utf-8"))

try:
    validate(spec)
except Exception as e:  # erreur de structure OpenAPI
    print("SPEC KO :", str(e).splitlines()[0])
    sys.exit(1)

# openapi-spec-validator 0.9.0 ne signale pas toujours une $ref interne cassée : contrôle explicite.
def refs(node):
    if isinstance(node, dict):
        if isinstance(node.get("$ref"), str):
            yield node["$ref"]
        for v in node.values():
            yield from refs(v)
    elif isinstance(node, list):
        for v in node:
            yield from refs(v)

cassees = []
for r in sorted(set(refs(spec))):
    cible = spec
    for partie in r.lstrip("#/").split("/"):
        cible = cible.get(partie) if isinstance(cible, dict) else None
    if cible is None:
        cassees.append(r)
if cassees:
    print("SPEC KO : références introuvables", cassees)
    sys.exit(1)
print("SPEC OK  OpenAPI", spec["openapi"], f"({len(set(refs(spec)))} références résolues)")

erreurs = 0
attendu = {
    "/api/creneaux": {"get"},
    "/api/rendez-vous": {"get", "post"},
    "/api/rendez-vous/{id}": {"get", "put", "patch", "delete"},
    "/api/patients/{patientId}": {"get", "patch"},
    "/api/patients/{patientId}/ordonnances": {"get"},
    "/api/urgences": {"post"},
}
for chemin, methodes in attendu.items():
    present = set(spec["paths"].get(chemin, {})) - {"parameters"}
    manque = methodes - present
    print(("OK        " if not manque else "MANQUE    ") + f"{chemin} {sorted(present)}" + (f" manque {sorted(manque)}" if manque else ""))
    erreurs += bool(manque)

def inline(node):
    """Remplace les $ref internes par leur contenu (les schémas du contrat ne sont pas récursifs)."""
    if isinstance(node, dict):
        if "$ref" in node:
            cible = spec
            for partie in node["$ref"].lstrip("#/").split("/"):
                cible = cible[partie]
            return inline(cible)
        return {k: inline(v) for k, v in node.items()}
    if isinstance(node, list):
        return [inline(v) for v in node]
    return node

def validateur(nom):
    return OAS30Validator(inline(spec["components"]["schemas"][nom]), format_checker=FormatChecker())

# Cohérence avec les données fictives : chaque patient du seed est un ProfilPatient valide.
profil = validateur("ProfilPatient")
champs = set(spec["components"]["schemas"]["ProfilPatient"]["properties"])
patients = json.loads((racine / "contracts/seed/patients.json").read_text(encoding="utf-8"))
ko = [(p["id"], e.message) for p in patients for e in profil.iter_errors({k: v for k, v in p.items() if k in champs})]
print(("OK        " if not ko else "INVALIDE  ") + f"{len(patients)} patients du seed conformes à ProfilPatient" + (f" : {ko}" if ko else ""))
erreurs += bool(ko)

# Contrôles négatifs : ces corps doivent être refusés.
negatifs = [
    ("RendezVousCreation", {"patientId": "TN-12345", "creneauId": "CRN-000001"}, "identifiant patient mal formé"),
    ("RendezVousCreation", {"creneauId": "CRN-000001"}, "patientId manquant"),
    ("RendezVousModification", {}, "PATCH vide"),
    ("RendezVousModification", {"statut": "PERDU"}, "statut inconnu"),
    ("DemandeUrgence", {"patientId": "TN-0012345", "identifiantAlerte": "ALR-0001", "niveau": "GRAVE"}, "niveau d'alerte inconnu"),
]
for nom, corps, raison in negatifs:
    refuse = not validateur(nom).is_valid(corps)
    print(("REJETÉ    " if refuse else "ACCEPTÉ ! ") + f"{nom} : {raison}")
    erreurs += not refuse

sys.exit(1 if erreurs else 0)
