# Greater Events — Microservicios Spring Cloud (TP5)

Backend **REST** distribuido para una comunidad vinculada a **eventos musicales** y **artistas**, con perfil **administrador** (backoffice) y **usuario final** (catálogo público, follow de artistas, favoritos de eventos y notificaciones). La API tiene tres niveles de acceso:

- **Público** (sin token): catálogo de artistas y eventos vigentes, registro de usuarios.
- **Admin** (`/admin/**`, rol `admin`): backoffice de TP2 + gestión de usuarios admin (Keycloak).
- **Usuario final** (`/me/**`, rol `user`): seguir/dejar de seguir artistas, marcar favoritos, notificaciones.

**TP5** descompone el monolito TP4 en microservicios con **Eureka**, **Config Server**, **API Gateway** (JWT relay), **RabbitMQ** (notificaciones asíncronas) y **tres bases MySQL** independientes. El punto de entrada sigue siendo **`http://localhost:8081`**.

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
| API Gateway (Postman)  | `http://localhost:8081`                        |
| MySQL (Docker)         | `localhost:3307`, root/insecure                |
| Bases de datos         | `catalog_event`, `user_social`, `notification_db` |
| RabbitMQ UI            | `http://localhost:15672` (guest/guest)         |
| Eureka                 | `http://localhost:8761`                        |

Pasos (en orden, desde la raíz del repo, en Git Bash o PowerShell):

```bash
# 1) Levantar infra + microservicios (Docker + 6 JARs Spring)
./dev-support/start-tp5.sh

# 2) Smoke tests publicos (sin token, deben responder 200 con JSON)
curl -s http://localhost:8081/artists | head -c 300; echo
curl -s http://localhost:8081/events  | head -c 300; echo

# 3) Probar en Postman
# 3.1) Importar SOLO API/Greater-Events.postman_collection.json (autocontenida: trae secret, baseUrl y credenciales adentro; no necesita environment).
# 3.2) Carpeta "1 - Publicos": no requieren token (incluye POST /auth/register).
# 3.3) Carpeta "3 - Admin": pestana Authorization -> Get New Access Token -> login tp3-user/tp3pass.
# 3.4) Carpeta "2 - End-user": idem pero login tp4-user/tp4pass.
# 3.6) Para notificaciones: como tp4-user marcar favorito/seguir artista; como tp3-user cancelar/reprogramar evento;
#      volver a tp4-user y GET /me/notifications (RabbitMQ, puede tardar 1-2s).
```

> Guía expandida en la sección [01](#01-tp5--microservicios-spring-cloud). TP4 documentado en la sección [00](#00-tp4--usuarios-finales-roles-publico-y-notificaciones).
>
> **Nota de seguridad:** `pdyc-secret-dev`, `tp3pass` y `tp4pass` son valores de desarrollo locales para que esta entrega sea reproducible. En ambientes reales se rotan, se inyectan por variables de entorno y nunca se commitean.

---

## 01. TP5 — Microservicios Spring Cloud

Esta sección documenta la **Práctica 5**: descomposición del monolito TP4 en microservicios con Spring Cloud.

### 01.0 Respuestas conceptuales (consigna TP5)

**1. Microservicios.** Arquitectura que divide una aplicación en servicios pequeños, autónomos y desplegables de forma independiente. Cada uno encapsula una capacidad de negocio concreta, expone su API, persiste en su propia base de datos y se comunica con otros servicios por red (REST, mensajería). El objetivo es escalar, evolucionar y desplegar por dominio sin acoplar todo el sistema en un único monolito.

**2. Teorema CAP.** En un sistema distribuido con partición de red (P), solo se pueden garantizar simultáneamente dos de tres propiedades: **Consistencia** (todos los nodos ven los mismos datos), **Disponibilidad** (toda petición recibe respuesta) y **Tolerancia a particiones**. En microservicios las redes fallan con frecuencia; por eso el diseño suele elegir AP (disponibilidad + partición) o CP según el caso, y compensar con **consistencia eventual** donde haga falta.

**3. Tolerancia a fallos.** Implica que el sistema sigue operando (total o parcialmente) cuando un componente cae: timeouts, reintentos, circuit breakers, réplicas, colas persistentes. En microservicios es crítico porque hay más puntos de falla (red, broker, BD por servicio); un fallo aislado no debería tumbar todo el ecosistema.

**4. Resiliencia.** Capacidad del sistema de **absorber fallos y recuperarse** sin degradación permanente: detectar errores, aislar el servicio afectado, usar alternativas (fallback, caché, respuesta degradada) y restablecer el estado normal. Va más allá de “no caerse”: incluye observabilidad y autorreparación.

**5. Circuit Breaker.** Patrón que envuelve llamadas a servicios externos con un “interruptor”: tras N fallos consecutivos, el circuito **abre** y las llamadas fallan rápido sin saturar al dependiente; tras un tiempo entra en **half-open** para probar recuperación y, si responde bien, **cierra** de nuevo. Evita cascadas de timeout en microservicios.

**6. Consistencia eventual.** Tras una escritura, no todos los lectores ven el dato nuevo al instante, pero **convergen al mismo estado** si no hay más actualizaciones. Es el trade-off típico cuando se prioriza disponibilidad (p. ej. notificaciones vía RabbitMQ: el evento se cancela en catalog y la notificación aparece unos segundos después en notification-service).

**7. Patrones breves:**
- **Saga:** Orquesta una transacción de negocio que cruza varios servicios mediante pasos locales + compensaciones si un paso falla (ej.: registrar en Keycloak y revertir si falla la BD local).
- **Event Sourcing:** Persistir el estado como secuencia de eventos de dominio en lugar de sobrescribir filas; el estado actual se reconstruye reproduciendo eventos.
- **CQRS:** Separar modelos de **escritura** (commands) y **lectura** (queries); permite optimizar cada lado (p. ej. vistas desnormalizadas para listados sin cargar el agregado completo).

**8. Service Discovery.** Mecanismo para que un servicio encuentre instancias vivas de otro sin URLs fijas. El cliente (o el gateway/load balancer) consulta un registro y obtiene host/puerto actuales. Implementaciones comunes: **Netflix Eureka**, **Consul**, **etcd**, **Kubernetes DNS/Services**, **Spring Cloud Kubernetes**.

**9. API Gateway.** Punto de entrada único delante de los microservicios: enruta paths a servicios internos, termina TLS, valida JWT, aplica rate limiting y **Token Relay**. En este proyecto: Spring Cloud Gateway en `:8081` con rutas `lb://` vía Eureka.

### 01.1 Arquitectura

| Componente | Puerto | Responsabilidad |
| ---------- | ------ | --------------- |
| **api-gateway** | 8081 | Punto de entrada único; enruta y reenvía JWT (`TokenRelay`) |
| **catalog-event-service** | 8082 | Artistas, eventos, catálogo público, admin artists/events |
| **user-social-service** | 8083 | Registro, `/me/following`, `/me/favorite-events`, admin users |
| **notification-service** | 8084 | `/me/notifications`; consume RabbitMQ |
| **eureka-server** | 8761 | Service discovery |
| **config-server** | 8888 | Configuración centralizada (`config-repo/`) |
| **RabbitMQ** | 5672 / 15672 | Mensajería async para cambios de estado de eventos |
| **MySQL** | 3307 (host) | Tres BDs: `catalog_event`, `user_social`, `notification_db` |

Flujo de notificaciones (reemplaza el `@EventListener` del monolito TP4):

1. `catalog-event-service` confirma/reprograma/cancela un evento y publica `EventStateChangedMessage` en RabbitMQ **después del commit** de la transacción.
2. `notification-service` consume el mensaje, consulta destinatarios vía Feign a `user-social-service` (`/internal/notifications/recipients`).
3. Persiste notificaciones en su propia BD y las expone en `GET /me/notifications`.

### 01.2 Módulos Maven

```
greater-events/          (parent POM)
├── common-lib/          DTOs, enums, seguridad JWT compartida
├── eureka-server/
├── config-server/
├── api-gateway/
├── catalog-event-service/
├── user-social-service/
└── notification-service/
```

### 01.3 Cómo probar el TP5 end-to-end

```bash
# Arranque completo (Docker + realm Keycloak + compilar + 6 JARs)
./dev-support/start-tp5.sh

# 1) Catálogo público, sin token
curl -s http://localhost:8081/artists | head -c 400; echo
curl -s http://localhost:8081/events  | head -c 400; echo

# 2) Registro público
curl -s -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"jperez","email":"jperez@example.com","password":"Secret123!","first_name":"Juan","last_name":"Perez"}'

# 3) Postman — folder "End-user endpoints" (tp4-user/tp4pass):
#    POST /me/following  {"artist_id": 1}
#    POST /me/favorite-events {"event_id": 2}

# 4) Postman — folder "Admin endpoints" (tp3-user/tp3pass):
#    PUT /admin/events/2/canceled

# 5) Volver a "End-user endpoints":
#    GET /me/notifications  → notificaciones generadas vía RabbitMQ

# Tests unitarios / context-load (sin Docker):
./mvnw clean test
```

Para detener los microservicios Spring iniciados por el script:

```bash
# Git Bash / Linux / macOS
pkill -f 'eureka-server|config-server|catalog-event-service|user-social-service|notification-service|api-gateway' || true
docker compose down    # agregar -v para borrar volúmenes MySQL
```

### 01.4 Autenticación perimetral (Gateway) y autorización de grano fino (microservicios)

Implementación alineada con el **Anexo TP5** de la consigna. Clase: `api-gateway/.../GatewaySecurityConfig.java`.

El Gateway usa `SecurityWebFilterChain` (WebFlux/Netty) con:

- `.anyExchange().permitAll()` — no discrimina paths públicos/privados; solo enruta.
- `.oauth2ResourceServer(...jwt...)` — si viene `Authorization: Bearer`, valida firma/exp/`iss` contra JWKS de Keycloak.
- Filtro **TokenRelay** en cada ruta del gateway — reenvía el JWT validado al microservicio destino.

Cada microservicio tiene su propio `SecurityConfig` (Servlet) con reglas de dominio (`hasRole("admin")`, `hasRole("user")`, rutas públicas).

| Caso | Qué pasa |
| ---- | -------- |
| **A — Sin token** | Gateway deja pasar la petición anónima. El microservicio aplica su `SecurityConfig` y responde **401** si la ruta exige autenticación (ej. `GET /me/notifications`). |
| **B — Token válido** | Gateway valida JWT, `permitAll()` deja pasar, TokenRelay propaga el token. El microservicio decodifica, mapea roles (`KeycloakJwtAuthenticationConverter`) y autoriza. |
| **C — Token inválido/expirado** | Falla en la fase de **autenticación** del Gateway → **401** inmediato; el tráfico no llega a la red interna. |

Ventajas pedagógicas: el Gateway no se redeploya cuando un equipo agrega endpoints; el escudo perimetral filtra tokens corruptos; la lógica de permisos vive en el servicio de dominio.

### 01.5 Flujo práctico: “Feed de eventos” (`GET /me/following/events`)

Ejemplo de composición entre servicios con datos dispersos:

1. Cliente → **API Gateway** (`GET /me/following/events` + JWT).
2. Gateway → **user-social-service** (TokenRelay).
3. `EndUserService` lee los `artist_id` que sigue el usuario en la BD `user_social`.
4. Llama por **OpenFeign** a **catalog-event-service**: `GET /internal/events/upcoming?artistIds=1,3,...`.
5. Catalog filtra eventos `CONFIRMED`/`RESCHEDULED` futuros de esos artistas.
6. User-social combina y devuelve JSON al cliente.

Mismo patrón Feign en: `GET /me/following` (resuelve nombres de artistas), `POST /me/following` / `POST /me/favorite-events` (valida IDs en catalog).

### 01.6 Checklist consigna práctica

| Requisito consigna | Implementación en el repo |
| ------------------ | ------------------------- |
| Spring Cloud Gateway + JWT + TokenRelay | `api-gateway`, `config-repo/api-gateway.yml` |
| Eureka (service discovery) | `eureka-server`, clientes en cada servicio |
| Config Server centralizado | `config-server`, `config-repo/*.yml` |
| Keycloak centralizado (TP3/TP4) | `docker-compose.yml`, `keycloak/setup-realm.sh` |
| Gateway `permitAll` + Resource Server | `GatewaySecurityConfig` |
| Autorización fina en microservicios | `SecurityConfig` en catalog, user-social, notification |
| Catalog & Event Service + BD propia | `catalog-event-service`, BD `catalog_event` |
| User & Social Service + BD propia + Feign | `user-social-service`, BD `user_social`, `CatalogClient` |
| Notification Service + BD propia | `notification-service`, BD `notification_db` |
| Comunicación síncrona REST/Feign | `CatalogClient`, `UserSocialClient`, `/internal/**` |
| Comunicación asíncrona (broker) | RabbitMQ, `EventStateChangedPublisher` → `EventStateChangedListener` |
| Feed de eventos compuesto | `EndUserService.listUpcomingEventsForFollowedArtists()` |

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
  - Los usuarios consultan sus notificaciones con `GET /me/notifications` (o `?unread_only=true`) y marcan como leídas con `PATCH /me/notifications/{id}` body `{"is_read": true}` (Anexo TP4). Se mantiene `PUT /me/notifications/{id}/read` como alias sin body.

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
| `PATCH /me/notifications/{id}`         | Rol `user`   | Marca la notificación como leída. Body `{"is_read": true}` (Anexo TP4). Alias: `PUT /me/notifications/{id}/read`. |
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

### 00.4 Refresh Token (Anexo TP4)

Cuando el **access token** expira (por defecto a los pocos minutos en Keycloak), se puede
obtener uno nuevo **sin reingresar usuario y contraseña** usando el **refresh token** que
devolvió el flujo Authorization Code. El grant type es `refresh_token`.

**Desde Postman:**

1. En la pestaña *Authorization* del folder (Admin o End-user), al hacer *Get New Access
   Token* con Authorization Code, Postman guarda tanto el `access_token` como el
   `refresh_token`.
2. Cuando el access token expira, Postman ofrece *Refresh Token* en el administrador de
   tokens (o se puede usar el request **"Refresh access token"** del folder *Helpers*).
3. El request golpea el `keycloakTokenUrl` con `grant_type=refresh_token` y devuelve un
   nuevo `access_token` (y un `refresh_token` rotado) sin pedir credenciales.

**Equivalente con curl** (reemplazar `<REFRESH_TOKEN>` por el recibido en el login):

```bash
curl -s -X POST "http://localhost:8080/realms/unnoba/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token" \
  -d "client_id=pdyc" \
  -d "client_secret=pdyc-secret-dev" \
  -d "refresh_token=<REFRESH_TOKEN>"
# Respuesta: nuevo access_token + refresh_token (rotado) + expires_in.
```

> El refresh token tiene una vida más larga que el access token. Cuando también expira (o
> se revoca la sesión en Keycloak), recién ahí el usuario debe volver a autenticarse.

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

1. **Import** → subí **solo** `API/Greater-Events.postman_collection.json`. Es **autocontenida**: todas las variables viven dentro de la colección, no hace falta importar ningún environment. Valores ya cargados:

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

1. Abrí **Postman** → **Import** → subí **solo** **`API/Greater-Events.postman_collection.json`** (autocontenida; no hace falta environment).
2. Las variables **`baseUrl`** (`http://localhost:8081`) y **`keycloakClientSecret`** (`pdyc-secret-dev`) ya vienen cargadas dentro de la colección.
3. En la pestaña **Authorization** de la carpeta, Auth Type **OAuth 2.0**, usá **Get New Access Token**. La colección ya trae Authorization Code, callback `https://oauth.pstmn.io/v1/callback`, Auth URL, Access Token URL, Client ID `pdyc`, scope `openid` y client authentication en body.
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
