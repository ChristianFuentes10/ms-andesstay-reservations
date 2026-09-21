# ms-andesstay-reservations

Microservicio de reservas del caso **AndesStay** (DSY1107 - Desarrollo Cloud Native I).

Es el microservicio de dominio dueño del ciclo de vida de una reserva: la crea
sobre una unidad de `ms-andesstay-catalog`, la mueve por sus estados
(confirmar, check-in, check-out, cancelar) y publica un evento en el tópico
Kafka `reservations.events` por cada cambio, que consume
`ms-andesstay-audit` para trazabilidad.

```
Angular --Bearer JWT--> [API Gateway] --> ms-andesstay-bff (o directo)
                                                |
                                                v
                                     ms-andesstay-reservations  --consulta unidad--> ms-andesstay-catalog
                                                |
                                                v (publica evento)
                                     Kafka "reservations.events"
                                                |
                                                v
                                       ms-andesstay-audit (consume y guarda)
```

## Stack

- Java 17
- Spring Boot 3.3.4 (Web, Data JPA, Validation, Security, OAuth2 Resource
  Server, Kafka)
- PostgreSQL (persistencia de reservas)
- Apache Kafka (productor del tópico `reservations.events`)
- `spring-webflux` (solo el módulo, para usar `WebClient` como cliente HTTP
  hacia el catálogo, manteniendo esta app como servlet clásica)

## Estructura

```
src/main/java/cl/duoc/andesstay/reservations/
├── ReservationsApplication.java
├── config/
│   ├── JwtConfig.java            # JwtDecoder: valida firma + issuer + client_id (Cognito)
│   ├── SecurityConfig.java       # Exige JWT valido; autorizacion fina en el controller
│   ├── SecurityConfigNoAuth.java # Perfil "nosec" solo para desarrollo local
│   └── WebClientConfig.java      # WebClient hacia ms-andesstay-catalog
├── security/                     # Validador de audience, converter de roles, 401/403 homogeneos
├── client/CatalogClient.java     # Consulta la unidad (existe, disponible, precio/noche)
├── model/                        # Entidad Reservation + enum EstadoReserva (maquina de estados)
├── repository/                   # JPA + Specifications para filtros dinamicos
├── service/
│   ├── ReservationService.java         # Reglas de negocio y transiciones de estado
│   └── ReservationEventPublisher.java  # Publica en Kafka (reservations.events)
├── controller/ReservationController.java
├── dto/                           # Request/Response DTOs, UnitDto, ErrorResponse, ReservationEventMessage
└── exception/                     # Excepciones de dominio + GlobalExceptionHandler
```

## Máquina de estados

```
PENDIENTE --confirmar--> CONFIRMADA --checkin--> CHECKIN --checkout--> CHECKOUT
    |                        |
    +---------cancelar-------+---------> CANCELADA
```

Cada transición dispara un evento con `type` = `RESERVA_CREADA`,
`RESERVA_CONFIRMADA`, `CHECKIN`, `CHECKOUT` o `RESERVA_CANCELADA` (ver
`EstadoReserva`), publicado en `reservations.events` con el mismo formato
(`ReservationEventMessage`: `eventId`, `type`, `timestamp`, `traceId`,
`correlationId`, `reservationId`, `actor`) que espera
`ms-andesstay-audit`.

## Roles y endpoints

Se asume el mismo User Pool de Cognito que usan `ms-andesstay-catalog` y
`ms-andesstay-audit`, con los grupos `ADMIN`, `RECEPCIONISTA` y `HUESPED`
(ajusta `CognitoJwtAuthenticationConverter`/tu User Pool si tu proyecto usa
otros nombres de rol, por ejemplo si `HUESPED` en tu caso es `CLIENTE`).

| Método | Ruta                             | Rol requerido                              |
|--------|-----------------------------------|---------------------------------------------|
| POST   | `/api/reservations`               | ADMIN, RECEPCIONISTA, HUESPED (crea para sí mismo) |
| GET    | `/api/reservations`               | ADMIN, RECEPCIONISTA (listado con filtros)  |
| GET    | `/api/reservations/mias`          | cualquier usuario autenticado (sus propias reservas) |
| GET    | `/api/reservations/{id}`          | ADMIN, RECEPCIONISTA, o el HUESPED dueño    |
| PUT    | `/api/reservations/{id}/confirmar`| ADMIN, RECEPCIONISTA                        |
| PUT    | `/api/reservations/{id}/checkin`  | ADMIN, RECEPCIONISTA                        |
| PUT    | `/api/reservations/{id}/checkout` | ADMIN, RECEPCIONISTA                        |
| PUT    | `/api/reservations/{id}/cancelar` | ADMIN, RECEPCIONISTA, o el HUESPED dueño (solo si está PENDIENTE/CONFIRMADA) |
| DELETE | `/api/reservations/{id}`          | ADMIN                                        |

Filtros de `GET /api/reservations` (todos opcionales): `guestId`, `unitId`,
`estado` (`PENDIENTE`/`CONFIRMADA`/`CHECKIN`/`CHECKOUT`/`CANCELADA`),
`desde`, `hasta` (fechas ISO `yyyy-MM-dd`), `page`, `size`.

El campo `guestId` **no** se recibe en el body al crear una reserva: siempre
se toma del `sub` del JWT autenticado, para que un huésped no pueda reservar
"a nombre de otro" armando el request a mano.

## Reglas de negocio al crear una reserva

1. `checkOut` debe ser posterior a `checkIn`.
2. La unidad debe existir en `ms-andesstay-catalog` y estar `available`.
3. `guestsCount` no puede superar la `capacity` de la unidad.
4. No puede solaparse con otra reserva de la misma unidad en estado
   `PENDIENTE`, `CONFIRMADA` o `CHECKIN` (evita doble reserva).
5. El precio total se calcula como `pricePerNight` (tomado del catálogo en
   ese momento) × número de noches, y queda guardado en la reserva para no
   depender de que el catálogo no cambie el precio después.

## Configuración (variables de entorno)

```bash
export DB_PASSWORD=xxxxx
export CATALOG_SERVICE_URL=http://localhost:8081
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export COGNITO_REGION=us-east-1
export COGNITO_USER_POOL_ID=xxxxxxxx
export COGNITO_APP_CLIENT_ID=xxxxxxxx
```

## Cómo correrlo local

1. Crear la base de datos:
   ```bash
   psql -U postgres -c "CREATE DATABASE andesstay_reservations;"
   ```
2. Levantar Kafka (ver `infra/kafka/compose.yml` del proyecto, el mismo que
   usa `ms-andesstay-audit`):
   ```bash
   docker compose up -d
   ```
3a. **Sin Cognito todavía (perfil `nosec`, solo desarrollo):**
   ```bash
   export DB_PASSWORD=xxxxx
   mvn spring-boot:run -Dspring-boot.run.profiles=nosec
   ```
3b. **Con seguridad real (User Pool de Cognito ya creado):**
   ```bash
   export DB_PASSWORD=xxxxx COGNITO_REGION=... COGNITO_USER_POOL_ID=... COGNITO_APP_CLIENT_ID=...
   mvn spring-boot:run
   ```

El servicio queda arriba en `http://localhost:8082`.

## Pruebas

```bash
mvn test
```

`ReservationSecurityTest` cubre, con `@WebMvcTest` + `spring-security-test`
(mismo enfoque que `CatalogProxySecurityTest` del BFF): sin token → 401,
token válido sin el rol requerido → 403, token válido con rol adecuado →
200.

## Pendiente / siguiente fase

- Exponer estos endpoints también a través del BFF (agregar un
  `ReservationClient` + `ReservationProxyController` en
  `ms-andesstay-bff`, igual que ya existe para catálogo) si el frontend
  Angular no va a llamar a este microservicio directamente.
- Ajustar los nombres de rol (`HUESPED`/`RECEPCIONISTA`) si tu User Pool de
  Cognito usa otros nombres de grupo.
- Desplegar en EC2 detrás de AWS API Gateway, igual que el resto de los
  componentes.
