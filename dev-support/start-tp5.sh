#!/usr/bin/env bash
# Levanta el stack TP5 completo (infra Docker + microservicios Spring).
# Uso: ./dev-support/start-tp5.sh
# Reqs: Docker, Java 17+, Maven Wrapper, curl, python3

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

log() { printf '[start-tp5] %s\n' "$*"; }

# python shim (Linux suele tener solo python3)
mkdir -p .bin
ln -sf "$(command -v python3)" .bin/python
export PATH="$ROOT/.bin:$PATH"

log "1/4 — Docker (MySQL, RabbitMQ, Keycloak)..."
docker compose up -d

log "2/4 — Esperando MySQL y Keycloak..."
for i in $(seq 1 40); do
  docker exec greater-events-mysql mysqladmin ping -h 127.0.0.1 -uroot -pinsecure --silent 2>/dev/null && break
  sleep 2
done
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
sleep 8
start_app config-server
sleep 5
start_app catalog-event-service
start_app user-social-service
start_app notification-service
sleep 10
start_app api-gateway

log ""
log "Listo. Punto de entrada Postman: http://localhost:8081"
log "Eureka dashboard:            http://localhost:8761"
log "RabbitMQ UI:                 http://localhost:15672 (guest/guest)"
log "Keycloak:                    http://localhost:8080"
log ""
log "Logs: tail -f /tmp/greater-events-*.log"
