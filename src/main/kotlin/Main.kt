package com.github.jo_makar

import java.io.File
import java.util.Properties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.LongSerializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig

@kotlin.time.ExperimentalTime
fun main(args: Array<String>) {
    val producer = args.any { it == "-producer" } || ! args.any { it == "-consumer" }

    val inputTopic = "input-topic"
    val stateStore = "store"

    if (producer) {
        val producer = KafkaProducer<String, Long>(
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092")
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, LongSerializer::class.java.name)
            }
        )

        val basePath = "bitcoind/blocks/"

        val xorKey = File(basePath + "xor.dat").readBytes()
        check(xorKey.size == 8)
        println("xorKey = ${xorKey.joinToString(" ", "[", "]") { String.format("%02x", it) }}")

        val blkDatPaths = File(basePath)
            .listFiles { Regex("^blk\\d+\\.dat$").matches(it.name) }
            ?.sorted()
            ?: emptyList()

        // TODO Stopping point, notes for later continued work
        // - Is the first blk*.dat file incomplete?  Does not seem to end on a block boundary
        // - How can the pipeline be started and stopped?  Ie starting / stopping producers, consumer (Stream app) appropriately
        // - How to make the state store queries outside of the Streams application?
        //   - Ie how to query the underlying RocksDB database outside of a Streams application?

        // TODO Should include TXID in produced records to allow consumer the option of ignoring seen records

        for (path in blkDatPaths) {
            println("processing $path")
            val stream = XorFileStream(path, xorKey)

            for (i in 0..<10) {
                val block = Block(stream)
                for (transfer in block.coinbase.transferTo()) {
                    if (transfer.first != null)
                        producer.send(ProducerRecord(inputTopic, transfer.first, transfer.second.toLong())).get()
                }
            }

            break
        }

        producer.flush()
        producer.close()
    }

    else /* consumer */ {
        val builder = StreamsBuilder()
        builder.table(
            inputTopic,
            Consumed.with(Serdes.String(), Serdes.Long()),
            Materialized.`as`<String, Long, KeyValueStore<Bytes, ByteArray>>(stateStore)
                .withKeySerde(Serdes.String())
                .withValueSerde(Serdes.Long())
        )

        val streams = KafkaStreams(
            builder.build(),
            Properties().apply {
                put(StreamsConfig.APPLICATION_ID_CONFIG, "analyzer-btc")
                put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092")
                put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
                put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.Long()::class.java)
            }
        )

        streams.setStateListener { newState, oldState -> println("state: $oldState => $newState") }

        streams.start()
        Runtime.getRuntime().addShutdownHook(Thread { streams.close() })

        Thread.sleep(5000)

        val store: ReadOnlyKeyValueStore<String, Long>? = streams.store(
            StoreQueryParameters.fromNameAndType(stateStore, QueryableStoreTypes.keyValueStore())
        )

        val address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
        if (store != null)
            println("$address: ${store.get(address)}")
    }
}
