# Charge contracts/graphql/dashboard.graphqls et valide les opérations d'exemple contre le schéma.
# Prérequis (outil de vérification) : pip install graphql-core==3.3.2
# Usage : python scripts/check_graphql.py
import sys
from pathlib import Path
from graphql import build_schema, parse, validate, GraphQLError

racine = Path(__file__).resolve().parent.parent / "contracts" / "graphql"
try:
    schema = build_schema((racine / "dashboard.graphqls").read_text(encoding="utf-8"))
except GraphQLError as e:
    print("SCHEMA KO :", e)
    sys.exit(1)
print("SCHEMA OK  Query:", ", ".join(schema.query_type.fields),
      "| Mutation:", ", ".join(schema.mutation_type.fields))

erreurs = 0
for f in sorted((racine / "exemples").glob("*.graphql")):
    try:
        errs = validate(schema, parse(f.read_text(encoding="utf-8")))
    except GraphQLError as e:
        errs = [e]
    print(("VALIDE    " if not errs else "INVALIDE  ") + f.name)
    for e in errs:
        print("   ", e.message)
    erreurs += bool(errs)

# Contrôles négatifs : ces opérations doivent être rejetées.
negatifs = {
    "champ inconnu": '{ patient(id: "TN-0012345") { adresseSecrete } }',
    "énumération invalide": '{ patients(filtres: { niveauRisque: [TRES_ELEVE] }) { id } }',
    "argument obligatoire manquant": "{ patient { id } }",
    "mutation absente du contrat": 'mutation { supprimerPatient(id: "TN-0012345") }',
}
for nom, q in negatifs.items():
    errs = validate(schema, parse(q))
    print(("REJETÉ    " if errs else "ACCEPTÉ ! ") + nom + (f" -> {errs[0].message}" if errs else ""))
    erreurs += not errs

sys.exit(1 if erreurs else 0)
