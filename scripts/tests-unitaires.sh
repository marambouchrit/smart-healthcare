#!/usr/bin/env bash
# Tests de chaque composant (P5.4) : 5 projets Java (Maven), 2 projets Python (unittest), le client Angular (Vitest).
# Prérequis : Docker démarré (Testcontainers) et `docker compose up -d postgres` (tests PostgreSQL du REST, SOAP et gRPC),
# environnements Python préparés (voir CLAUDE.md), `npm install` fait dans web-client.
# Usage (Git Bash, depuis la racine) : bash scripts/tests-unitaires.sh      Code de sortie : 0 si tout passe.
set -u
cd "$(dirname "$0")/.."
RACINE=$(pwd)
JOURNAUX=$(mktemp -d)
declare -a LIGNES
STATUT_GLOBAL=0

maven() {   # $1 = dossier du projet
  local nom=$1 journal="$JOURNAUX/$(basename "$1").log"
  (cd "$RACINE/$1" && ./mvnw -q test >"$journal" 2>&1)
  local code=$?
  local bilan
  bilan=$(cat "$RACINE/$1"/target/surefire-reports/*.txt 2>/dev/null | grep -h "Tests run:" |
    awk -F'[:,]' '{t+=$2; f+=$4; e+=$6; s+=$8} END {printf "%d tests, %d échecs, %d erreurs, %d ignorés", t, f, e, s}')
  ajouter "$nom" "$code" "$bilan" "$journal"
}

python_unittest() {   # $1 = dossier du projet (avec .venv)
  local journal="$JOURNAUX/$(basename "$1").log"
  (cd "$RACINE/$1" && .venv/Scripts/python -X utf8 -m unittest discover -s tests -t . >"$journal" 2>&1)
  local code=$?
  local bilan
  bilan=$(grep -E "^Ran [0-9]+ tests" "$journal" | tail -1)
  bilan="$bilan, $(tail -3 "$journal" | grep -E "^(OK|FAILED)" | tail -1)"
  ajouter "$1" "$code" "$bilan" "$journal"
}

angular() {
  local journal="$JOURNAUX/web-client.log"
  (cd "$RACINE/web-client" && npm test -- --watch=false >"$journal" 2>&1)
  local code=$?
  local bilan
  bilan=$(sed 's/\x1b\[[0-9;]*m//g' "$journal" | grep -E "^\s+Tests\s" | tail -1 | sed 's/^ *//')
  ajouter "web-client" "$code" "$bilan" "$journal"
}

ajouter() {   # nom, code, bilan, journal
  local etat="OK"
  if [ "$2" -ne 0 ]; then etat="ÉCHEC"; STATUT_GLOBAL=1; fi
  LIGNES+=("$(printf '%-28s %-6s %s' "$1" "$etat" "$3")")
  echo "[$etat] $1 : $3" ; [ "$2" -ne 0 ] && echo "       journal : $4"
}

echo "Tests de chaque composant — journaux dans $JOURNAUX"
maven services/soap-dossier
maven services/rest-portail
maven services/graphql-dashboard
maven orchestrator
maven gateway
python_unittest services/grpc-telemetrie
python_unittest iot-simulator
angular

echo
echo "=== Bilan"
printf '%s\n' "${LIGNES[@]}"
[ "$STATUT_GLOBAL" -eq 0 ] && echo "TOUS LES TESTS PASSENT" || echo "AU MOINS UNE SUITE EN ÉCHEC"
exit "$STATUT_GLOBAL"
