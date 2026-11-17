#!/bin/sh
set -e
for db in andesstay_catalog andesstay_reservations andesstay_audit andesstay_users; do
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "CREATE DATABASE $db"
done
# Carga el catalogo (unidades de ejemplo) si existen los SQL
[ -f /seed/schema.sql ] && psql -U "$POSTGRES_USER" -d andesstay_catalog -f /seed/schema.sql
[ -f /seed/data.sql ]   && psql -U "$POSTGRES_USER" -d andesstay_catalog -f /seed/data.sql
