# Guion de Presentación y Demo (10 Minutos) — Entrega 3

**Proyecto**: ms-andesstay-reservations  
**Asignatura**: DSY1107 — Arquitectura de Sistemas Distribuidos (Duoc UC)  
**Objetivo**: Demostrar en vivo el flujo completo de reportería en tiempo real con Kafka, administración de tópicos, métricas en Prometheus/Grafana y arquitectura cloud en AWS.

---

## 1. Cronograma Minuto a Minuto (Total: 10 min)

| Bloque | Tiempo | Tema | Responsable sugerido | Qué mostrar en pantalla |
|---|---|---|---|---|
| **1** | 00:00 - 01:00 (1 min) | **Introducción y Arquitectura** | Integrante 1 (Cloud / Arquitectura) | Diapositiva o Mermaid con la arquitectura Entrega 3: Spring Boot 3, Java 21, Kafka (2 brokers), Postgres RDS, Grafana y Cognito. |
| **2** | 01:00 - 03:00 (2 min) | **Cluster Kafka y Kafka-UI** | Integrante 2 (DevOps / Kafka) | Navegador en `http://localhost:8090` (Kafka-UI). Mostrar los 2 brokers activos, tópicos (`reservations.events`, `reporteria-eventos` con 3 particiones y `reporteria-eventos.DLT`). |
| **3** | 03:00 - 05:00 (2 min) | **Flujo Productor → Consumidor en Tiempo Real** | Integrante 3 (Backend / Seguridad) | Crear reserva vía API o frontend como `HUESPED`. Mostrar logs de publicación en `reservations.events` y `reporteria-eventos`, y consumo inmediato actualizando `report_kpi_daily`. |
| **4** | 05:00 - 07:00 (2 min) | **Dashboards de Grafana en Vivo (KPI y Técnico)** | Integrante 4 (Frontend / Reportería) | Navegador en `http://localhost:3000` (Grafana). Mostrar dashboard `andesstay-kpi` embebible vía iframe con auto-refresh cada 5s. Ejecutar endpoint demo para ver la actualización de KPIs en vivo. |
| **5** | 07:00 - 09:00 (2 min) | **Microservicio Administrador de Kafka (API ADMIN)** | Integrante 2 o 3 (Backend / Admin) | Postman o Swagger UI (`/swagger-ui.html`): Crear tópico dinámico con `POST /api/admin/kafka/topics`, modificar configuración con `PUT /config`, y eliminar con `DELETE`. Demostrar 403 Forbidden como rol HUESPED. |
| **6** | 09:00 - 10:00 (1 min) | **Infraestructura Cloud AWS (EC2 + RDS) y Cierre** | Integrante 1 (Cloud) | Mostrar consola AWS con EC2 y RDS Postgres, Security Groups y script de automatización `deploy-ec2.sh`. Conclusión del equipo. |

---

## 2. Detalle de Ejecución Paso a Paso

### Minuto 0 a 1: Arquitectura
- Explicar la separación de responsabilidades:
  1. Tópico `reservations.events`: Contrato intacto para auditoría (`ms-andesstay-audit`).
  2. Tópico `reporteria-eventos`: Evento enriquecido con precio, noches, estado y unidad para KPIs.
  3. Consumidor idempotente: Registra en `report_event_log` y actualiza agregados en `report_kpi_daily`.

### Minuto 1 a 3: Kafka-UI
- Ingresar a `http://localhost:8090` con usuario `admin` / clave `changeme123`.
- Destacar:
  - Cluster `andesstay-local` con **2 brokers** Confluent CP-Kafka 7.6.1.
  - Tópico `reporteria-eventos`: 3 particiones, factor de replicación 2.
  - Configuración min in-sync replicas = 1 y acks = all.

### Minuto 3 a 5: Creación de Reserva y Flujo Kafka
- Realizar petición HTTP con token de usuario `HUESPED`:
  ```bash
  POST /api/reservations
  {
    "unitId": 1,
    "checkIn": "2026-12-01",
    "checkOut": "2026-12-05",
    "guestsCount": 2
  }
  ```
- Explicar que el usuario recién registrado en Cognito sin grupos recibe automáticamente `ROLE_HUESPED`.
- Mostrar en la terminal los logs correlacionados con `traceId` demostrando que tras el commit se publica en ambos tópicos.

### Minuto 5 a 7: Grafana en Vivo y Generador Demo
- Abrir `http://localhost:3000/d/andesstay-kpi/andesstay-reporteria-kpi?kiosk&refresh=5s`.
- Ejecutar endpoint de demo (solo perfil demo o admin):
  ```bash
  POST /api/admin/demo/generar?cantidad=50
  ```
- Observar en tiempo real cómo los gráficos de Stat (Ingresos, Reservas), Timeseries (Ingresos diarios) y PieChart (Ranking unidades) se actualizan inmediatamente al ser consumidos desde Kafka.
- Mencionar la configuración para iframe de Angular: `GF_SECURITY_ALLOW_EMBEDDING=true` y acceso anónimo como `Viewer`.

### Minuto 7 a 9: Administrador de Kafka
- En Swagger UI o Postman con rol `ADMIN`:
  1. `POST /api/admin/kafka/topics` -> Crear tópico `kpi-metricas-test` con 2 particiones.
  2. Verificar en Kafka-UI que el tópico aparece de inmediato.
  3. `PUT /api/admin/kafka/topics/kpi-metricas-test/partitions?cantidad=4` -> Aumentar a 4 particiones.
  4. Intentar con token `HUESPED` -> Verificar respuesta `403 FORBIDDEN`.
  5. `DELETE /api/admin/kafka/topics/kpi-metricas-test` -> Eliminar tópico.

### Minuto 9 a 10: AWS y Conclusiones
- Resumir la preparación para AWS:
  - Instancia EC2 con Docker Compose y variables de entorno (`.env.example`).
  - Base de datos relacional RDS PostgreSQL con Security Group restringido.
  - Script idempotente `scripts/deploy-ec2.sh`.

---

## 3. Matriz "Qué Debe Saber Cada Integrante"

Para asegurar que todo el equipo domine el sistema ante preguntas de la comisión:

| Área | Preguntas Típicas de la Comisión | Conceptos Clave que Debe Explicar |
|---|---|---|
| **Backend & Java** | ¿Por qué se publica en Kafka después del commit? ¿Cómo se garantiza la idempotencia? | `@TransactionalEventListener(phase = AFTER_COMMIT)` o sincronización post-commit para no emitir eventos si hay rollback. Idempotencia mediante PK `eventId` en `report_event_log` antes de aplicar el upsert a `report_kpi_daily`. |
| **Kafka & Streaming** | ¿Por qué la clave del mensaje es `reservationId`? ¿Qué pasa si un broker cae? | La clave asegura que todos los eventos de una misma reserva caigan en la misma partición y mantengan orden estricto. Con 2 brokers y factor de replicación 2, si un broker cae, el ISR permite seguir operando sin pérdida de datos. Dead Letter Topic (`.DLT`) tras 3 reintentos fallidos. |
| **Cloud & DevOps (AWS)** | ¿Cómo se comunican los servicios en EC2? ¿Por qué `KAFKA_ADVERTISED_HOST`? | Red puente externa `andesstay-net`. En Kafka, los listeners `INTERNAL` resuelven entre contenedores y `EXTERNAL` expone la IP pública de la EC2 hacia el exterior sin exponer Prometheus al público. Security Groups restringen el puerto 5432 de RDS solo a la EC2. |
| **Seguridad & Frontend** | ¿Cómo maneja Cognito los usuarios recién registrados? ¿Cómo se integra con Angular? | `CognitoJwtAuthenticationConverter` asigna `ROLE_HUESPED` si el claim `cognito:groups` viene vacío. En Angular se embebe Grafana en `<iframe>` gracias a `GF_SECURITY_ALLOW_EMBEDDING=true`, y los guards de Angular controlan la vista según el rol. |
