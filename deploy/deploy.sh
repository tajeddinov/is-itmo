#!/usr/bin/env bash
set -Eeuo pipefail

REMOTE_HOST="${REMOTE_HOST:-se}"
REMOTE_BASE="${REMOTE_BASE:-~/is}"
REMOTE_WWW="${REMOTE_BASE}/www"
REMOTE_WAR="${REMOTE_BASE}/app.war"

FRONTEND_DIR="../frontend"
BACKEND_DIR="../"

declare -a CANDIDATE_FILES=(
  "httpd.conf"
  "init.sql"
  "postgresql.jar|postgresql-42.7.4.jar"
  "wildfly.tar.gz|wildfly-37.0.1.Final.tar.gz"
)

bold() { printf "\033[1m%s\033[0m\n" "$*"; }
log()  { printf "\033[1;34m[deploy]\033[0m %s\n" "$*"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

trap 'log "❌ Ошибка на строке $LINENO"; exit 1' ERR

pick_file() {
  local spec="$1"
  IFS='|' read -r -a opts <<< "$spec"
  for f in "${opts[@]}"; do
    if [[ -f "$f" ]]; then
      echo "$f"
      return 0
    fi
  done
  return 1
}

bold "0) SSH: создаю каталоги на ${REMOTE_HOST}"
ssh "${REMOTE_HOST}" bash -lc "
  set -Eeuo pipefail
  mkdir -p ${REMOTE_BASE} \
           ${REMOTE_WWW} \
           ${REMOTE_BASE}/log \
           ${REMOTE_BASE}/log/mutex-dir
"

bold "1) FRONTEND: build в ${FRONTEND_DIR} и загрузка в ${REMOTE_WWW}"

[[ -d "${FRONTEND_DIR}" ]] || { echo "Нет каталога фронтенда: ${FRONTEND_DIR}"; exit 1; }

pushd "${FRONTEND_DIR}" >/dev/null

if npm run | grep -qE '^\s*clean'; then
  log "npm run clean"
  npm run clean
else
  log "чистим dist (rm -rf dist)"
  rm -rf dist
fi

log "npm run build"
npm run build

[[ -d "dist" ]] || { echo "Сборка фронтенда не создала dist/"; exit 1; }

log "rsync dist/ → ${REMOTE_HOST}:${REMOTE_WWW}/ (с --delete)"
rsync -azv --delete --chmod=Du=rwx,Dgo=rx,Fu=rw,Fgo=r \
  -e "ssh" \
  "dist/" "${REMOTE_HOST}:${REMOTE_WWW}/"

popd >/dev/null

bold "2) BACKEND: gradle build в ${BACKEND_DIR} и загрузка WAR в ${REMOTE_WAR}"

[[ -d "${BACKEND_DIR}" ]] || { echo "Нет каталога бэкенда: ${BACKEND_DIR}"; exit 1; }

pushd "${BACKEND_DIR}" >/dev/null

GRADLE="./gradlew"
if [[ ! -x "$GRADLE" ]]; then
  GRADLE="gradle"
fi

log "$GRADLE clean"
$GRADLE clean

log "$GRADLE build"
$GRADLE build

WAR_PATH="build/libs/app.war"
[[ -f "${WAR_PATH}" ]] || {
  echo "Не найден ${WAR_PATH}. Убедись, что Gradle собирает WAR с именем app.war (задача war/bootWar)."
  exit 1
}

log "scp ${WAR_PATH} → ${REMOTE_HOST}:${REMOTE_WAR}"
scp -p "${WAR_PATH}" "${REMOTE_HOST}:${REMOTE_WAR}"

popd >/dev/null

bold "3) Копирую конфиги и архивы в ${REMOTE_BASE}"

for spec in "${CANDIDATE_FILES[@]:-}"; do
  if src="$(pick_file "$spec")"; then
    dst="${REMOTE_BASE}/$(basename "$src")"
    log "scp ${src} → ${REMOTE_HOST}:${dst}"
    scp -p "${src}" "${REMOTE_HOST}:${dst}"
  else
    echo "⚠️  Не найден ни один из вариантов: ${spec}"
  fi
done

bold "4) Проверка наличия на сервере"
ssh "${REMOTE_HOST}" bash -lc "
  set -Eeuo pipefail
  echo '--- ${REMOTE_BASE} ---'
  ls -lah ${REMOTE_BASE} | sed -n '1,200p'
  echo '--- www/ ---'
  ls -lah ${REMOTE_WWW} | sed -n '1,200p'
  echo '--- файл WAR ---'
  ls -lah ${REMOTE_WAR}
"

bold "✅ Deploy завершён"
