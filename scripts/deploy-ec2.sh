#!/usr/bin/env bash
# ==============================================================================
# SCRIPT DE DESPLIEGUE EN AWS EC2 - ms-andesstay-reservations (Entrega 3)
# ==============================================================================
# NOTA: Este script NO debe ser ejecutado automáticamente por el agente.
# Debe ejecutarse manualmente en la instancia EC2 destino (Amazon Linux 2023 / Ubuntu).
# Es idempotente y utiliza 'set -euo pipefail' para abortar ante cualquier error.
# ==============================================================================

set -euo pipefail

echo "=========================================================="
echo "Iniciando despliegue de ms-andesstay-reservations en EC2..."
echo "=========================================================="

# 1. Variables de entorno requeridas (con defaults si aplican)
APP_DIR="${APP_DIR:-/opt/andesstay}"
GIT_REPO="${GIT_REPO:-https://github.com/tu-organizacion/ms-andesstay-reservations.git}"
GIT_BRANCH="${GIT_BRANCH:-main}"
KAFKA_ADVERTISED_HOST="${KAFKA_ADVERTISED_HOST:-}"

if [[ -z "${KAFKA_ADVERTISED_HOST}" ]]; then
    # Intentar obtener la IP publica de la instancia EC2 mediante IMDSv2
    TOKEN=$(curl -s -S -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600" || true)
    if [[ -n "${TOKEN}" ]]; then
        KAFKA_ADVERTISED_HOST=$(curl -s -S -H "X-aws-ec2-metadata-token: $TOKEN" "http://169.254.169.254/latest/meta-data/public-ipv4" || true)
    fi
fi

if [[ -z "${KAFKA_ADVERTISED_HOST}" ]]; then
    echo "ERROR: Debe definir KAFKA_ADVERTISED_HOST con la IP pública o DNS de la instancia EC2."
    exit 1
fi

echo ">> Host público detectado para Kafka: ${KAFKA_ADVERTISED_HOST}"

# 2. Instalar Docker y Docker Compose si no estan instalados
if ! command -v docker &> /dev/null; then
    echo ">> Instalando Docker..."
    if command -v dnf &> /dev/null; then
        sudo dnf update -y
        sudo dnf install -y docker
    elif command -v apt-get &> /dev/null; then
        sudo apt-get update -y
        sudo apt-get install -y docker.io docker-compose-plugin
    fi
    sudo systemctl enable --now docker
    sudo usermod -aG docker "$USER" || true
fi

# 3. Crear red Docker compartida si no existe (idempotente)
if ! docker network ls --format '{{.Name}}' | grep -w "andesstay-net" > /dev/null; then
    echo ">> Creando red Docker andesstay-net..."
    docker network create andesstay-net
else
    echo ">> Red Docker andesstay-net ya existe."
fi

# 4. Preparar directorio de la aplicacion
sudo mkdir -p "${APP_DIR}"
sudo chown -R "$USER:$USER" "${APP_DIR}"
cd "${APP_DIR}"

# 5. Clonar o actualizar repositorio
if [[ ! -d "${APP_DIR}/.git" ]]; then
    echo ">> Clonando repositorio desde ${GIT_REPO}..."
    git clone --branch "${GIT_BRANCH}" "${GIT_REPO}" "${APP_DIR}"
else
    echo ">> Repositorio ya existe, actualizando rama ${GIT_BRANCH}..."
    git fetch origin "${GIT_BRANCH}"
    git checkout "${GIT_BRANCH}"
    git pull origin "${GIT_BRANCH}"
fi

# 6. Validar existencia de .env
if [[ ! -f "${APP_DIR}/.env" ]]; then
    echo ">> No se encontró .env. Creando a partir de infra/.env.example..."
    cp "${APP_DIR}/infra/.env.example" "${APP_DIR}/.env"
    sed -i "s/KAFKA_ADVERTISED_HOST=localhost/KAFKA_ADVERTISED_HOST=${KAFKA_ADVERTISED_HOST}/g" "${APP_DIR}/.env"
    echo "ATENCION: Complete los valores de RDS Postgres y Cognito en ${APP_DIR}/.env antes de continuar."
fi

# 7. Levantar Kafka (Zookeeper + 2 brokers + init)
echo ">> Levantando cluster Kafka (Zookeeper + 2 brokers)..."
cd "${APP_DIR}/infra/kafka"
docker compose --env-file "${APP_DIR}/.env" up -d

# Esperar a que los brokers esten listos
echo ">> Esperando que los brokers de Kafka estén saludables..."
timeout 120 bash -c 'until docker ps | grep andesstay-kafka-1 | grep -q "(healthy)"; do sleep 3; done' || echo "Continuando..."

# 8. Levantar Kafka-UI
echo ">> Levantando Kafka-UI..."
cd "${APP_DIR}/infra/kafka-ui"
docker compose --env-file "${APP_DIR}/.env" up -d

# 9. Levantar Monitoreo (Prometheus + Grafana + kafka-exporter)
echo ">> Levantando Prometheus, Grafana y Kafka-exporter..."
cd "${APP_DIR}/infra/monitoring"
docker compose --env-file "${APP_DIR}/.env" up -d

# 10. Construir y levantar el microservicio de reservas
echo ">> Construyendo y levantando ms-andesstay-reservations..."
cd "${APP_DIR}"
docker compose -f docker-compose.app.yml --env-file .env up -d --build

echo "=========================================================="
echo "DESPLIEGUE COMPLETADO EXITOSAMENTE"
echo "=========================================================="
echo "Verifique el estado de los contenedores:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo ""
echo "Endpoints expuestos:"
echo " - Microservicio Reservas:  http://${KAFKA_ADVERTISED_HOST}:8082"
echo " - Healthcheck Actuator:    http://${KAFKA_ADVERTISED_HOST}:8082/actuator/health"
echo " - Swagger UI:              http://${KAFKA_ADVERTISED_HOST}:8082/swagger-ui.html"
echo " - Kafka-UI:                http://${KAFKA_ADVERTISED_HOST}:8090"
echo " - Grafana Dashboards:      http://${KAFKA_ADVERTISED_HOST}:3000"
echo "=========================================================="
