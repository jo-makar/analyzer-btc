default:
    just --list

run:
    ./gradlew shadowJar
    java -jar build/libs/analyzer-btc-1.0-SNAPSHOT-all.jar

# Bring up the Kafka broker (--build --detach are useful extra arguments)
broker-up *extra:
    docker compose up {{extra}} broker
broker-down:
    docker compose down broker
broker-shell:
    docker exec -it -w /opt/kafka/bin broker bash
list-topics:
    docker exec broker /opt/kafka/bin/kafka-topics.sh --bootstrap-server broker:29092 --list

# Bring up the Bitcoin daemon (--build --detach are useful extra arguments)
bitcoind-up *extra:
    docker compose up {{extra}} bitcoind
bitcoind-down:
    docker compose down bitcoind
bitcoind-shell:
    docker exec -it bitcoind bash

compose-ps:
    docker compose ps
