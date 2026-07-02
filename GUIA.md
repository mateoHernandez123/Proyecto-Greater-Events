# Guía — Greater Events (PDyC 2026)

**Participantes:** Mateo Hernandez y Felipe Lucero  
**Repositorio:** [Proyecto-Greater-Events](https://github.com/mateoHernandez123/Proyecto-Greater-Events)  
**Rama a defender:** `main` (incluye TP2 → TP5)

---

## 1. Formato de la defensa

Entre el **jueves 2 y martes 7 de julio** cada grupo tiene **20 minutos** para:

- Responder preguntas sobre la **implementación** (código, arquitectura, decisiones técnicas).
- Responder preguntas **conceptuales** vinculadas a las consignas de los trabajos prácticos.
- Demostrar que el proyecto **corre en vivo** y probar endpoints con **Postman**.

La defensa es **virtual** (Meet de clases prácticas).

### Qué conviene tener listo antes de entrar

| Item              | Detalle                                                                 |
| ----------------- | ----------------------------------------------------------------------- |
| Docker Desktop    | Corriendo (ícono "Docker is running")                                   |
| Proyecto clonado  | `git checkout main && git pull`                                         |
| Stack levantado   | `./dev-support/start-tp5.sh` terminó sin errores                        |
| Postman           | Colección + environment importados                                      |
| Pestañas abiertas | Eureka (`8761`), RabbitMQ (`15672`), Keycloak (`8080`) por si preguntan |
| Compañero         | Uno levanta/demo, el otro responde conceptos (o alternar)               |

### Distribución sugerida de los 20 minutos

| Minutos | Actividad                                                              |
| ------- | ---------------------------------------------------------------------- |
| 0–2     | Presentación: qué hace el sistema, evolución monolito → microservicios |
| 2–5     | Levantar o confirmar que está corriendo; smoke test público            |
| 5–12    | Demo Postman: público → admin → usuario final → notificaciones         |
| 12–20   | Preguntas del profesor (código + conceptos)                            |

---

## 2. Resumen del proyecto (elevator pitch)

**Greater Events** es una API REST para gestionar **eventos musicales** y **artistas**, con tres perfiles:

1. **Público** — catálogo y registro (sin token).
2. **Admin** — backoffice completo (`/admin/**`, rol `admin`).
3. **Usuario final** — seguir artistas, favoritos, notificaciones (`/me/**`, rol `user`).

**Evolución por práctica:**

| TP      | Entrega                                                                           | Arquitectura   |
| ------- | --------------------------------------------------------------------------------- | -------------- |
| **TP2** | CRUD artistas/eventos, reglas de negocio, MySQL, capas Spring                     | Monolito       |
| **TP3** | Keycloak, OAuth2 JWT, protección `/admin/**`                                      | Monolito + IdP |
| **TP4** | Roles `admin`/`user`, catálogo público, `/me/**`, notificaciones async in-process | Monolito       |
| **TP5** | Eureka, Config Server, API Gateway, RabbitMQ, 3 microservicios + 3 BDs            | Microservicios |

**Punto de entrada actual (TP5):** `http://localhost:8081` (API Gateway).

---

## 3. Arquitectura monolítica (TP2–TP4)

### 3.1 Qué es y cómo era nuestro monolito

Un **monolito** es una única aplicación desplegable que concentra todo: controladores, servicios, persistencia, seguridad y lógica de negocio en **un solo proceso** y, en nuestro caso, **una sola base MySQL** (`pdyc2026`).

```
Cliente (Postman)
       │
       ▼
┌──────────────────────────────────────┐
│     Spring Boot (puerto 8081)        │
│  web → service → repository → JPA    │
│  SecurityConfig + JwtDecoder         │
│  @EventListener notificaciones (TP4) │
└──────────────┬───────────────────────┘
               │
       ┌───────┴────────┐
       ▼                ▼
    MySQL           Keycloak
   (pdyc2026)        (IdP)
```

### 3.2 Capas del monolito (patrón que se mantiene dentro de cada microservicio)

| Capa         | Responsabilidad                                   | Ejemplos                                        |
| ------------ | ------------------------------------------------- | ----------------------------------------------- |
| `web`        | HTTP, validación de entrada, códigos de respuesta | `EventAdminController`, `PublicEventController` |
| `service`    | Reglas de negocio, transacciones                  | `EventService`, `EndUserService`                |
| `repository` | Acceso a datos (Spring Data JPA)                  | `EventRepository`, `UserRepository`             |
| `model`      | Entidades JPA                                     | `Artist`, `Event`, `User`, `Notification`       |
| `dto`        | Contrato JSON (records, snake_case)               | `EventCreateRequest`, `ArtistResponse`          |
| `config`     | Beans, seguridad, carga de datos                  | `SecurityConfig`, `SampleDataLoader`            |
| `exception`  | Errores de dominio                                | `BusinessRuleException` → 400                   |

### 3.3 Reglas de negocio clave (TP2 — suelen preguntar)

**Estados de evento:** `tentative` → `confirmed` → `rescheduled` → `cancelled`

| Estado                      | Qué se puede hacer                                 |
| --------------------------- | -------------------------------------------------- |
| `tentative`                 | Editar, borrar, agregar/quitar artistas, confirmar |
| `confirmed` / `rescheduled` | Solo reprogramar o cancelar                        |
| `cancelled`                 | Estado terminal                                    |

**Artistas:**

- Si **nunca** participó en un evento → se puede editar o borrar físicamente.
- Si **ya participó** → no se edita; el DELETE lo **desactiva** (`active = false`).
- Artista inactivo **no** se puede agregar a eventos nuevos.

**Catálogo público (TP4):**

- Solo artistas `active = true`.
- Solo eventos `confirmed` o `rescheduled` con `start_date > now`.
- Detalle de evento `tentative` → **404** (no se expone al público).

### 3.4 Seguridad monolítica (TP3–TP4)

**Keycloak** actúa como **Identity Provider (IdP)**:

- Emite **JWT** con claim `realm_access.roles`.
- El backend valida firma/issuer/exp localmente (`JwtDecoder` + JWKS).
- `KeycloakJwtAuthenticationConverter` mapea roles a `ROLE_admin` / `ROLE_user`.

**Tres niveles en `SecurityFilterChain`:**

1. Público: `/auth/register`, `/artists/**`, `/events/**`
2. Admin: `/admin/**` → `hasRole("admin")`
3. Usuario: `/me/**` → `hasRole("user")`

**Errores uniformes:** siempre `{"error":"mensaje"}` con códigos 400, 401, 403, 404, 409.

### 3.5 Notificaciones en el monolito (TP4)

Flujo **síncrono-asíncrono dentro del mismo proceso**:

1. Admin confirma/reprograma/cancela evento → `EventService`.
2. Se publica `EventStateChangedEvent` (ApplicationEvent).
3. `NotificationService` con `@Async` + `@EventListener` crea notificaciones para:
   - Usuarios con el evento en **favoritos** (`FAVORITE_EVENT`).
   - Usuarios que **siguen** algún artista del lineup (`FOLLOWED_ARTIST`).
   - Si aplica por ambas → gana `FAVORITE_EVENT`.

**Limitación que motivó TP5:** todo vive en la misma JVM y BD; no escala ni se despliega por separado.

---

## 4. Arquitectura de microservicios (TP5)

### 4.1 Diagrama general

```
                    Cliente (Postman)
                           │
                           ▼
                 ┌─────────────────┐
                 │  API Gateway    │ :8081
                 │  JWT + TokenRelay│
                 └────────┬────────┘
                          │ (Eureka lb://)
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
┌───────────────┐ ┌───────────────┐ ┌──────────────────┐
│ catalog-event │ │ user-social   │ │ notification     │
│   :8082       │ │   :8083       │ │   :8084          │
│ BD catalog_   │ │ BD user_      │ │ BD notification_ │
│    event      │ │    social     │ │    db            │
└───────┬───────┘ └───────┬───────┘ └────────▲─────────┘
        │                 │                  │
        │    Feign REST   │◄─────────────────┘
        └────────────────►│         RabbitMQ (async)
                          │
              Eureka :8761  │  Config Server :8888
                          ▼
                     Keycloak :8080
                     MySQL :3307
```

### 4.2 Responsabilidad de cada componente

| Componente                | Puerto       | Qué hace                                                      |
| ------------------------- | ------------ | ------------------------------------------------------------- |
| **eureka-server**         | 8761         | Service discovery — registra instancias vivas                 |
| **config-server**         | 8888         | Config centralizada desde `config-repo/`                      |
| **api-gateway**           | 8081         | Entrada única; enruta por path; valida JWT; TokenRelay        |
| **catalog-event-service** | 8082         | Artistas, eventos, admin artists/events, catálogo público     |
| **user-social-service**   | 8083         | Registro, `/me/following`, `/me/favorite-events`, admin users |
| **notification-service**  | 8084         | `/me/notifications`; consume RabbitMQ                         |
| **RabbitMQ**              | 5672 / 15672 | Mensajería async entre catalog y notification                 |
| **MySQL**                 | 3307         | 3 bases independientes (database per service)                 |
| **Keycloak**              | 8080         | Autenticación centralizada (igual que TP3/TP4)                |

### 4.3 Enrutamiento del Gateway (`config-repo/api-gateway.yml`)

| Path                                         | Microservicio destino |
| -------------------------------------------- | --------------------- |
| `/artists/**`, `/events/**`                  | catalog-event-service |
| `/admin/artists/**`, `/admin/events/**`      | catalog-event-service |
| `/auth/**`                                   | user-social-service   |
| `/me/following/**`, `/me/favorite-events/**` | user-social-service   |
| `/admin/users/**`                            | user-social-service   |
| `/me/notifications/**`                       | notification-service  |

Todas las rutas usan **`TokenRelay`** para reenviar el JWT al servicio interno.

### 4.4 Autenticación perimetral vs autorización fina (Anexo TP5)

| Caso                            | Comportamiento                                               |
| ------------------------------- | ------------------------------------------------------------ |
| **Sin token** en ruta protegida | Gateway deja pasar → microservicio responde **401**          |
| **Token válido**                | Gateway valida → TokenRelay → microservicio autoriza por rol |
| **Token inválido/expirado**     | Gateway responde **401** inmediato (no llega al servicio)    |

**Gateway:** `permitAll()` + Resource Server (solo valida JWT si viene).  
**Microservicios:** `SecurityConfig` propio con reglas de dominio (`hasRole("admin")`, etc.).

### 4.5 Comunicación entre servicios

**Síncrona (REST + OpenFeign):**

- `user-social` → `catalog`: validar artista/evento, resolver nombres, feed de eventos.
- `notification` → `user-social`: `GET /internal/notifications/recipients`.

**Asíncrona (RabbitMQ):**

1. `catalog-event-service` confirma/reprograma/cancela → publica `EventStateChangedMessage` **post-commit**.
2. `notification-service` consume el mensaje.
3. Consulta destinatarios vía Feign a `user-social`.
4. Persiste en su BD y expone en `GET /me/notifications`.

**Consistencia eventual:** la notificación puede aparecer 1–2 segundos después del cambio de estado del evento.

### 4.6 Ejemplo para explicar en voz alta: `GET /me/following/events`

1. Cliente → Gateway con JWT de `tp4-user`.
2. Gateway → `user-social-service`.
3. Lee `artist_id` seguidos en BD `user_social`.
4. Feign → `catalog-event-service` `/internal/events/upcoming?artistIds=1,3,...`.
5. Catalog filtra eventos confirmados/reprogramados futuros.
6. User-social devuelve JSON al cliente.

Demuestra **composición de datos** entre servicios con BD separadas.

### 4.7 Módulos Maven (TP5)

```
greater-events/          (parent POM)
├── common-lib/          DTOs, enums, JWT compartido
├── eureka-server/
├── config-server/
├── api-gateway/
├── catalog-event-service/
├── user-social-service/
└── notification-service/
```

---

## 5. Preguntas conceptuales frecuentes (por TP)

### TP2 — REST y dominio

| Pregunta                                   | Respuesta corta                                                   |
| ------------------------------------------ | ----------------------------------------------------------------- |
| ¿Por qué REST?                             | Recursos con sustantivos, verbos HTTP semánticos, stateless, JSON |
| ¿Por qué DTOs y no entidades en la API?    | Desacoplar persistencia del contrato HTTP; validar entrada        |
| ¿Por qué snake_case en JSON?               | Convención del proyecto/consigna; camelCase en Java               |
| ¿Qué es `@ManyToMany` en Event-Artist?     | Tabla intermedia `event_artists`; un evento tiene varios artistas |
| ¿Por qué no borrar artistas con historial? | Integridad referencial y trazabilidad de eventos pasados          |

### TP3 — IAM y OAuth2

| Pregunta                           | Respuesta corta                                                  |
| ---------------------------------- | ---------------------------------------------------------------- |
| ¿Qué es IAM?                       | Gestión de identidades, autenticación, autorización, roles       |
| ¿Qué es un IdP?                    | Servicio que autentica y emite tokens confiables (Keycloak)      |
| ¿OAuth2 vs autenticación propia?   | Delegamos credenciales al IdP; la API solo valida tokens         |
| ¿Authorization Code?               | Flujo con redirección; el cliente nunca ve la password en la API |
| ¿Por qué JWT y no opaque token?    | Validación local sin llamar al IdP en cada request               |
| ¿Client Credentials?               | Service account de `pdyc` para Admin API de Keycloak             |
| ¿Por qué Direct Access Grants OFF? | Consigna TP3: login solo vía Authorization Code (Postman)        |
| ¿Qué valida el Resource Server?    | Firma (JWKS), issuer, expiración, opcionalmente roles            |

### TP4 — Roles, público, notificaciones

| Pregunta                                    | Respuesta corta                                                         |
| ------------------------------------------- | ----------------------------------------------------------------------- |
| ¿Diferencia admin vs user?                  | Realm roles en Keycloak; mapeados a `ROLE_*` en Spring                  |
| ¿Por qué User local si ya está en Keycloak? | Datos de dominio (follows, favoritos) propios de la app                 |
| ¿Qué es autoprovisioning?                   | `CurrentUserService` crea fila local al primer hit a `/me/**`           |
| ¿Saga en registro?                          | Crear en Keycloak → asignar rol → persistir local; rollback si falla BD |
| ¿Por qué @Async para notificaciones?        | No bloquear la respuesta HTTP del admin al confirmar evento             |
| ¿Refresh token?                             | Renovar access token sin re-login; grant `refresh_token`                |

### TP5 — Microservicios y distribuidos

| Pregunta                               | Respuesta corta                                                                              |
| -------------------------------------- | -------------------------------------------------------------------------------------------- |
| ¿Microservicios?                       | Servicios pequeños, autónomos, desplegables independientes, BD propia                        |
| ¿Teorema CAP?                          | Con partición de red solo 2 de 3: Consistencia, Disponibilidad, Tolerancia a particiones     |
| ¿Consistencia eventual?                | Escritura en catalog + mensaje RabbitMQ; lectura en notification unos segundos después       |
| ¿Service Discovery?                    | Eureka — el gateway resuelve `lb://catalog-event-service` sin URL fija                       |
| ¿API Gateway?                          | Punto de entrada único: routing, JWT, TokenRelay                                             |
| ¿Config Server?                        | Config externa versionada en `config-repo/`                                                  |
| ¿Por qué RabbitMQ y no @EventListener? | Desacopla procesos; notification puede escalar/reiniciarse independiente                     |
| ¿Circuit Breaker?                      | Tras N fallos, corta llamadas rápido para evitar cascada (patrón; Resilience4j si lo usaran) |
| ¿Saga?                                 | Transacción distribuida con pasos + compensación (ej. registro Keycloak + BD)                |
| ¿CQRS?                                 | Separar modelo de escritura y lectura (ej. feed compuesto vs agregado completo)              |
| ¿Database per service?                 | `catalog_event`, `user_social`, `notification_db` — sin FK cruzadas                          |

---

## 6. Preguntas de implementación / código (qué saber señalar)

| Tema                               | Dónde está en el repo                                                  |
| ---------------------------------- | ---------------------------------------------------------------------- |
| Reglas de ciclo de vida del evento | `catalog-event-service/.../EventService.java`                          |
| Publicación a RabbitMQ post-commit | `EventStateChangedPublisher.java`                                      |
| Consumer de notificaciones         | `EventStateChangedListener.java`                                       |
| Feign catalog ← user-social        | `CatalogClient.java`                                                   |
| Feign user-social ← notification   | `UserSocialClient.java`                                                |
| Conversor de roles JWT             | `common-lib/.../KeycloakJwtAuthenticationConverter.java`               |
| Seguridad Gateway                  | `api-gateway/.../GatewaySecurityConfig.java`                           |
| Seguridad por servicio             | `*/config/SecurityConfig.java` en cada microservicio                   |
| Setup Keycloak idempotente         | `keycloak/setup-realm.sh`                                              |
| Datos de demo                      | `SampleDataLoader.java` (6 artistas, 5 eventos si BD vacía)            |
| Errores JSON                       | `ApiExceptionHandler`, `JsonAuthEntryPoint`, `JsonAccessDeniedHandler` |

**Frase útil:** "La lógica de negocio no cambió respecto al monolito; cambió **dónde vive** y **cómo se comunica**."

---

## 7. Credenciales y URLs (tabla rápida)

| Recurso               | Valor                                                             |
| --------------------- | ----------------------------------------------------------------- |
| API Gateway (Postman) | `http://localhost:8081`                                           |
| Keycloak admin        | `http://localhost:8080/admin/master/console/` → `admin` / `admin` |
| Realm                 | `unnoba`                                                          |
| Client ID             | `pdyc`                                                            |
| Client secret         | `pdyc-secret-dev`                                                 |
| Usuario admin         | `tp3-user` / `tp3pass` (rol `admin`)                              |
| Usuario final         | `tp4-user` / `tp4pass` (rol `user`)                               |
| MySQL                 | `localhost:3307`, `root` / `insecure`                             |
| Bases TP5             | `catalog_event`, `user_social`, `notification_db`                 |
| Eureka                | `http://localhost:8761`                                           |
| RabbitMQ UI           | `http://localhost:15672` → `guest` / `guest`                      |

---

## 8. Cómo levantar el proyecto completo (TP5)

### 8.1 Prerrequisitos

| Herramienta           | Verificación                                   |
| --------------------- | ---------------------------------------------- |
| JDK 17+               | `java -version`                                |
| Docker Desktop        | `docker --version` (debe estar **corriendo**)  |
| Git Bash o PowerShell | Terminal en la raíz del repo                   |
| Python 3              | `python --version` (usa el script de Keycloak) |
| Postman               | Para la demo                                   |

Maven **no** hace falta instalado: usamos `./mvnw`.

### 8.2 Pasos (desde cero)

```bash
# 1) Clonar y actualizar
git clone https://github.com/mateoHernandez123/Proyecto-Greater-Events.git
cd Proyecto-Greater-Events
git checkout main
git pull origin main

# 2) Asegurar Docker Desktop corriendo, luego:
./dev-support/start-tp5.sh
```

El script hace automáticamente:

1. `docker compose up -d` (MySQL, RabbitMQ, Keycloak, Postgres).
2. Espera MySQL + Keycloak.
3. Crea las 3 bases si no existen.
4. Ejecuta `keycloak/setup-realm.sh`.
5. Compila con `./mvnw -DskipTests package`.
6. Arranca en orden: Eureka → Config → catalog, user-social, notification → Gateway.
7. Espera health en `http://localhost:8081/artists`.

**Duración estimada:** 3–5 minutos (primera vez puede tardar más por imágenes Docker).

### 8.3 Smoke tests por terminal

```bash
# Público — deben responder 200
curl -s http://localhost:8081/artists | head -c 300; echo
curl -s http://localhost:8081/events  | head -c 300; echo

# Protegido sin token — debe responder 401
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/admin/artists
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/me/notifications

# Infra
curl -s -o /dev/null -w "Eureka: %{http_code}\n" http://localhost:8761/
curl -s -o /dev/null -w "Keycloak: %{http_code}\n" http://localhost:8080/realms/unnoba/.well-known/openid-configuration
```

### 8.4 Ver logs si algo falla

```bash
tail -f /tmp/greater-events-*.log
```

| Log                                        | Servicio      |
| ------------------------------------------ | ------------- |
| `greater-events-eureka-server.log`         | Eureka        |
| `greater-events-config-server.log`         | Config Server |
| `greater-events-catalog-event-service.log` | Catalog       |
| `greater-events-user-social-service.log`   | User Social   |
| `greater-events-notification-service.log`  | Notification  |
| `greater-events-api-gateway.log`           | Gateway       |

### 8.5 Detener todo

```bash
# Matar JARs Spring (Git Bash)
pkill -f 'eureka-server|config-server|catalog-event-service|user-social-service|notification-service|api-gateway' || true

# Bajar Docker
docker compose down        # conserva datos
docker compose down -v     # borra volúmenes MySQL/Keycloak
```

### 8.6 Problemas frecuentes

| Síntoma                               | Solución                                                                                                       |
| ------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `dockerDesktopLinuxEngine` pipe error | Abrir Docker Desktop y esperar "Docker is running"                                                             |
| Puerto 8081 ocupado                   | `netstat -ano \| grep ':8081'` → `taskkill //PID <pid> //F`                                                    |
| Keycloak no responde                  | Esperar 30–60s; reintentar `setup-realm.sh`                                                                    |
| `/events` vacío en público            | Normal si no hay eventos confirmados/reprogramados futuros; confirmar uno como admin                           |
| 401 en admin/me                       | Obtener token nuevo en Postman (Get New Access Token)                                                          |
| 403 Forbidden                         | Token válido pero rol incorrecto (`tp3-user` para admin, `tp4-user` para `/me/**`)                             |
| Notificaciones no aparecen            | Esperar 1–2s (RabbitMQ); verificar que tp4-user siguió artista o marcó favorito **antes** del cambio de estado |

---

## 9. Configuración de Postman

### 9.1 Importar archivos

1. Postman → **Import**.
2. Seleccionar **un solo archivo**: `API/Greater-Events.postman_collection.json`.
3. Listo — es **autocontenida**: todas las variables viven dentro de la colección, **no hace falta importar ni seleccionar ningún environment**.

### 9.2 Variables (ya precargadas dentro de la colección)

| Variable                              | Valor típico            | Uso                     |
| ------------------------------------- | ----------------------- | ----------------------- |
| `baseUrl`                             | `http://localhost:8081` | Todas las requests      |
| `keycloakClientSecret`                | `pdyc-secret-dev`       | OAuth2                  |
| `adminUsername` / `adminPassword`     | `tp3-user` / `tp3pass`  | Folder Admin            |
| `endUserUsername` / `endUserPassword` | `tp4-user` / `tp4pass`  | Folder End-user         |
| `eventId`                             | `1`                     | Evento tentative (seed) |
| `eventIdConfirmed`                    | `2`                     | Evento confirmado       |
| `followArtistId`                      | `1`                     | Seguir artista          |
| `favoriteEventId`                     | `2`                     | Marcar favorito         |

### 9.3 Cómo obtener token OAuth2

**Folder "3 — Admin (rol admin, OAuth2)":**

1. Click en el folder → pestaña **Authorization**.
2. Type: **OAuth 2.0** (heredado de la colección).
3. **Get New Access Token** → login Keycloak con `tp3-user` / `tp3pass`.
4. **Use Token**.

**Folder "2 — End-user (rol user, OAuth2)":**

- Mismo flujo pero login con `tp4-user` / `tp4pass`.

**Folder "4 — Errores y seguridad (casos negativos)":**

- Casos negativos con test automático del status: 401, 403, 404, 409 y 400. Para los 403 hay que loguear con el rol equivocado (cada request indica cuál).

> Si expira el token: **Get New Access Token** de nuevo, o usar **Helpers → Refresh access token**.

---

## 10. Guía de pruebas Postman (demo para la defensa)

Ejecutar **en este orden** para mostrar el sistema completo.

### Escenario A — Endpoints públicos (sin token)

Folder: **Public endpoints (sin token)**

| #   | Request                    | Qué demuestra                          | Resultado esperado                      |
| --- | -------------------------- | -------------------------------------- | --------------------------------------- |
| A1  | **List active artists**    | Catálogo público TP4                   | 200, JSON con artistas activos          |
| A2  | **List upcoming events**   | Solo confirmados/reprogramados futuros | 200 (puede ser `[]` si no hay vigentes) |
| A3  | **Get event by id**        | Detalle público (`publicEventId=2`)    | 200 con detalle, o 404 si tentative     |
| A4  | **Artist upcoming events** | Eventos por artista                    | 200                                     |
| A5  | **Register end user**      | Registro público (opcional)            | 201; 409 si username/email duplicado    |

### Escenario B — Admin (rol admin, token tp3-user)

Folder: **Admin endpoints (rol admin)** → obtener token admin primero.

| #   | Request                           | Qué demuestra                       | Resultado esperado               |
| --- | --------------------------------- | ----------------------------------- | -------------------------------- |
| B1  | **List artists**                  | CRUD TP2 protegido                  | 200                              |
| B2  | **List events**                   | Ver estados (tentative, confirmed…) | 200                              |
| B3  | **Get event by id** (`eventId=1`) | Evento tentative del seed           | 200, state tentative             |
| B4  | **Create artist**                 | Alta de artista                     | 201                              |
| B5  | **Create event**                  | Nuevo tentative                     | 201                              |
| B6  | **Add artist to event**           | Grilla en tentative                 | 201/204                          |
| B7  | **Confirm event**                 | Transición tentative → confirmed    | 200; dispara RabbitMQ (TP5)      |
| B8  | **Reschedule event**              | Nueva fecha futura                  | 200                              |
| B9  | **Cancel event**                  | Cancelación                         | 200                              |
| B10 | **List admin users**              | Integración Keycloak Admin API      | 200                              |
| B11 | **Create admin user**             | Alta admin + rol automático         | 201; copiar UUID a `adminUserId` |

**Regla para demo de artistas:**

- **Update artist** → usar `artistIdFree` (artista sin historial).
- **Update** sobre artista del seed con eventos → **400** (regla de negocio).

### Escenario C — Usuario final (rol user, token tp4-user)

Folder: **End-user endpoints (rol user)** → token con `tp4-user`.

| #   | Request                                   | Qué demuestra                  | Resultado esperado |
| --- | ----------------------------------------- | ------------------------------ | ------------------ |
| C1  | **Follow artist**                         | `POST /me/following`           | 201                |
| C2  | **List following**                        | Lectura de follows             | 200 con artista    |
| C3  | **Add favorite event**                    | Favorito (`favoriteEventId=2`) | 201                |
| C4  | **List favorite events**                  | Favoritos vigentes             | 200                |
| C5  | **Upcoming events from followed artists** | Composición Feign TP5          | 200 con eventos    |
| C6  | **Unfollow artist**                       | DELETE                         | 204                |
| C7  | **Remove favorite event**                 | DELETE                         | 204                |

### Escenario D — Notificaciones end-to-end (el más importante en TP5)

**Preparación (como tp4-user):**

1. **Follow artist** con `followArtistId=1` (artista en lineup del evento).
2. **Add favorite event** con `favoriteEventId=2`.

**Disparo (como tp3-user):**

3. Cambiar al folder Admin → token admin.
4. **Reschedule event** o **Cancel event** sobre `eventIdConfirmed=2`.

**Verificación (volver a tp4-user):**

5. Esperar **1–2 segundos** (RabbitMQ).
6. **List notifications** → deben aparecer notificaciones.
7. **List unread notifications** → filtro `unread_only=true`.
8. **Mark notification as read (PATCH)** con `{"is_read": true}`.
9. (Opcional) **Delete notification**.

**Qué explicar mientras corre:**

> "El catalog publica en RabbitMQ después del commit. Notification consume, pregunta a user-social quién debe recibir notificación, y persiste en su propia base."

### Escenario E — Errores de seguridad (si piden validar)

| Request                 | Token                | Resultado |
| ----------------------- | -------------------- | --------- |
| `GET /admin/artists`    | ninguno              | 401       |
| `GET /admin/artists`    | tp4-user (rol user)  | 403       |
| `GET /me/notifications` | tp3-user (rol admin) | 403       |
| `GET /me/notifications` | ninguno              | 401       |

---

## 11. Script de demo de 10 minutos (lectura en voz alta)

```
[INTRO - 1 min]
"Greater Events gestiona eventos musicales. Empezó como monolito Spring Boot (TP2),
 agregamos Keycloak (TP3), usuarios finales y notificaciones in-process (TP4),
 y en TP5 lo partimos en microservicios con Eureka, Gateway, Config Server y RabbitMQ.
 El entry point sigue siendo localhost:8081 pero ahora es el API Gateway."

[DEMO PÚBLICO - 1 min]
"Sin token listo artistas activos y eventos vigentes."
→ GET /artists, GET /events

[DEMO ADMIN - 3 min]
"Con tp3-user obtengo token OAuth2 Authorization Code. El Gateway valida el JWT
 y lo reenvía con TokenRelay al microservicio catalog."
→ GET /admin/events, Confirm event (o Cancel)

[DEMO USUARIO - 2 min]
"Con tp4-user sigo un artista y marco un favorito. El feed de eventos
 compone datos de user-social y catalog vía Feign."
→ POST /me/following, GET /me/following/events

[DEMO NOTIFICACIONES - 2 min]
"Cuando el admin reprograma/cancela, catalog publica en RabbitMQ.
 Notification consume, consulta destinatarios a user-social, y persiste."
→ Cancel event (admin) → GET /me/notifications (user)

[CIERRE - 1 min]
"Cada microservicio tiene su BD. Eureka resuelve instancias. Config centralizado.
 Keycloak sigue siendo el IdP único. ¿Preguntas?"
```

---

## 12. Datos de seed (referencia)

Al primer arranque, `SampleDataLoader` crea si la tabla de artistas está vacía:

- **6 artistas** activos (IDs 1–6).
- **5 eventos** (IDs 1–5) en distintos estados.

Variables Postman alineadas al seed:

| Variable             | ID  | Notas                                      |
| -------------------- | --- | ------------------------------------------ |
| `eventId`            | 1   | Tentative — editable                       |
| `eventIdConfirmed`   | 2   | Confirmado — para favoritos/notificaciones |
| `eventIdRescheduled` | 3   | Reprogramado                               |
| `eventIdCancelled`   | 4   | Cancelado                                  |
| `artistIdInLineup`   | 1   | Artista con historial — no editable        |
| `artistIdFree`       | 7+  | Crear con POST si no existe                |

---

## 13. Checklist pre-defensa

- [ ] `git checkout main && git pull`
- [ ] Docker Desktop corriendo
- [ ] `./dev-support/start-tp5.sh` completó con "Listo. Punto de entrada Postman: http://localhost:8081"
- [ ] `curl http://localhost:8081/artists` → 200
- [ ] Eureka muestra 4 apps UP (catalog, user-social, notification, gateway)
- [ ] Postman: colección `Greater-Events.postman_collection.json` importada (autocontenida, sin environment)
- [ ] Token admin obtenido (tp3-user)
- [ ] Token user obtenido (tp4-user)
- [ ] Escenario D (notificaciones) probado al menos una vez antes de la defensa
- [ ] Ambos integrantes saben explicar monolito vs microservicios
- [ ] Ambos integrantes saben ubicar `EventService`, `GatewaySecurityConfig` y flujo RabbitMQ

---

## 14. Referencias en el repo

| Documento / carpeta        | Contenido                           |
| -------------------------- | ----------------------------------- |
| `README.md`                | Documentación completa del proyecto |
| `API/`                     | Colección y environment Postman     |
| `dev-support/start-tp5.sh` | Script de arranque TP5              |
| `config-repo/`             | Configuración centralizada          |
| `keycloak/setup-realm.sh`  | Setup idempotente del realm         |
| `docker-compose.yml`       | Infra Docker                        |

---

_Última actualización: junio 2026 — rama `main` (TP5 microservicios)._
