default:
    @just --list

run:
    ./gradlew shadowJar
    java -jar build/libs/analyzer-btc-1.0-SNAPSHOT-all.jar

test *extra:
    @# --info --rerun and --test <specifier> are useful extra arguments
    ./gradlew test {{extra}}

broker-up *extra:
    @# --build --detach are useful extra arguments
    docker compose up {{extra}} broker
broker-down:
    docker compose down broker
broker-shell:
    docker exec -it -w /opt/kafka/bin broker bash
list-topics:
    docker exec broker /opt/kafka/bin/kafka-topics.sh --bootstrap-server broker:29092 --list

bitcoind-up *extra:
    @# --build and --detach are useful extra arguments
    docker compose up {{extra}} bitcoind
bitcoind-down:
    docker compose down bitcoind
bitcoind-shell:
    docker exec -it bitcoind bash

compose-ps:
    docker compose ps
