# Stack local de AndesStay

Levanta Postgres + catalog + reservations + audit + users en Docker Desktop para probarlos juntos.
No es para AWS. El BFF y el frontend se ejecutan aparte.

Estructura esperada (repos hermanos):

    AndesStay/
      ms-andesstay-reservations/infra/local-stack/   <- esta carpeta
      ms-andesstay-catalog/  ms-andesstay-audit/  ms-andesstay-users/

Pasos (cada comando por separado):

    docker network create andesstay-net
    cd ms-andesstay-reservations\infra\kafka
    docker compose up -d
    cd ..\local-stack
    copy .env.example .env      (completar COGNITO_USER_POOL_ID y COGNITO_APP_CLIENT_ID)
    docker compose -f docker-compose.local.yml --env-file .env up -d --build

Verificacion: http://localhost:8082/actuator/health y :8083 -> {"status":"UP"}.
Nunca subir .env, login.json ni token.txt. Apagar: docker compose -f docker-compose.local.yml down
