#!/usr/bin/env bash
# Levanta el stack TP5 completo (infra Docker + microservicios Spring).
# Uso: ./dev-support/start-tp5.sh
# Reqs: Docker, Java 17+, Maven Wrapper, curl, python3

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

log() { printf '[start-tp5] %s\n' "$*"; }

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts="${3:-60}"
  for i in $(seq 1 "$attempts"); do
    if curl -sf "$url" >/dev/null 2>&1; then
      log "$label listo."
      return 0
    fi
    sleep 2
  done
  log "ERROR: $label no respondió en $((attempts * 2))s ($url)"
  return 1
}

wait_for_eureka_service() {
  local service="$1"
  local attempts="${2:-60}"
  for i in $(seq 1 "$attempts"); do
    if curl -sf "http://localhost:8761/eureka/apps/$service" | grep -q '<status>UP</status>'; then
      log "  → $service registrado en Eureka."
      return 0
    fi
    sleep 2
  done
  log "ERROR: $service no apareció UP en Eureka."
  return 1
}

# python shim (Linux suele tener solo python3; opcional si el FS no permite symlinks)
if command -v python3 >/dev/null 2>&1; then
  mkdir -p .bin 2>/dev/null || true
  if ln -sf "$(command -v python3)" .bin/python 2>/dev/null; then
    export PATH="$ROOT/.bin:$PATH"
  fi
fi

log "1/4 — Docker (MySQL, RabbitMQ, Keycloak)..."
docker compose up -d

log "2/4 — Esperando MySQL y Keycloak..."
for i in $(seq 1 40); do
  docker exec greater-events-mysql mysqladmin ping -h 127.0.0.1 -uroot -pinsecure --silent 2>/dev/null && break
  sleep 2
done

log "Asegurando bases TP5 (idempotente)..."
docker exec greater-events-mysql mysql -uroot -pinsecure -e "
CREATE DATABASE IF NOT EXISTS catalog_event CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS user_social CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS notification_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"

wait_for_url "http://localhost:8080/realms/master/.well-known/openid-configuration" "Keycloak"
./keycloak/setup-realm.sh

export KEYCLOAK_CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-pdyc-secret-dev}"

log "3/4 — Compilando..."
./mvnw -q -DskipTests package

log "4/4 — Arrancando microservicios (logs en /tmp/greater-events-*.log)..."

start_app() {
  local module="$1"
  local jar
  jar=$(ls "$ROOT/$module/target/"*.jar 2>/dev/null | grep -v 'original' | head -1)
  nohup java -jar "$jar" > "/tmp/greater-events-${module}.log" 2>&1 &
  log "  → $module (PID $!)"
}

# Orden: Eureka → Config → servicios → Gateway
start_app eureka-server
wait_for_url "http://localhost:8761/" "Eureka" 30

start_app config-server
wait_for_url "http://localhost:8888/application/default" "Config Server" 30

start_app catalog-event-service
start_app user-social-service
start_app notification-service

wait_for_eureka_service "CATALOG-EVENT-SERVICE"
wait_for_eureka_service "USER-SOCIAL-SERVICE"
wait_for_eureka_service "NOTIFICATION-SERVICE"

start_app api-gateway
wait_for_url "http://localhost:8081/artists" "API Gateway" 30

log ""
log "Listo. Punto de entrada Postman: http://localhost:8081"
log "Eureka dashboard:            http://localhost:8761"
log "RabbitMQ UI:                 http://localhost:15672 (guest/guest)"
log "Keycloak:                    http://localhost:8080"
log ""
log "Logs: tail -f /tmp/greater-events-*.log"
