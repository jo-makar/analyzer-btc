package com.github.jo_makar

import java.io.File

fun main() {
    val basePath = "bitcoind/blocks/"

    val xorKey = File(basePath + "xor.dat").readBytes()
    check(xorKey.size == 8)
    println("xorKey = ${xorKey.joinToString(" ", "[", "]") { String.format("%02x", it) }}")

    val blkDatPaths = File(basePath)
        .listFiles { Regex("^blk\\d+\\.dat$").matches(it.name) }
        ?.sorted()
        ?: emptyList()

    for (path in blkDatPaths) {
        println("processing $path")
        val stream = XorFileStream(path, xorKey)

        // FIXME STOPPED Ref https://learnmeabitcoin.com/technical/block/blkdat/
        //               Then attempt to parse the file and produce List<Block>
        //               Identify Addresses from the Blocks and update their balances in a KTable
        println(stream.readNBytes(4).joinToString(" ", "[", "]") { String.format("%02x", it) })
        break // FIXME Remove
    }

    // FIXME Kafka streams https://kafka.apache.org/39/documentation/streams/tutorial
    /* FIXME Similar example
        import org.apache.kafka.common.serialization.Serdes
        import org.apache.kafka.streams.KafkaStreams
        import org.apache.kafka.streams.StreamsBuilder
        import org.apache.kafka.streams.StreamsConfig
        import org.apache.kafka.streams.kstream.KStream
        import java.util.Properties

        fun main() {
            val props = Properties().apply {
                put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-application")
                put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092") // Replace with your Kafka broker address
                put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().javaClass)
                put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().javaClass)
            }

            val builder = StreamsBuilder()
            val textLines: KStream<String, String> = builder.stream("input-topic") // Input topic name

            val wordCounts: KStream<String, Long> = textLines
                .flatMapValues { value -> value.toLowerCase().split("\\W+".toRegex()) }
                .groupBy { _, word -> word }
                .count()
                .toStream()

            wordCounts.to("output-topic") // Output topic name

            val streams = KafkaStreams(builder.build(), props)
            streams.start()

            // Add shutdown hook to close the stream gracefully
            Runtime.getRuntime().addShutdownHook(Thread(streams::close))
        }
     */
}
