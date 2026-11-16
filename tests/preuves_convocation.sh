#!/usr/bin/env bash
# Convocation d'un même rendez-vous : propriétaire (200), autre patient (404), après annulation (409).
APP=${APP_URL:-http://localhost:8088}; ID=${1:?usage: bash tests/preuves_convocation.sh <id du rendez-vous de Camille, confirmé>}
cd "$(mktemp -d)"   # cookies et PDF hors du dépôt
login(){ rm -f "$1"; local c=$(curl -s -b "$1" -c "$1" $APP/api/csrf); local h=$(echo "$c"|sed -E 's/.*"headerName":"([^"]+)".*/\1/'); local t=$(echo "$c"|sed -E 's/.*"token":"([^"]+)".*/\1/');
 curl -s -o /dev/null -b "$1" -c "$1" -H "$h: $t" --data-urlencode "username=$2" --data-urlencode 'password=MediSlot-demo-2026!' $APP/api/login; }
login camille.txt patient@medislot.test; login autre.txt autre@medislot.test
echo "### 1. Camille, propriétaire : 200"
echo "\$ curl -s -D - -o convocation.pdf -b <cookie Camille> $APP/api/appointments/$ID/convocation.pdf"
curl -s -D - -o convocation.pdf -b camille.txt $APP/api/appointments/$ID/convocation.pdf | tr -d '\r' | grep -iE '^HTTP|^content-(type|disposition|length)|^cache-control|^x-content-type|^pragma'
echo "\$ head -c 8 convocation.pdf"; head -c 8 convocation.pdf; echo
echo; echo "### 2. Second patient, même numéro : 404"
echo "\$ curl -s -D - -b <cookie autre@medislot.test> $APP/api/appointments/$ID/convocation.pdf"
curl -s -D - -b autre.txt $APP/api/appointments/$ID/convocation.pdf | tr -d '\r' | grep -iE '^HTTP|^content-type|^\{'; echo
echo; echo "### 3. Après annulation par Camille : 409"
c=$(curl -s -b camille.txt -c camille.txt $APP/api/csrf); h=$(echo "$c"|sed -E 's/.*"headerName":"([^"]+)".*/\1/'); t=$(echo "$c"|sed -E 's/.*"token":"([^"]+)".*/\1/')
echo "\$ curl -X POST … $APP/api/appointments/$ID/cancel  -> $(curl -s -o /dev/null -w '%{http_code}' -X POST -b camille.txt -c camille.txt -H "$h: $t" $APP/api/appointments/$ID/cancel)"
echo "\$ curl -s -D - -b <cookie Camille> $APP/api/appointments/$ID/convocation.pdf"
curl -s -D - -b camille.txt $APP/api/appointments/$ID/convocation.pdf | tr -d '\r' | grep -iE '^HTTP|^content-type|^\{'; echo
echo; echo "### Bonus. Sans session : 401"
echo "\$ curl -s -o /dev/null -w '%{http_code}' $APP/api/appointments/$ID/convocation.pdf"
curl -s -o /dev/null -w '%{http_code}\n' $APP/api/appointments/$ID/convocation.pdf
