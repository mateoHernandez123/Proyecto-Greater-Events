# Greater Events — API REST (Spring Boot)

Backend **REST** para gestionar una comunidad vinculada a **eventos musicales** y **artistas**: relación **muchos a muchos**, reglas de ciclo de vida del evento y de alta/baja de artistas. Toda la API pública del servicio vive bajo el prefijo **`/admin/`**.

Este documento está redactado como **documentación de producto / proyecto de software** (qué hace el sistema, cómo está armado, cómo ejecutarlo y qué conviene versionar). Si participás de un proceso de evaluación formal, al final hay un **checklist de contenidos del repositorio** que suele pedirse al cerrar una entrega; las reglas concretas de plataforma o nombres de repo las define cada organización.

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

| Área | Contenido |
| ---- | ----------- |
| API | Endpoints REST bajo `/admin/artists` y `/admin/events` según la especificación funcional (ver tabla más abajo). |
| Corrección de ruta | La especificación original citaba `DELETE .../artist/:song_id`; en este código el recurso coherente es **`DELETE /admin/events/{id}/artists/{artistId}`**. |
| Capas | Repositorios Spring Data, servicios `@Service`, controladores `@RestController`, DTOs con **Java records** y validación Jakarta. |
| Errores HTTP | `ApiExceptionHandler` mapea reglas de negocio a **400**, no encontrado a **404**, con cuerpo `{"error":"mensaje"}`. |
| Datos demo | `SampleDataLoader` (solo si no hay artistas al arrancar; desactivado con perfil `test`). |
| Herramientas | Maven Wrapper (`mvnw`), `docker-compose.yml`, script opcional `dev-support/start-mysql.ps1` (Windows), colección Postman en **`API/`**. |
| Perfil opcional | **`local`**: H2 en memoria para ejecutar sin MySQL (`application-local.properties`). |

---

## 3. Cómo está organizado el código (capas y responsabilidades)

### Paquete `...events.model`

| Clase / tipo | Rol |
| ------------ | --- |
| **`Artist`** | Entidad JPA: `name`, `genre` (enum), `active`. |
| **`Event`** | Entidad JPA: `name`, `description`, `startDate`, `state`, relación **`@ManyToMany`** con `Artist` (tabla intermedia `event_artists`). |
| **`Genre`**, **`EventState`** | Enumeraciones persistidas como string; exponen valores en **minúsculas** en JSON y aceptan el mismo formato en query/body. |

### Paquete `...repository`

Interfaces **Spring Data JPA** (`ArtistRepository`, `EventRepository`): consultas por género/estado, carga de eventos con artistas para listados y detalle, y comprobaciones usadas por reglas de negocio (por ejemplo si un artista ya figura en algún evento).

### Paquete `...service`

| Servicio | Comportamiento |
| -------- | --------------- |
| **`ArtistService`** | Listado con filtro opcional de género; alta; actualización solo si el artista **no** tiene eventos; borrado físico si no tiene historial, si no **desactivación**. |
| **`EventService`** | Listados con filtro de estado; detalle; CRUD de evento acotado al estado **tentative**; alta/baja de artistas en grilla solo en **tentative**; transiciones **confirm / reschedule / cancel** con todas las validaciones de fechas y estados; usa **`Clock`** inyectado para fechas “ahora” testeables. |

### Paquete `...web`

| Componente | Rol |
| ---------- | --- |
| **`ArtistAdminController`**, **`EventAdminController`** | Mapean verbos HTTP y rutas `/admin/...`; códigos **201** en POST de creación, **204** en DELETE de evento; `DELETE` de artista devuelve **200** o **204** según el caso. |
| **`StringToGenreConverter`**, **`StringToEventStateConverter`** | Conversión de query params (`genre`, `state`) a enums. |
| **`ApiExceptionHandler`** | `@RestControllerAdvice` centraliza respuestas de error. |

### Paquete `...dto`

**Records** inmutables para cuerpos y respuestas JSON (`ArtistCreateRequest`, `EventDetailResponse`, etc.), con nombres en **`snake_case`** donde aplica (`start_date`, `artist_id`, …) para alinear con la especificación de la API.

### Paquete `...config`

**`ClockConfig`**: bean `Clock` del sistema. **`SampleDataLoader`**: datos iniciales si la BD está vacía.

### Paquete `...exception`

**`BusinessRuleException`**, **`ResourceNotFoundException`**: errores de dominio traducidos a HTTP por el advice.

---

## 4. Referencia de endpoints

| Método y ruta | Descripción breve |
| ------------- | ------------------- |
| `GET /admin/artists` | Lista artistas; query opcional `genre`. |
| `GET /admin/artists/{id}` | Detalle de un artista. |
| `POST /admin/artists` | Crea artista (body: `name`, `genre`). |
| `PUT /admin/artists/{id}` | Actualiza nombre y género (solo sin historial en eventos). |
| `DELETE /admin/artists/{id}` | Borra o desactiva según historial. |
| `GET /admin/events` | Lista resumida; query opcional `state`. |
| `GET /admin/events/{id}` | Detalle con artistas. |
| `POST /admin/events` | Crea evento tentative sin artistas (`name`, `start_date`, `description`). |
| `PUT /admin/events/{id}` | Actualiza datos solo si **tentative**. |
| `DELETE /admin/events/{id}` | Borra solo si **tentative**. |
| `POST /admin/events/{id}/artists` | Body `artist_id`; solo **tentative**; artista activo. |
| `DELETE /admin/events/{id}/artists/{artistId}` | Quita de la grilla; solo **tentative**. |
| `PUT /admin/events/{id}/confirmed` | Confirma desde **tentative**. |
| `PUT /admin/events/{id}/rescheduled` | Body `start_date`; reprograma **confirmed** o **rescheduled**. |
| `PUT /admin/events/{id}/canceled` | Cancela **confirmed** o **rescheduled**. |

**Media type:** JSON `application/json` en cuerpos de entrada y respuestas.

---

## 5. Criterios REST / HTTP aplicados (resumen)

- Recursos nombrados con **sustantivos en plural** y jerarquía clara (`/admin/events`, `/admin/events/{id}/artists`).
- Verbos HTTP alineados a operaciones: **GET** lectura, **POST** creación, **PUT** reemplazo/acción de dominio, **DELETE** eliminación o baja lógica.
- **Sin estado de sesión en el servidor** entre requests: cada llamada lleva lo necesario; autenticación no forma parte de esta versión del API.
- Respuestas con códigos explícitos (**200**, **201**, **204**, **400**, **404**) y cuerpo JSON acotado en errores.

---

## 6. Checklist: qué suele exigirse en un repositorio “completo”

Para que cualquier persona (o un proceso de revisión) pueda reproducir el trabajo, conviene que el repositorio incluya al menos:

- [ ] Código fuente **Maven** + **Spring Boot** (`pom.xml`, `src/`).
- [ ] **`README.md`** con alcance, arquitectura, endpoints y pasos de ejecución (este archivo).
- [ ] Configuración de conexión coherente (`application.properties` y, si aplica, `application-local.properties`).
- [ ] Colección **Postman** (y environment) en **`API/`** para probar todos los flujos.
- [ ] **`.gitignore`** adecuado (por ejemplo excluir `target/`, datos locales de MySQL embebidos si los hubiera).

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

| Camino | Cuándo usarlo |
| ------ | ---------------- |
| **A. Docker** | Tenés Docker Desktop (o motor Docker) y querés MySQL sin instalarlo en el sistema. |
| **B. MySQL ya instalado** | Ya tenés un servidor MySQL local o remoto; solo creás la base y ajustás credenciales si hace falta. |
| **C. Script Windows** | Windows, MySQL 8.4 instalado (por ejemplo con `winget install -e --id Oracle.MySQL`), sin servicio configurado; el script levanta `mysqld` con datadir en tu usuario. |
| **D. Perfil `local` (H2)** | No querés MySQL: levantás la app en memoria para probar endpoints (los datos se pierden al cerrar la JVM). |

Los valores **por defecto** del proyecto (`src/main/resources/application.properties`):

- URL: `jdbc:mysql://localhost:3306/pdyc2026`
- Usuario: `root`
- Contraseña: `insecure`

Si usás otros datos, editá ese archivo **antes** de arrancar Spring.

---

### Paso 3A — MySQL con Docker

1. Iniciá **Docker Desktop** (o el servicio Docker) y esperá a que esté listo.
2. En la raíz del proyecto:

   ```bash
   docker compose up -d
   ```

3. Esperá unos segundos: el contenedor expone **3306** y crea la base **`pdyc2026`** con usuario/contraseña alineados al `application.properties`.

Si el error habla de `dockerDesktopLinuxEngine` o pipe, el motor Docker no está en ejecución.

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

### Paso 4 — Puerto 8080 libre

La app usa **8080** por defecto. Si está ocupado, o bien liberás el proceso, o bien cambiás en `application.properties`:

```properties
server.port=8081
```

**Git Bash (Windows)** — ver quién escucha y matar proceso:

```bash
netstat -ano | grep ':8080'
```

En la línea `LISTENING`, el último número es el **PID**:

```bash
taskkill //PID <PID> //F
```

En **PowerShell** también podés usar `Get-NetTCPConnection -LocalPort 8080`.

---

### Paso 5 — Levantar la aplicación Spring

En la raíz del proyecto.

**Con MySQL (perfil por defecto):**

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

En **CMD / PowerShell**:

```bat
mvnw.cmd spring-boot:run
```

Esperá en el log la línea **`Started EventsApplication`**. La API queda en **`http://localhost:8080`** (o el puerto que configuraste).

**Comandos útiles:**

| Objetivo | Comando |
| -------- | ------- |
| Compilar y tests | `./mvnw test` |
| Generar JAR | `./mvnw package` → luego `java -jar target/events-0.0.1-SNAPSHOT.jar` (mismas reglas de MySQL/perfil que arriba). |

Si en la terminal aparece **`mvn: command not found`**, usá **`./mvnw`** (wrapper), no `mvn`.

---

### Paso 6 — Probar que responde (curl)

Con la app arriba:

```bash
curl -s http://localhost:8080/admin/artists
curl -s http://localhost:8080/admin/events
```

Deberías ver JSON (lista de artistas y de eventos). Si la base estaba vacía, el **seed** carga datos de ejemplo en el primer arranque.

---

### Paso 7 — Probar con Postman

1. Abrí **Postman** → **Import** → subí **`API/Greater-Events.postman_collection.json`**.
2. Importá también **`API/Greater-Events-Local.postman_environment.json`**.
3. En el desplegable de entornos (arriba a la derecha), elegí **Greater Events — Local**.
4. Revisá **`baseUrl`** (por defecto `http://localhost:8080`; si cambiaste el puerto de Spring, cambiá acá también).
5. Las variables **`eventId`**, **`artistId`**, etc. están pensadas para el seed y la colección; leé la descripción de la colección en Postman para saber qué id usar en cada request.

La API **no** lleva autenticación HTTP: las credenciales de MySQL son solo para que Spring se conecte a la BD, no van en Postman.

---

## 8. Problemas frecuentes

| Síntoma | Qué hacer |
| ------- | --------- |
| `Communications link failure` / `Connection refused` hacia MySQL | MySQL arriba, base `pdyc2026`, URL/usuario/clave en `application.properties`; o usá perfil `local` con H2. |
| `Port 8080 was already in use` | Paso 4: liberar puerto o `server.port`. |
| `mvn: command not found` | Usar `./mvnw` o `mvnw.cmd`. |
| `Unable to access jarfile target/...` | Ejecutar antes `./mvnw package`. |
| Docker: error de pipe / motor | Iniciar Docker Desktop. |
| `CREATE DATABASE` da “command not found” en bash | Eso es SQL: ejecutalo con el cliente `mysql -e "..."`, no pegado solo en bash. |
| PUT a artista **400** con solo datos del seed | Regla de negocio: no se edita un artista que ya participó en eventos; creá uno con **POST** y usá su `id` (la colección Postman lo explica con `artistIdFree`). |
