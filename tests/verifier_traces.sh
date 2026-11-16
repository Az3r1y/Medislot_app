#!/usr/bin/env bash
# Vérifie la chaîne de traces sur la stack lancée : Nginx -> collecteur, agent Java, Jaeger.
# Uniquement bash, curl et grep (fonctionne sous Git Bash).
APP=${APP_URL:-http://localhost:8088}
JAEGER=${JAEGER_URL:-http://localhost:16686}
ok=0; ko=0
point() { if [ "$2" = 0 ]; then echo "OK     $1"; ok=$((ok+1)); else echo "ÉCHEC  $1${3:+ : $3}"; ko=$((ko+1)); fi; }

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{"resourceSpans":[]}' "$APP/otlp/v1/traces")
[ "$code" = 200 ]; point "1. Nginx relaie /otlp/ au collecteur" $? "HTTP $code"

# backend en cours d'exécution, quel que soit le nom du projet Compose
backend=$(docker ps -q --filter label=com.docker.compose.service=backend | head -1)
[ -n "$backend" ] && docker logs "$backend" 2>&1 | grep -q 'opentelemetry-javaagent - version'
point "2. Le back démarre avec l'agent Java" $? "aucun conteneur backend lancé, ou pas de ligne de version de l'agent dans son journal"

curl -s -o /dev/null "$APP/api/professionals"; sleep 7   # BatchSpanProcessor de l'agent : ~5 s
traces=$(curl -s "$JAEGER/api/traces?service=medislot-api&operation=GET%20%2Fapi%2Fprofessionals&limit=1&lookback=5m")
echo "$traces" | grep -q '"operationName":"SELECT '
point "3. Jaeger reçoit medislot-api avec des spans SQL" $? "aucune trace GET /api/professionals avec SELECT dans Jaeger"

echo "Bilan : $ok OK, $ko ÉCHEC"
[ "$ko" = 0 ]
