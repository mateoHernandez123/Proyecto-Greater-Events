#!/usr/bin/env bash
# Configura el realm `unnoba` en una instancia local de Keycloak (http://localhost:8080)
# para el TP3 y TP4 de PDyC 2026. Idempotente: si una entidad ya existe la deja como está.
#
# Uso:   ./keycloak/setup-realm.sh
# Reqs:  Keycloak escuchando en localhost:8080 con admin/admin (ver docker-compose.yml).
#        curl y python3 en el PATH.
#
# TP4: ademas crea los realm roles `admin` y `user`, y asegura que el usuario
# tp3-user tenga el rol `admin` (para que pueda consumir /admin/**).

set -euo pipefail

KC_BASE="${KC_BASE:-http://localhost:8080}"
KC_ADMIN_USER="${KC_ADMIN_USER:-admin}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:-admin}"
REALM="${REALM:-unnoba}"
CLIENT_ID="${CLIENT_ID:-pdyc}"
CLIENT_SECRET="${CLIENT_SECRET:-pdyc-secret-dev}"
TEST_USER="${TEST_USER:-tp3-user}"
TEST_PASS="${TEST_PASS:-tp3pass}"
END_USER="${END_USER:-tp4-user}"
END_PASS="${END_PASS:-tp4pass}"

log() { printf '[setup-realm] %s\n' "$*"; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Falta el comando: $1" >&2; exit 1; }
}

require_cmd curl
require_cmd python3

log "Esperando a Keycloak en $KC_BASE ..."
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$KC_BASE/realms/master/.well-known/openid-configuration" || true)
  if [ "$code" = "200" ]; then
    log "Keycloak listo."
    break
  fi
  sleep 2
  if [ "$i" = "60" ]; then
    echo "Keycloak no respondio en 120s" >&2
    exit 1
  fi
done

log "Obteniendo token de admin..."
ADMIN_TOKEN=$(curl -s -X POST "$KC_BASE/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$KC_ADMIN_USER" \
  -d "password=$KC_ADMIN_PASS" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | python3 -c "import json,sys;print(json.load(sys.stdin)['access_token'])")

auth() { echo "Authorization: Bearer $ADMIN_TOKEN"; }

log "Verificando realm $REALM..."
REALM_HTTP=$(curl -s -o /dev/null -w '%{http_code}' -H "$(auth)" "$KC_BASE/admin/realms/$REALM")
if [ "$REALM_HTTP" = "404" ]; then
  log "Creando realm $REALM..."
  curl -s -o /dev/null -w 'realm create: %{http_code}\n' -X POST "$KC_BASE/admin/realms" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "{\"realm\":\"$REALM\",\"enabled\":true,\"sslRequired\":\"external\",\"registrationAllowed\":false,\"loginWithEmailAllowed\":true,\"resetPasswordAllowed\":true,\"accessTokenLifespan\":3600}"
else
  log "Realm $REALM ya existe (http=$REALM_HTTP)."
fi

log "Verificando client $CLIENT_ID..."
CLIENT_LIST=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/clients?clientId=$CLIENT_ID")
PDYC_ID=$(echo "$CLIENT_LIST" | python3 -c "import json,sys;l=json.load(sys.stdin);print(l[0]['id'] if l else '')")
if [ -z "$PDYC_ID" ]; then
  log "Creando client $CLIENT_ID con secret $CLIENT_SECRET..."
  curl -s -o /dev/null -w 'client create: %{http_code}\n' -X POST "$KC_BASE/admin/realms/$REALM/clients" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "{\"clientId\":\"$CLIENT_ID\",\"enabled\":true,\"protocol\":\"openid-connect\",\"publicClient\":false,\"clientAuthenticatorType\":\"client-secret\",\"secret\":\"$CLIENT_SECRET\",\"serviceAccountsEnabled\":true,\"standardFlowEnabled\":true,\"directAccessGrantsEnabled\":false,\"redirectUris\":[\"https://oauth.pstmn.io/v1/callback\",\"http://localhost:8081/*\"],\"webOrigins\":[\"+\"],\"attributes\":{\"post.logout.redirect.uris\":\"+\"}}"
  PDYC_ID=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/clients?clientId=$CLIENT_ID" | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['id'])")
else
  log "Client $CLIENT_ID ya existe (id=$PDYC_ID)."
fi

log "Asegurando client secret..."
curl -s -o /dev/null -w 'client secret: %{http_code}\n' -X PUT "$KC_BASE/admin/realms/$REALM/clients/$PDYC_ID" \
  -H "$(auth)" -H "Content-Type: application/json" \
  -d "{\"secret\":\"$CLIENT_SECRET\"}"

log "Asignando realm-admin (composite) al service account del client $CLIENT_ID..."
SVC_USER_ID=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/clients/$PDYC_ID/service-account-user" | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
RM_ID=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/clients?clientId=realm-management" | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['id'])")
# `realm-admin` es un composite role que incluye manage-users, view-users, query-users,
# manage-realm, view-realm, etc. Es el approach estandar para un service account que
# administra el realm. Tambien asignamos los granulares por si el composite se desactiva.
ROLES_JSON=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/clients/$RM_ID/roles" | python3 -c "import json,sys;wanted={'realm-admin','manage-users','view-users','query-users','manage-realm','view-realm'};print(json.dumps([{'id':r['id'],'name':r['name']} for r in json.load(sys.stdin) if r['name'] in wanted]))")
curl -s -o /dev/null -w 'role assign: %{http_code}\n' -X POST "$KC_BASE/admin/realms/$REALM/users/$SVC_USER_ID/role-mappings/clients/$RM_ID" \
  -H "$(auth)" -H "Content-Type: application/json" \
  -d "$ROLES_JSON"

ensure_realm_role() {
  local role_name="$1"
  local role_desc="$2"
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' -H "$(auth)" "$KC_BASE/admin/realms/$REALM/roles/$role_name")
  if [ "$code" = "404" ]; then
    log "Creando realm role $role_name..."
    curl -s -o /dev/null -w "role $role_name create: %{http_code}\n" -X POST "$KC_BASE/admin/realms/$REALM/roles" \
      -H "$(auth)" -H "Content-Type: application/json" \
      -d "{\"name\":\"$role_name\",\"description\":\"$role_desc\"}"
  else
    log "Realm role $role_name ya existe."
  fi
}

assign_realm_role_to_user() {
  local user_id="$1"
  local role_name="$2"
  local role_payload
  role_payload=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/roles/$role_name" | python3 -c "import json,sys;r=json.load(sys.stdin);print(json.dumps([{'id':r['id'],'name':r['name']}]))")
  curl -s -o /dev/null -w "role $role_name -> user: %{http_code}\n" -X POST "$KC_BASE/admin/realms/$REALM/users/$user_id/role-mappings/realm" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "$role_payload"
}

log "Asegurando realm roles TP4 (admin, user)..."
ensure_realm_role "admin" "Greater Events backoffice role"
ensure_realm_role "user"  "Greater Events end-user role"

log "Verificando usuario de prueba $TEST_USER..."
USER_LIST=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/users?username=$TEST_USER")
USER_ID=$(echo "$USER_LIST" | python3 -c "import json,sys;l=json.load(sys.stdin);print(l[0]['id'] if l else '')")
if [ -z "$USER_ID" ]; then
  log "Creando usuario $TEST_USER con password $TEST_PASS..."
  curl -s -o /dev/null -w 'user create: %{http_code}\n' -X POST "$KC_BASE/admin/realms/$REALM/users" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "{\"username\":\"$TEST_USER\",\"enabled\":true,\"emailVerified\":true,\"email\":\"$TEST_USER@example.com\",\"firstName\":\"TP3\",\"lastName\":\"User\",\"credentials\":[{\"type\":\"password\",\"value\":\"$TEST_PASS\",\"temporary\":false}]}"
  USER_ID=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/users?username=$TEST_USER" | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['id'])")
else
  log "Usuario $TEST_USER ya existe (id=$USER_ID)."
fi
log "Asignando rol admin a $TEST_USER..."
assign_realm_role_to_user "$USER_ID" "admin"

log "Verificando usuario final $END_USER..."
END_USER_LIST=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/users?username=$END_USER")
END_USER_ID=$(echo "$END_USER_LIST" | python3 -c "import json,sys;l=json.load(sys.stdin);print(l[0]['id'] if l else '')")
if [ -z "$END_USER_ID" ]; then
  log "Creando usuario $END_USER con password $END_PASS..."
  curl -s -o /dev/null -w 'end-user create: %{http_code}\n' -X POST "$KC_BASE/admin/realms/$REALM/users" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "{\"username\":\"$END_USER\",\"enabled\":true,\"emailVerified\":true,\"email\":\"$END_USER@example.com\",\"firstName\":\"TP4\",\"lastName\":\"EndUser\",\"credentials\":[{\"type\":\"password\",\"value\":\"$END_PASS\",\"temporary\":false}]}"
  END_USER_ID=$(curl -s -H "$(auth)" "$KC_BASE/admin/realms/$REALM/users?username=$END_USER" | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['id'])")
else
  log "Usuario $END_USER ya existe (id=$END_USER_ID)."
fi
log "Asignando rol user a $END_USER..."
assign_realm_role_to_user "$END_USER_ID" "user"

log "Listo. Resumen:"
echo "  Realm:         $REALM"
echo "  Client:        $CLIENT_ID  (secret: $CLIENT_SECRET)"
echo "  Roles:         admin, user"
echo "  Admin user:    $TEST_USER  /  $TEST_PASS    (rol admin)"
echo "  End user:      $END_USER  /  $END_PASS      (rol user)"
echo "  Issuer:        $KC_BASE/realms/$REALM"
echo "  Token URL:     $KC_BASE/realms/$REALM/protocol/openid-connect/token"
echo "  Auth URL:      $KC_BASE/realms/$REALM/protocol/openid-connect/auth"
echo
echo "Antes de arrancar el backend:"
echo "  export KEYCLOAK_CLIENT_SECRET=\"$CLIENT_SECRET\""
