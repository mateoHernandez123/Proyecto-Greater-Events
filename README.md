# Greater Events — API REST (Spring Boot)

Backend **REST** para una comunidad vinculada a **eventos musicales** y **artistas**, con perfil **administrador** (backoffice) y **usuario final** (catálogo público, follow de artistas, favoritos de eventos y notificaciones). La API tiene tres niveles de acceso:

- **Público** (sin token): catálogo de artistas y eventos vigentes, registro de usuarios.
- **Admin** (`/admin/**`, rol `admin`): backoffice de TP2 + gestión de usuarios admin (Keycloak).
- **Usuario final** (`/me/**`, rol `user`): seguir/dejar de seguir artistas, marcar favoritos, notificaciones.

Autenticación delegada a **Keycloak** (OAuth2 Resource Server, JWT). Roles parseados desde el claim `realm_access.roles`.

**Participantes:** Mateo Hernandez y Felipe Lucero.

---

## TL;DR — Para el profesor evaluador (5 minutos)

Todas las credenciales locales ya vienen seteadas en el repo (son **dev only**) para que el evaluador no tenga que buscarlas:

| Recurso                | Valor                                          |
| ---------------------- | ---------------------------------------------- |
| Keycloak admin console | `http://localhost:8080/admin/master/console/`  |
| Keycloak admin user    | `admin` / `admin`                              |
| Realm                  | `unnoba`                                       |
| Client ID              | `pdyc`                                         |
| Client secret          | `pdyc-secret-dev`                              |
| Realm roles            | `admin`, `user`                                |
| Usuario admin de prueba| `tp3-user` / `tp3pass` (rol `admin`)           |
| Usuario final de prueba| `tp4-user` / `tp4pass` (rol `user`)            |
| Backend Spring Boot    | `http://localhost:8081`                        |
| MySQL (perfil default) | `localhost:3306`, db `pdyc2026`, root/insecure |

Pasos (en orden, desde la raíz del repo, en Git Bash o PowerShell):

```bash
# 1) Levantar MySQL + Keycloak + Postgres-de-Keycloak
docker compose up -d

# 2) Esperar a que Keycloak responda (40-60s la primera vez)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/realms/master/.well-known/openid-configuration
# tiene que devolver 200

# 3) Crear realm `unnoba`, client `pdyc`, secret, roles admin/user, usuarios tp3-user y tp4-user (idempotente)
bash keycloak/setup-realm.sh        # PowerShell: .\keycloak\setup-realm.ps1

# 4) Arrancar el backend (puerto 8081)
export KEYCLOAK_CLIENT_SECRET="pdyc-secret-dev"   # PowerShell: $env:KEYCLOAK_CLIENT_SECRET="pdyc-secret-dev"
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # `local` usa H2 en memoria; sin esa flag usa MySQL

# 5) Smoke tests publicos (sin token, deben responder 200 con JSON)
curl -s http://localhost:8081/artists | head -c 300; echo
curl -s http://localhost:8081/events  | head -c 300; echo

# 6) Probar en Postman
# 6.1) Importar API/Greater-Events.postman_collection.json y API/Greater-Events-Local.postman_environment.json
# 6.2) Elegir el environment "Greater Events - Local" (ya trae secret, baseUrl, credenciales).
# 6.3) Carpeta "Public endpoints": no requieren token (incluye POST /auth/register).
# 6.4) Carpeta "Admin endpoints":  pestana Authorization -> Get New Access Token -> login tp3-user/tp3pass.
# 6.5) Carpeta "End-user endpoints": idem pero login tp4-user/tp4pass.
```

> Guía expandida en la sección [0.1](#01-cómo-probar-el-tp3-de-punta-a-punta). TP4 documentado en la sección [00](#00-tp4--usuarios-finales-roles-publico-y-notificaciones).
>
> **Nota de seguridad:** `pdyc-secret-dev`, `tp3pass` y `tp4pass` son valores de desarrollo locales para que esta entrega sea reproducible. En ambientes reales se rotan, se inyectan por variables de entorno y nunca se commitean.

---

## 00. TP4 — Usuarios finales, roles, público y notificaciones

Esta sección documenta los cambios que introduce la **Práctica 4** sobre la base del TP3.

### 00.1 Conceptos aplicados

- **Roles de realm en Keycloak:** dos roles nuevos en el realm `unnoba`:
  - `admin`: necesario para consumir cualquier endpoint `/admin/**` (backoffice).
  - `user`:  necesario para consumir cualquier endpoint `/me/**` (acciones de usuario final).
  - Los roles se asignan a usuarios en Keycloak. El script `keycloak/setup-realm.sh` los crea, asigna `admin` a `tp3-user` y crea `tp4-user` con rol `user`. Si crear un admin por `POST /admin/users`, el backend le asigna automáticamente el rol `admin`. Si crear un usuario final por `POST /auth/register`, se le asigna automáticamente `user`.
- **AuthenticationConverter:** clase `KeycloakJwtAuthenticationConverter` (config) que extrae el claim `realm_access.roles` del JWT y lo expone como `GrantedAuthority` prefijado con `ROLE_`, así Spring entiende `hasRole("admin")` y `hasRole("user")`. El `name` del principal es el `sub` (UUID estable de Keycloak), independiente del username.
- **SecurityFilterChain con tres niveles** (en este orden):
  1. **Públicos:** `POST /auth/register`, `GET /artists`, `GET /artists/{id}/events`, `GET /events`, `GET /events/{id}`, `/error`.
  2. **Admin:** `/admin/**` requiere `hasRole("admin")`.
  3. **Usuario final autenticado:** `/me/**` requiere `hasRole("user")`.
  4. Cualquier otro request requiere autenticación (default deny por seguridad).
- **Entidad local `User`** con `username` y `email` únicos, `keycloakId` (UUID), `createdAt`, más dos colecciones `@ManyToMany`: `followingArtists` (tabla intermedia `user_following_artists`) y `favoriteEvents` (`user_favorite_events`). El `keycloakId` es la fuente de verdad para asociar el JWT entrante con un usuario local; el `CurrentUserService` aprovisiona la fila local automáticamente la primera vez que un JWT válido golpea `/me/**` (defensa en profundidad).
- **Catálogo público:** `PublicCatalogService` solo devuelve artistas con `active=true` y eventos en estado `CONFIRMED` o `RESCHEDULED` con `start_date > now`. El detalle público de un evento que esté en estado `TENTATIVE` responde `404` para cumplir la consigna.
- **Notificaciones:** implementadas con un **event de dominio asincrónico**:
  - `EventService` publica `EventStateChangedEvent(eventId, newState)` cuando un evento pasa a `CONFIRMED`, `RESCHEDULED` o `CANCELLED`.
  - `NotificationService` está marcado con `@Async("notificationExecutor")` + `@EventListener` y, en un `ThreadPoolTaskExecutor` dedicado, genera una `Notification` por cada usuario que tenga el evento como favorito o que siga a alguno de los artistas del lineup (si calza por ambas razones, se prioriza `FAVORITE_EVENT`).
  - Los usuarios consultan sus notificaciones con `GET /me/notifications` (o `?unread_only=true`) y marcan como leídas con `PUT /me/notifications/{id}/read`.

### 00.2 Endpoints nuevos (TP4)

| Método y ruta                          | Acceso       | Descripción                                                                                                |
| -------------------------------------- | ------------ | ---------------------------------------------------------------------------------------------------------- |
| `POST /auth/register`                  | Público      | Registra usuario en Keycloak (rol `user`) y en la DB local. `201` con `id`, `keycloak_id`, `username`, `email`. |
| `GET  /artists`                        | Público      | Lista artistas activos, ordenados por nombre.                                                              |
| `GET  /artists/{artistId}/events`      | Público      | Próximos eventos del artista (`confirmed` o `rescheduled`, futuros). `404` si el artista no existe / inactivo. |
| `GET  /events`                         | Público      | Eventos vigentes ordenados por proximidad de fecha. Nunca devuelve `tentative`.                            |
| `GET  /events/{id}`                    | Público      | Detalle público. `404` si el evento está en `tentative`.                                                   |
| `GET  /me/following`                   | Rol `user`   | Artistas seguidos por el usuario autenticado.                                                              |
| `POST /me/following`                   | Rol `user`   | Body `{"artist_id": N}`. `201` al seguir. `400` si ya lo seguía / artista inactivo. `404` si no existe.    |
| `DELETE /me/following/{artistId}`      | Rol `user`   | `204` al dejar de seguir. `404` si no era seguido.                                                         |
| `GET  /me/following/events`            | Rol `user`   | Próximos eventos donde participa alguno de los artistas seguidos, ordenados por fecha.                     |
| `GET  /me/favorite-events`             | Rol `user`   | Favoritos vigentes (`confirmed`/`rescheduled` futuros).                                                    |
| `POST /me/favorite-events`             | Rol `user`   | Body `{"event_id": N}`. `400` si el evento es `tentative` o ya era favorito.                               |
| `DELETE /me/favorite-events/{eventId}` | Rol `user`   | `204` al desmarcar. `404` si no era favorito.                                                              |
| `GET  /me/notifications`               | Rol `user`   | Lista las notificaciones del usuario. Soporta query `unread_only=true`.                                    |
| `PUT  /me/notifications/{id}/read`     | Rol `user`   | Marca la notificación como leída.                                                                          |
| `POST /admin/users` (cambio TP4)       | Rol `admin`  | Sigue funcionando como en TP3 pero ahora **asigna automáticamente el rol `admin`** al usuario creado.      |

### 00.3 Cómo probar el TP4 end-to-end

```bash
# 0) Levantar stack y configurar realm (idempotente)
docker compose up -d
bash keycloak/setup-realm.sh
export KEYCLOAK_CLIENT_SECRET="pdyc-secret-dev"
./mvnw spring-boot:run

# 1) Catalogo publico, sin token
curl -s http://localhost:8081/artists | head -c 400; echo
curl -s http://localhost:8081/events  | head -c 400; echo

# 2) Registro publico de un usuario final
curl -s -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"jperez","email":"jperez@example.com","password":"Secret123!","first_name":"Juan","last_name":"Perez"}'

# 3) Probar /me/** desde Postman:
#    - Importar coleccion + environment de API/, elegir "Greater Events - Local".
#    - Folder "End-user endpoints" -> Authorization -> Get New Access Token -> login con tp4-user / tp4pass.
#    - POST /me/following  body {"artist_id": 1}
#    - POST /me/favorite-events body {"event_id": 2}
#    - GET  /me/following, /me/favorite-events, /me/following/events

# 4) Disparar notificaciones (desde "Admin endpoints" como tp3-user)
#    - PUT /admin/events/2/rescheduled body {"start_date":"2030-01-15T22:00:00"}
#    - PUT /admin/events/2/canceled

# 5) Volver al folder "End-user endpoints" y leer:
#    - GET /me/notifications  -> aparecen las notificaciones generadas async.
```

---

## 0. TP3 — IAM, OAuth2 y Keycloak

- **IAM (Identity and Access Management):** conjunto de procesos y herramientas para administrar identidades digitales, autenticación, autorización, roles, permisos y ciclo de vida de usuarios.
- **IdP (Identity Provider):** proveedor de identidad que autentica usuarios y emite tokens o aserciones confiables para las aplicaciones. En este TP el IdP es **Keycloak**.
- **SSO (Single Sign-On):** mecanismo por el cual un usuario inicia sesión una vez ante el IdP y reutiliza esa sesión para acceder a varias aplicaciones integradas.
- **OAuth2:** estándar de autorización que permite a un cliente obtener tokens de acceso para consumir recursos protegidos sin compartir credenciales con cada API.
- **Grant Types principales:** Authorization Code para apps web/confidenciales con redirección; Client Credentials para comunicación servicio a servicio; Refresh Token para renovar acceso sin reautenticación; Device Code para dispositivos con entrada limitada; Password Grant existe por compatibilidad histórica pero está desaconsejado.
- **JWT support vs opaque token support en Spring Security Resource Server:** con JWT support el backend valida firma, issuer y claims localmente usando un `JwtDecoder`; con opaque token support el backend consulta al IdP por introspección en cada token usando un `OpaqueTokenIntrospector`. Este proyecto usa **JWT support**.

---

## 0.1 Cómo probar el TP3 de punta a punta

Esta es la guía rápida y completa para que cualquier persona (incluido el evaluador) pueda probar el TP3 desde cero. Tenés dos caminos para configurar Keycloak:

- **Camino rápido (recomendado para probar):** ejecutar el script `keycloak/setup-realm.sh` (o `.ps1` en PowerShell) que llama a la Admin REST API de Keycloak y deja todo listo (realm, client, secret, rol `manage-users`, usuario de prueba).
- **Camino consigna (manual):** Keycloak arranca vacío y vos creás realm, client, secret y usuario por la consola, tal como pide la práctica. Está descrito más abajo en la sección 7 (paso 3A — Camino A2).

### 0.1.1 Prerequisitos

Tenés que tener instalado y disponible en el `PATH`:

| Herramienta        | Versión mínima                                    | Cómo verificar                                |
| ------------------ | ------------------------------------------------- | --------------------------------------------- |
| **JDK**            | 17+ (probado con 21)                              | `java -version`                               |
| **Docker Desktop** | reciente, con motor Linux                         | `docker --version` y `docker compose version` |
| **Postman**        | actual                                            | abrir Postman                                 |
| **Git**            | cualquiera reciente                               | `git --version`                               |
| **Python**         | 3.x (lo usa el script de setup para parsear JSON) | `python --version`                            |

Maven **no** hace falta a nivel sistema: el repo trae `mvnw`/`mvnw.cmd`.

> Si al hacer `docker compose up -d` ves el error `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`, **Docker Desktop no está corriendo**. Abrilo desde el menú Inicio y esperá a que el ícono diga "Docker is running" antes de seguir.

### 0.1.2 Paso 1 — Clonar el repo y entrar al directorio

```bash
git clone git@github.com:mateoHernandez123/Proyecto-Greater-Events.git
cd Proyecto-Greater-Events
git switch practica-3-keycloak
```

### 0.1.3 Paso 2 — Levantar MySQL + Keycloak con Docker

Desde la raíz del repo:

```bash
docker compose up -d
```

Eso levanta tres contenedores:

| Servicio                    | Imagen                             | Puerto local | Para qué sirve                              |
| --------------------------- | ---------------------------------- | ------------ | ------------------------------------------- |
| `greater-events-mysql`      | `mysql:8.0`                        | `3306`       | BD del backend (`pdyc2026`, root/insecure). |
| `greater-events-keycloakdb` | `postgres:15`                      | interno      | Almacenamiento de Keycloak.                 |
| `keycloak_web`              | `quay.io/keycloak/keycloak:23.0.7` | `8080`       | IdP. Admin: `admin` / `admin`.              |

Verificá que Keycloak arrancó (puede tardar 30–60s la primera vez):

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/realms/master/.well-known/openid-configuration
```

Tiene que devolver `200`. También podés entrar a `http://localhost:8080/admin/master/console/` con `admin` / `admin`.

### 0.1.4 Paso 3 — Configurar el realm `unnoba`

Corré el script de setup. Es **idempotente**: si lo corrés varias veces no rompe nada, solo asegura que todo exista.

**Git Bash:**

```bash
bash keycloak/setup-realm.sh
```

**PowerShell:**

```powershell
.\keycloak\setup-realm.ps1
```

El script crea / asegura:

- Realm: **`unnoba`**
- Client: **`pdyc`** (confidencial, **Standard Flow on**, **Service Accounts on**, **Direct Access Grants off** — alineado con la consigna del TP3)
- Client secret: **`pdyc-secret-dev`**
- Service account del client `pdyc` con roles `manage-users`, `view-users`, `query-users` del client `realm-management`
- Usuario de prueba: **`tp3-user`** / **`tp3pass`**
- Redirect URIs válidas: `https://oauth.pstmn.io/v1/callback` y `http://localhost:8081/*`

> Si el script termina con error de conexión, esperá unos segundos más a que Keycloak termine de arrancar y volvé a correrlo.

### 0.1.5 Paso 4 — Configurar el secret y arrancar el backend

El backend necesita el client secret del client `pdyc` para hablar con la Admin API de Keycloak (Client Credentials).

**Git Bash:**

```bash
export KEYCLOAK_CLIENT_SECRET="pdyc-secret-dev"
./mvnw spring-boot:run
```

**PowerShell:**

```powershell
$env:KEYCLOAK_CLIENT_SECRET="pdyc-secret-dev"
.\mvnw.cmd spring-boot:run
```

Esperá la línea **`Started EventsApplication`** en el log. La API queda en **`http://localhost:8081`**.

> Si todavía no querés tocar MySQL, podés correr con el perfil `local` (H2 en memoria): `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`. Igual necesitás Keycloak corriendo para validar tokens.

### 0.1.6 Paso 5 — Smoke test sin token (debe responder 401)

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/admin/artists
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/admin/events
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/admin/users
```

Las tres tienen que devolver **`401`**. Confirma que Spring Security está protegiendo todos los endpoints `/admin/**`.

### 0.1.7 Paso 6 — (Opcional) Probar con curl

La forma alineada con la consigna del TP3 para obtener un token es **Authorization Code desde Postman** (paso 7). Si por alguna razón querés probar rápido por consola con `curl`, tenés que activar temporalmente el grant **Direct access grants** en el client `pdyc`:

1. Entrá a `http://localhost:8080/admin/master/console/` con `admin` / `admin`.
2. Realm dropdown → **unnoba** → **Clients** → **pdyc** → **Settings** → **Capability config** → activá **Direct access grants** → **Save**.
3. Ya podés pedir token con `grant_type=password`:

   ```bash
   TOKEN=$(curl -s -X POST \
     -d "grant_type=password" \
     -d "client_id=pdyc" \
     -d "client_secret=pdyc-secret-dev" \
     -d "username=tp3-user" \
     -d "password=tp3pass" \
     -d "scope=openid" \
     http://localhost:8080/realms/unnoba/protocol/openid-connect/token | python -c "import json,sys;print(json.load(sys.stdin)['access_token'])")

   curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/admin/artists | head -c 300; echo
   curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/admin/events  | head -c 300; echo
   curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/admin/users   | head -c 300; echo
   ```

4. Cuando termines, volvé a **deseleccionar Direct access grants** para mantener la configuración como pide la consigna.

Sin token o con token inválido, el backend responde con JSON consistente:

```bash
$ curl -s http://localhost:8081/admin/artists
{"error":"Unauthorized: Full authentication is required to access this resource"}

$ curl -s -H "Authorization: Bearer not-a-real-token" http://localhost:8081/admin/artists
{"error":"Unauthorized: An error occurred while attempting to decode the Jwt: Malformed token"}
```

### 0.1.8 Paso 7 — Probar con Postman (Authorization Code, lo que pide la consigna)

1. **Import** → subí `API/Greater-Events.postman_collection.json` y `API/Greater-Events-Local.postman_environment.json`.
2. Elegí el environment **Greater Events — Local** (arriba a la derecha). Ya viene completo:

   | Variable               | Valor                                                               |
   | ---------------------- | ------------------------------------------------------------------- |
   | `baseUrl`              | `http://localhost:8081`                                             |
   | `keycloakAuthUrl`      | `http://localhost:8080/realms/unnoba/protocol/openid-connect/auth`  |
   | `keycloakTokenUrl`     | `http://localhost:8080/realms/unnoba/protocol/openid-connect/token` |
   | `keycloakClientId`     | `pdyc`                                                              |
   | `keycloakClientSecret` | `pdyc-secret-dev`                                                   |
   | `keycloakUsername`     | `tp3-user`                                                          |
   | `keycloakPassword`     | `tp3pass`                                                           |
   | `adminUserId`          | placeholder a reemplazar luego del primer `Create Admin User`       |

3. Abrí la colección **Greater Events — Admin API** → solapa **Authorization** → botón **Get New Access Token** abajo. Postman abre el flujo Authorization Code:
   - Si Postman pide login en Keycloak, usá `{{keycloakUsername}}` / `{{keycloakPassword}}` (los valores reales son `tp3-user` / `tp3pass`).
   - Tras autenticar, hacé clic en **Use Token** para que se inyecte como `Authorization: Bearer ...` en cada request.
4. Probá las requests:
   - **Admin Users (Keycloak):** `Get Admin Users`, `Create Admin User`, `Get Admin User by id`, `Delete Admin User`. Después de **Create**, copiá el `id` del JSON al campo `adminUserId` del environment para usar **Get** y **Delete**.
   - **Artists** y **Events:** todo el CRUD del TP2 sigue funcionando, ahora protegido por el token.

> **Si el token expira** (Keycloak emite tokens con vida corta), volvé a hacer **Get New Access Token** y **Use Token**.

### 0.1.9 Paso 8 — Validaciones automáticas

Desde la raíz del repo:

```bash
./mvnw test
```

Tiene que terminar con **`BUILD SUCCESS`** y `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` (el test verifica que el contexto de Spring carga con la configuración de seguridad).

### 0.1.10 Paso 9 — Bajar todo

```bash
docker compose down            # apaga MySQL, Keycloak y Postgres-de-Keycloak
docker compose down -v         # además borra los volúmenes (datos de MySQL y Keycloak)
```

`Ctrl+C` en la terminal de Maven baja el backend.

---

## 1. Alcance funcional (dominio y reglas)

- **Evento:** nombre, descripción, fecha de realización, estado (`tentative`, `confirmed`, `rescheduled`, `cancelled`) y conjunto de artistas asignados.
- **Artista:** nombre, género musical (`rock`, `techno`, `pop`, `jazz`, `folk`) y flag **`active`** (un artista desactivado no puede agregarse a nuevos eventos).
- **Tentative:** se puede editar nombre/fecha/descripción, borrar el evento, agregar y quitar artistas de la grilla.
- **Confirmed / rescheduled:** solo se puede **cancelar** o **reprogramar** (nueva fecha futura; el estado pasa a `rescheduled`).
- **Confirmar** un evento: solo desde **tentative**, y solo si la fecha de realización es **estrictamente futura** respecto al reloj del servidor.
- **Reprogramar:** solo eventos **confirmed** o **rescheduled** que aún no se dieron (fecha de realización **≥ fecha actual** según la regla implementada); la nueva fecha debe ser **futura**.
- **Cancelar:** solo desde **confirmed** o **rescheduled** → estado **cancelled** (en JSON se expone como `cancelled`).
- **Artista:** si **nunca** participó en un evento → se puede **editar** o **borrar** físicamente; si ya participó → **no** se edita; el **DELETE** pasa a **desactivar** (`active = false`) en lugar de borrar la fila.

**Persistencia:** entidades JPA en el paquete **`ar.edu.unnoba.pdyc2026.events.model`**, MySQL por defecto (`application.properties`, base sugerida **`pdyc2026`**, `spring.jpa.hibernate.ddl-auto=update`).

---

## 2. Qué está implementado en este repositorio

| Área                  | Contenido                                                                                                                                                                                                                                                                                         |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| API admin (TP2/TP3)   | Endpoints REST bajo `/admin/artists`, `/admin/events` y `/admin/users`. Reglas de ciclo de vida del evento y gestión de artistas.                                                                                                                                                                |
| API pública (TP4)     | `GET /artists`, `GET /artists/{id}/events`, `GET /events`, `GET /events/{id}`, `POST /auth/register`. Nunca exponen eventos `tentative`.                                                                                                                                                          |
| API usuario final (TP4)| Endpoints `/me/following`, `/me/favorite-events`, `/me/following/events`, `/me/notifications` con autoprovisioning del `User` local desde el JWT.                                                                                                                                                |
| Seguridad             | Spring Security OAuth2 Resource Server con JWT. `KeycloakJwtAuthenticationConverter` parsea `realm_access.roles`. Filter chain con tres niveles: público / `hasRole("admin")` / `hasRole("user")`.                                                                                                |
| Keycloak Admin        | `keycloak-admin-client` con Client Credentials para CRUD de usuarios admin y registro de usuarios finales. `KeycloakRoleService` asigna realm roles (`admin`, `user`) en cada creación.                                                                                                            |
| Notificaciones (TP4)  | Entidad `Notification` + `ApplicationEventPublisher`. `EventService` publica `EventStateChangedEvent`; `NotificationService` lo consume con `@Async("notificationExecutor")` y crea una notificación por usuario que tenga el evento favorito o un artista seguido en el lineup.                  |
| Corrección de ruta    | La especificación original del TP2 citaba `DELETE .../artist/:song_id`; el recurso coherente es **`DELETE /admin/events/{id}/artists/{artistId}`**.                                                                                                                                              |
| Capas                 | Repositorios Spring Data, servicios `@Service`, controladores `@RestController`, DTOs con **Java records** y validación Jakarta.                                                                                                                                                                  |
| Errores HTTP          | `ApiExceptionHandler` mapea reglas de negocio a **400**, no encontrado a **404**, conflicto único a **409** (username/email). `JsonAuthEntryPoint` y `JsonAccessDeniedHandler` mapean **401** y **403**. Todos devuelven `{"error":"mensaje"}`.                                                    |
| Datos demo            | `SampleDataLoader` (solo si no hay artistas al arrancar; desactivado con perfil `test`).                                                                                                                                                                                                          |
| Herramientas          | Maven Wrapper (`mvnw`), `docker-compose.yml` con MySQL + Keycloak, scripts `keycloak/setup-realm.sh`/`.ps1` (crean realm + client + roles `admin`/`user` + usuarios `tp3-user`/`tp4-user`), `dev-support/start-mysql.ps1` (Windows), colección Postman en **`API/`**.                              |
| Perfil opcional       | **`local`**: H2 en memoria para ejecutar sin MySQL (`application-local.properties`).                                                                                                                                                                                                              |

---

## 3. Cómo está organizado el código (capas y responsabilidades)

### Paquete `...events.model`

| Clase / tipo                                  | Rol                                                                                                                                                                                |
| --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`Artist`**                                  | Entidad JPA: `name`, `genre` (enum), `active`.                                                                                                                                     |
| **`Event`**                                   | Entidad JPA: `name`, `description`, `startDate`, `state`, relación **`@ManyToMany`** con `Artist` (tabla intermedia `event_artists`).                                              |
| **`User`** (TP4)                              | Entidad JPA: `username` (único), `email` (único), `keycloakId` (UUID), `createdAt`. Dos `@ManyToMany`: `followingArtists` (`user_following_artists`), `favoriteEvents` (`user_favorite_events`). |
| **`Notification`** (TP4)                      | Entidad JPA: pertenece a `User`, referencia a `Event`, `reason` (`FAVORITE_EVENT`/`FOLLOWED_ARTIST`), `newState`, `message`, `createdAt`, `read`.                                  |
| **`Genre`**, **`EventState`**, **`NotificationReason`** | Enumeraciones persistidas como string; los dos primeros exponen valores en **minúsculas** en JSON y aceptan el mismo formato en query/body.                              |

### Paquete `...repository`

Interfaces **Spring Data JPA** (`ArtistRepository`, `EventRepository`, `UserRepository`, `NotificationRepository`): consultas por género/estado, listados públicos de eventos vigentes, próximos eventos por artista o por colección de artist ids, lookup por `keycloakId`, notificaciones del usuario (todas o solo no leídas), y comprobaciones de reglas de negocio.

### Paquete `...service`

| Servicio                          | Comportamiento                                                                                                                                                                                                                                                                                          |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`ArtistService`**               | Listado con filtro opcional de género; alta; actualización solo si el artista **no** tiene eventos; borrado físico si no tiene historial, si no **desactivación**.                                                                                                                                      |
| **`EventService`**                | CRUD de evento acotado al estado **tentative**; transiciones **confirm / reschedule / cancel** con validaciones de fechas. **TP4:** publica `EventStateChangedEvent` después de cada transición.                                                                                                       |
| **`AdminUserService`** (TP3/TP4)  | CRUD de usuarios admin en Keycloak via admin client. **TP4:** al crear, asigna el realm role `admin`.                                                                                                                                                                                                  |
| **`AuthService`** (TP4)           | `POST /auth/register`: crea el usuario en Keycloak, le asigna el rol `user` y persiste la fila en `users`. Hace rollback en Keycloak si falla la persistencia local.                                                                                                                                   |
| **`KeycloakRoleService`** (TP4)   | Servicio compartido por `AdminUserService` y `AuthService` para asignar realm roles vía admin client.                                                                                                                                                                                                  |
| **`CurrentUserService`** (TP4)    | Resuelve el `User` local a partir del JWT del request actual. Si no existe, lo provisiona leyendo `sub`, `preferred_username` y `email`.                                                                                                                                                               |
| **`EndUserService`** (TP4)        | Lógica de follow/unfollow de artistas, favoritos de eventos y listados derivados (`/me/following*`, `/me/favorite-events`).                                                                                                                                                                            |
| **`PublicCatalogService`** (TP4)  | Catálogo público: solo artistas activos y eventos `CONFIRMED`/`RESCHEDULED` con fecha futura. Nunca expone `TENTATIVE`.                                                                                                                                                                                |
| **`NotificationService`** (TP4)   | `@Async` `@EventListener` que crea notificaciones cuando un evento cambia de estado. También expone la lectura/marcado para `/me/notifications`.                                                                                                                                                       |

### Paquete `...web`

| Componente                                                      | Rol                                                                                                                                                                      |
| --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`ArtistAdminController`**, **`EventAdminController`**         | Backoffice TP2/TP3 bajo `/admin/...`.                                                                                                                                    |
| **`AdminUserController`**                                       | `/admin/users` (Keycloak).                                                                                                                                               |
| **`AuthController`** (TP4)                                      | `/auth/register` (público).                                                                                                                                              |
| **`PublicArtistController`**, **`PublicEventController`** (TP4) | Catálogo público `/artists/**`, `/events/**`.                                                                                                                            |
| **`MeFollowingController`**, **`MeFavoriteEventsController`**, **`MeNotificationsController`** (TP4) | Endpoints `/me/**` para usuarios finales autenticados.                                                                                |
| **`StringToGenreConverter`**, **`StringToEventStateConverter`** | Conversión de query params (`genre`, `state`) a enums.                                                                                                                   |
| **`ApiExceptionHandler`**                                       | `@RestControllerAdvice` centraliza respuestas de error (incluye **409** para conflictos de unicidad).                                                                    |

### Paquete `...dto`

**Records** inmutables con nombres en **`snake_case`** en JSON. TP4 agrega `RegisterUserRequest`/`RegisterUserResponse`, `FollowArtistRequest`, `FavoriteEventRequest` y `NotificationResponse`.

### Paquete `...event` (TP4)

**`EventStateChangedEvent`**: record publicado por `EventService` cuando un evento cambia de estado. Lo consume el `NotificationService` de forma asincrónica.

### Paquete `...config`

**`ClockConfig`**: bean `Clock` del sistema. **`SecurityConfig`** (TP3 + TP4): filter chain con tres niveles (público/admin/user), `JwtDecoder`, cliente admin de Keycloak y dos executors (`keycloakExecutor` para admin client, `notificationExecutor` para listeners async); habilita `@EnableAsync`. **`KeycloakJwtAuthenticationConverter`** (TP4): parsea `realm_access.roles` y los expone como `ROLE_admin`/`ROLE_user`. **`JsonAuthEntryPoint`** y **`JsonAccessDeniedHandler`**: traducen 401/403 a `{"error":"..."}`. **`SampleDataLoader`**: datos iniciales.

### Paquete `...exception`

**`BusinessRuleException`**, **`ResourceNotFoundException`**: errores de dominio traducidos a HTTP por el advice.

---

## 4. Referencia de endpoints

| Método y ruta                                  | Acceso              | Descripción breve                                                                                                                                   |
| ---------------------------------------------- | ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST /auth/register`                          | Público             | TP4. Registra usuario final (`username`, `email`, `password`, `first_name`, `last_name`). `201`, `409` si duplicado.                                |
| `GET /artists`                                 | Público             | TP4. Lista artistas con `active=true`.                                                                                                              |
| `GET /artists/{artistId}/events`               | Público             | TP4. Próximos eventos `confirmed`/`rescheduled` del artista.                                                                                        |
| `GET /events`                                  | Público             | TP4. Eventos vigentes ordenados por fecha. Nunca incluye `tentative`.                                                                               |
| `GET /events/{id}`                             | Público             | TP4. Detalle público. `404` si el evento es `tentative`.                                                                                            |
| `GET /admin/artists`                           | Admin (rol `admin`) | Lista artistas; query opcional `genre`.                                                                                                             |
| `GET /admin/artists/{id}`                      | Admin               | Detalle de un artista.                                                                                                                              |
| `POST /admin/artists`                          | Admin               | Crea artista (body: `name`, `genre`).                                                                                                               |
| `PUT /admin/artists/{id}`                      | Admin               | Actualiza nombre y género (solo sin historial en eventos).                                                                                          |
| `DELETE /admin/artists/{id}`                   | Admin               | Borra o desactiva según historial.                                                                                                                  |
| `GET /admin/events`                            | Admin               | Lista resumida; query opcional `state`.                                                                                                             |
| `GET /admin/events/{id}`                       | Admin               | Detalle con artistas.                                                                                                                               |
| `POST /admin/events`                           | Admin               | Crea evento tentative sin artistas (`name`, `start_date`, `description`).                                                                           |
| `PUT /admin/events/{id}`                       | Admin               | Actualiza datos solo si **tentative**.                                                                                                              |
| `DELETE /admin/events/{id}`                    | Admin               | Borra solo si **tentative**.                                                                                                                        |
| `POST /admin/events/{id}/artists`              | Admin               | Body `artist_id`; solo **tentative**; artista activo.                                                                                               |
| `DELETE /admin/events/{id}/artists/{artistId}` | Admin               | Quita de la grilla; solo **tentative**.                                                                                                             |
| `PUT /admin/events/{id}/confirmed`             | Admin               | Confirma desde **tentative**. **TP4:** dispara notificaciones async.                                                                                |
| `PUT /admin/events/{id}/rescheduled`           | Admin               | Body `start_date`; reprograma. **TP4:** dispara notificaciones async.                                                                               |
| `PUT /admin/events/{id}/canceled`              | Admin               | Cancela. **TP4:** dispara notificaciones async.                                                                                                     |
| `GET /admin/users`                             | Admin               | Lista usuarios administradores desde Keycloak.                                                                                                      |
| `GET /admin/users/{id}`                        | Admin               | Obtiene un usuario admin por UUID.                                                                                                                  |
| `POST /admin/users`                            | Admin               | Crea usuario admin. **TP4:** además asigna automáticamente el rol `admin`.                                                                          |
| `DELETE /admin/users/{id}`                     | Admin               | Elimina un usuario admin.                                                                                                                           |
| `GET /me/following`                            | User (rol `user`)   | TP4. Artistas que sigue el usuario autenticado.                                                                                                     |
| `POST /me/following`                           | User                | TP4. Body `{"artist_id":N}`.                                                                                                                        |
| `DELETE /me/following/{artistId}`              | User                | TP4. Deja de seguir.                                                                                                                                |
| `GET /me/following/events`                     | User                | TP4. Próximos eventos de artistas seguidos.                                                                                                         |
| `GET /me/favorite-events`                      | User                | TP4. Favoritos vigentes.                                                                                                                            |
| `POST /me/favorite-events`                     | User                | TP4. Body `{"event_id":N}`. `400` si el evento es `tentative` o ya es favorito.                                                                     |
| `DELETE /me/favorite-events/{eventId}`         | User                | TP4. Quita de favoritos.                                                                                                                            |
| `GET /me/notifications`                        | User                | TP4. Lista notificaciones (`unread_only=true` opcional).                                                                                            |
| `PUT /me/notifications/{id}/read`              | User                | TP4. Marca una notificación como leída.                                                                                                             |

**Media type:** JSON `application/json` en cuerpos de entrada y respuestas.

**Autenticación:** los endpoints públicos no requieren token. Los demás esperan `Authorization: Bearer <access_token>` con el rol indicado en la columna "Acceso".

---

## 5. Criterios REST / HTTP aplicados (resumen)

- Recursos nombrados con **sustantivos en plural** y jerarquía clara (`/admin/events`, `/admin/events/{id}/artists`).
- Verbos HTTP alineados a operaciones: **GET** lectura, **POST** creación, **PUT** reemplazo/acción de dominio, **DELETE** eliminación o baja lógica.
- **Sin estado de sesión en el servidor** entre requests: cada llamada lleva su `Bearer Token`; el backend valida JWT contra Keycloak.
- Respuestas con códigos explícitos (**200**, **201**, **204**, **400**, **404**) y cuerpo JSON acotado en errores.

---

## 6. Checklist: qué suele exigirse en un repositorio “completo”

Para que cualquier persona (o un proceso de revisión) pueda reproducir el trabajo, conviene que el repositorio incluya al menos:

- [x] Código fuente **Maven** + **Spring Boot** (`pom.xml`, `src/`).
- [x] **`README.md`** con alcance, arquitectura, endpoints, seguridad OAuth2 y pasos de ejecución (este archivo).
- [x] Configuración de conexión coherente (`application.properties` y, si aplica, `application-local.properties`).
- [x] `docker-compose.yml` con MySQL, Keycloak y base Postgres de Keycloak.
- [x] Scripts de setup de Keycloak (`keycloak/setup-realm.sh` y `keycloak/setup-realm.ps1`) para dejar realm/client/usuario listos vía Admin REST API.
- [x] Colección **Postman** (y environment) en **`API/`** para probar autenticación y todos los flujos.
- [x] **`.gitignore`** adecuado (por ejemplo excluir `target/`, datos locales de MySQL embebidos si los hubiera).

Si tu institución pide **un ZIP en una plataforma**, un **nombre de repositorio** (`pdyc2026-[sede]-[grupo]`, etc.) o **repositorio privado con colaboradores**, aplicá esas reglas **además** de lo anterior; no forman parte del código en sí.

---

## 7. Guía paso a paso (cualquier máquina)

Seguí los pasos en orden. Podés **omitir los que correspondan a MySQL** si más abajo elegís solo el **perfil `local`** (H2).

### Paso 1 — Código y terminal

1. Cloná el repositorio o descomprimí el proyecto.
2. Abrí una terminal en la **raíz del repo** (donde está `pom.xml` y `mvnw`).

### Paso 2 — Java

Comprobá **JDK 17+**:

```bash
java -version
```

Si no tenés Maven global, no importa: el proyecto usa **Maven Wrapper** (`./mvnw` o `mvnw.cmd`).

### Paso 3 — Elegir cómo conectar la base de datos

| Camino                     | Cuándo usarlo                                                                                                                                                         |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **A. Docker**              | Tenés Docker Desktop (o motor Docker) y querés MySQL + Keycloak sin instalarlos en el sistema.                                                                        |
| **B. MySQL ya instalado**  | Ya tenés un servidor MySQL local o remoto; solo creás la base y ajustás credenciales si hace falta.                                                                   |
| **C. Script Windows**      | Windows, MySQL 8.4 instalado (por ejemplo con `winget install -e --id Oracle.MySQL`), sin servicio configurado; el script levanta `mysqld` con datadir en tu usuario. |
| **D. Perfil `local` (H2)** | No querés MySQL: levantás la app en memoria para probar endpoints (los datos se pierden al cerrar la JVM).                                                            |

Los valores **por defecto** del proyecto (`src/main/resources/application.properties`):

- URL: `jdbc:mysql://localhost:3306/pdyc2026`
- Usuario: `root`
- Contraseña: `insecure`

Si usás otros datos, editá ese archivo **antes** de arrancar Spring.

---

### Paso 3A — MySQL y Keycloak con Docker

1. Iniciá **Docker Desktop** (o el servicio Docker) y esperá a que esté listo.
2. En la raíz del proyecto:

   ```bash
   docker compose up -d
   ```

3. Esperá unos segundos: MySQL expone **3306** y crea la base **`pdyc2026`** con usuario/contraseña alineados al `application.properties`; Keycloak expone **8080** con admin `admin` / `admin`.

**Camino A1 — Configuración por script (rápido):** ejecutá `bash keycloak/setup-realm.sh` (o `.\keycloak\setup-realm.ps1` en PowerShell). El script habla con la Admin REST API de Keycloak y deja creados, de forma idempotente:

- Realm `unnoba`, client `pdyc` con secret `pdyc-secret-dev`, service account con roles `manage-users`/`view-users`/`query-users`, redirect URIs (`https://oauth.pstmn.io/v1/callback`, `http://localhost:8081/*`) y un usuario `tp3-user` / `tp3pass`.

**Camino A2 — Configuración manual desde la consola (lo que describe la consigna):**

1. Accedé a `http://localhost:8080/admin/master/console/` con `admin` / `admin`.
2. **Create Realm** → nombre `unnoba`.
3. **Clients** → **Create client** → nombre `pdyc` → siguiente.
4. **Settings** del client `pdyc`:
   - Valid redirect URIs: `https://oauth.pstmn.io/v1/callback`.
   - Capability config: **Client authentication: On**, **Standard flow: On**, **Service account roles: On**, **Direct access grants: Off**.
5. **Service account roles** → **Assign role** → cambiar filtro a **Filter by clients** → seleccionar **manage-users** del client `realm-management` → Assign.
6. **Credentials** → **Client Authenticator: Client Id and Secret** → generá el secret y guardalo (lo vas a usar en `KEYCLOAK_CLIENT_SECRET` y en Postman).
7. **Users** → **Add user** → completá `username` → Create. Después solapa **Credentials** → **Set password**, password no temporal.

> Si al hacer `docker compose up -d` aparece un error con `dockerDesktopLinuxEngine` o pipe, el motor Docker no está en ejecución: abrí Docker Desktop y esperá a que el ícono diga "Docker is running".

---

### Paso 3B — MySQL instalado por vos (manual)

1. Asegurate de que el servidor MySQL **esté corriendo** y escuchando donde indique tu `application.properties` (por defecto `localhost:3306`).
2. Creá la base (el SQL va **dentro** del cliente `mysql`, no como comando suelto en la shell):

   ```bash
   mysql -h 127.0.0.1 -P 3306 -u root -p -e "CREATE DATABASE IF NOT EXISTS pdyc2026 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   ```

3. Si tu `mysql` no está en el `PATH`, usá la ruta completa al ejecutable (en Windows suele estar en `C:\Program Files\MySQL\MySQL Server 8.x\bin\mysql.exe`).

---

### Paso 3C — Windows: script `dev-support/start-mysql.ps1`

1. Instalá **MySQL Server** si aún no lo tenés (por ejemplo `winget install -e --id Oracle.MySQL`). Revisá que exista `mysqld.exe` (ruta por defecto del script: `C:\Program Files\MySQL\MySQL Server 8.4`; si tu versión es otra, editá la variable `$Basedir` al inicio del script).
2. En **PowerShell**, desde la raíz del repo:

   ```powershell
   .\dev-support\start-mysql.ps1
   ```

3. El script usa un datadir bajo **`%USERPROFILE%\mysql-data-pdyc2026`**, arranca `mysqld` en **127.0.0.1:3306** y deja **`root` / `insecure`** y la base **`pdyc2026`**. Si el puerto 3306 ya está en uso, asume que MySQL ya está levantado y sale sin error.

---

### Paso 3D — Sin MySQL: perfil `local` (H2)

Desde la raíz del proyecto:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

No hace falta crear bases ni Docker. La consola H2 puede habilitarse en `application-local.properties` si la querés usar en el navegador.

---

### Paso 4 — Puertos locales

Keycloak usa **8080** y la app Spring Boot usa **8081** por defecto. Si 8080 está ocupado, liberalo antes de iniciar Docker o cambiá el puerto publicado de Keycloak en `docker-compose.yml`. Si 8081 está ocupado, cambiá en `application.properties`:

```properties
server.port=8081
```

**Git Bash (Windows)** — ver quién escucha en 8080 o 8081 y matar proceso:

```bash
netstat -ano | grep ':8080'
netstat -ano | grep ':8081'
```

En la línea `LISTENING`, el último número es el **PID**:

```bash
taskkill //PID <PID> //F
```

En **PowerShell** también podés usar `Get-NetTCPConnection -LocalPort 8080` o `Get-NetTCPConnection -LocalPort 8081`.

---

### Paso 5 — Levantar la aplicación Spring

En la raíz del proyecto, configurá el secret del client `pdyc` generado en Keycloak. En Git Bash:

```bash
export KEYCLOAK_CLIENT_SECRET="pegá-acá-el-secret"
```

En PowerShell:

```powershell
$env:KEYCLOAK_CLIENT_SECRET="pegá-acá-el-secret"
```

**Con MySQL (perfil por defecto):**

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

En **CMD / PowerShell**:

```bat
mvnw.cmd spring-boot:run
```

Esperá en el log la línea **`Started EventsApplication`**. La API queda en **`http://localhost:8081`** (o el puerto que configuraste).

**Comandos útiles:**

| Objetivo         | Comando                                                                                                           |
| ---------------- | ----------------------------------------------------------------------------------------------------------------- |
| Compilar y tests | `./mvnw test`                                                                                                     |
| Generar JAR      | `./mvnw package` → luego `java -jar target/events-0.0.1-SNAPSHOT.jar` (mismas reglas de MySQL/perfil que arriba). |

Si en la terminal aparece **`mvn: command not found`**, usá **`./mvnw`** (wrapper), no `mvn`.

---

### Paso 6 — Probar que responde (curl)

Con la app arriba, primero obtené un `access_token` desde Postman o desde Keycloak y luego usalo como Bearer Token:

```bash
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8081/admin/artists
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8081/admin/events
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8081/admin/users
```

Deberías ver JSON (lista de artistas, eventos o usuarios). Sin token válido, Spring Security responde **401**.

---

### Paso 7 — Probar con Postman

1. Abrí **Postman** → **Import** → subí **`API/Greater-Events.postman_collection.json`**.
2. Importá también **`API/Greater-Events-Local.postman_environment.json`**.
3. En el desplegable de entornos (arriba a la derecha), elegí **Greater Events — Local**.
4. Revisá **`baseUrl`** (por defecto `http://localhost:8081`) y completá **`keycloakClientSecret`** con el secret del client `pdyc`.
5. En la pestaña **Authorization** de la colección, Auth Type **OAuth 2.0**, usá **Get New Access Token**. La colección ya trae Authorization Code, callback `https://oauth.pstmn.io/v1/callback`, Auth URL, Access Token URL, Client ID `pdyc`, scope `openid` y client authentication en body.
6. Iniciá sesión con el usuario creado en Keycloak y usá el token obtenido para ejecutar las requests.
7. Las variables **`eventId`**, **`artistId`**, **`adminUserId`**, etc. están pensadas para el seed y la colección; leé la descripción de la colección en Postman para saber qué id usar en cada request.

---

## 8. Problemas frecuentes

| Síntoma                                                                | Qué hacer                                                                                                                                                       |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Communications link failure` / `Connection refused` hacia MySQL       | MySQL arriba, base `pdyc2026`, URL/usuario/clave en `application.properties`; o usá perfil `local` con H2.                                                      |
| `Port 8080 was already in use`                                         | Paso 4: liberar puerto para Keycloak o cambiar el puerto publicado en `docker-compose.yml`.                                                                     |
| `Port 8081 was already in use`                                         | Paso 4: liberar puerto o cambiar `server.port`.                                                                                                                 |
| `401 Unauthorized` `{"error":"Unauthorized: Full authentication ..."}` | No mandaste header `Authorization: Bearer ...`. Obtené un token nuevo desde Postman (Get New Access Token + Use Token).                                         |
| `401 Unauthorized` `{"error":"Unauthorized: ... decode the Jwt ..."}`  | El token está malformado, expiró o fue emitido por otro realm/issuer. Obtené uno nuevo.                                                                         |
| `403 Forbidden` `{"error":"Forbidden: ..."}`                           | TP4: el JWT es válido pero el usuario no tiene el rol requerido (`admin` para `/admin/**`, `user` para `/me/**`). Reasignar rol en Keycloak o usar otra cuenta. |
| `409 Conflict` `{"error":"Username is already registered."}`           | TP4: el `username` o `email` enviado a `POST /auth/register` ya existe. Elegir otro o borrar el usuario en Keycloak + DB local.                                 |
| `JwtDecoder` / `issuer-uri` falla al arrancar                          | Keycloak debe estar levantado y el realm `unnoba` creado antes de iniciar el backend.                                                                           |
| `/admin/users` devuelve error de Keycloak                              | Revisá `KEYCLOAK_CLIENT_SECRET`, que el client `pdyc` tenga `Service account roles`, y que el service account tenga `manage-users`.                             |
| `mvn: command not found`                                               | Usar `./mvnw` o `mvnw.cmd`.                                                                                                                                     |
| `Unable to access jarfile target/...`                                  | Ejecutar antes `./mvnw package`.                                                                                                                                |
| Docker: error de pipe / motor                                          | Iniciar Docker Desktop.                                                                                                                                         |
| `CREATE DATABASE` da “command not found” en bash                       | Eso es SQL: ejecutalo con el cliente `mysql -e "..."`, no pegado solo en bash.                                                                                  |
| PUT a artista **400** con solo datos del seed                          | Regla de negocio: no se edita un artista que ya participó en eventos; creá uno con **POST** y usá su `id` (la colección Postman lo explica con `artistIdFree`). |
