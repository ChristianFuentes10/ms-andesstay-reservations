# Red Docker compartida

Antes de levantar cualquier compose, crear la red compartida:

    docker network create andesstay-net

Esta red permite que todos los servicios se comuniquen por nombre de contenedor.
