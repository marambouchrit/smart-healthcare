#!/usr/bin/env bash
# Contrôle de tous les contrats de contracts/ (P5.5) : seed, XSD, WSDL, .proto, SDL GraphQL, OpenAPI (portail et trace).
# Outils Python (versions de docs/DECISIONS.md) dans un environnement au choix, passé par la variable PYTHON :
#   python -m venv .venv-contrats
#   .venv-contrats/Scripts/pip install zeep==4.3.3 grpcio-tools==1.85.1 graphql-core==3.3.2 openapi-spec-validator==0.9.0
#   PYTHON=.venv-contrats/Scripts/python bash scripts/check-contrats.sh
# Code de sortie : 0 si tous les contrôles réussissent.
set -u
cd "$(dirname "$0")/.."
PY="${PYTHON:-python}"
STATUT=0

controle() {   # libellé, commande…
  local libelle=$1; shift
  local sortie
  sortie=$("$@" 2>&1)
  if [ $? -eq 0 ]; then
    echo "[OK]    $libelle"
  else
    echo "[ÉCHEC] $libelle"; echo "$sortie" | tail -5 | sed 's/^/        /'; STATUT=1
  fi
}

controle "seed (contracts/seed)"                 node scripts/check-seed.mjs
controle "XSD + exemples SOAP"                   bash -c 'java scripts/ValidateXsd.java contracts/soap/dossier.xsd contracts/soap/exemples/*.xml'
controle "WSDL (client zeep, 4 opérations)"      "$PY" -X utf8 scripts/check_wsdl.py contracts/soap/dossier.wsdl contracts/soap/dossier.xsd
controle ".proto gRPC"                           "$PY" -X utf8 scripts/check_proto.py
controle "SDL GraphQL + exemples"                "$PY" -X utf8 scripts/check_graphql.py
controle "OpenAPI portail REST"                  "$PY" -X utf8 scripts/check_openapi.py
controle "OpenAPI trace des workflows"           "$PY" -X utf8 -c "
import yaml
from openapi_spec_validator import validate
validate(yaml.safe_load(open('contracts/orchestrateur/workflows-openapi.yaml', encoding='utf-8')))"

[ $STATUT -eq 0 ] && echo "TOUS LES CONTRATS SONT VALIDES" || echo "AU MOINS UN CONTRAT EN ÉCHEC"
exit $STATUT
