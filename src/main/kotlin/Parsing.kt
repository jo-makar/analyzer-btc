package com.github.jo_makar

import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.xor
import kotlin.time.Instant

// Refs: https://learnmeabitcoin.com/technical/block/blkdat/
//       https://learnmeabitcoin.com/technical/block/#header
//       https://learnmeabitcoin.com/technical/transaction/

// NB All integral types in Java/Kotlin are signed, including Bytes

class XorFileStream(val stream: InputStream, val xorKey: ByteArray) {
    private var xorKeyIdx = 0
    init { check(xorKey.size == 8) }

    constructor(file: File, xorKey: ByteArray) : this(FileInputStream(file), xorKey)

    fun readByte(): Byte {
        val byte = stream.read()
        if (byte == -1)
            throw EOFException()

        val xorByte = xorKey[xorKeyIdx]
        xorKeyIdx = (xorKeyIdx + 1) % 8
        return byte.toByte() xor xorByte
    }

    fun readNBytes(n: Int): ByteArray {
        val buffer = stream.readNBytes(n)
        check(buffer.size == n)

        for (i in 0..<buffer.size) {
            buffer[i] = buffer[i] xor xorKey[xorKeyIdx]
            xorKeyIdx = (xorKeyIdx + 1) % 8
        }
        return buffer
    }

    fun readUShort(): UShort {
        return ByteBuffer.wrap(readNBytes(2))
            .order(ByteOrder.LITTLE_ENDIAN).getShort()
            .toUShort()
    }

    fun readUInt(): UInt {
        return ByteBuffer.wrap(readNBytes(4))
            .order(ByteOrder.LITTLE_ENDIAN).getInt()
            .toUInt()
    }

    fun readULong(): ULong {
        return ByteBuffer.wrap(readNBytes(8))
            .order(ByteOrder.LITTLE_ENDIAN).getLong()
            .toULong()
    }

    fun readCompactSize(): ULong {
        return when (val firstByte = readByte().toUByte()) {
            in 0u..0xfcu -> firstByte.toULong()
            0xfd.toUByte() -> readUShort().toULong()
            0xfe.toUByte() -> readUInt().toULong()
            0xff.toUByte() -> readULong()
            else -> throw RuntimeException("not possible")
        }
    }

    fun readCompactSize(firstByte: Byte): ULong {
        return when (firstByte.toUByte()) {
            in 0u..0xfcu -> firstByte.toULong()
            0xfd.toUByte() -> readUShort().toULong()
            0xfe.toUByte() -> readUInt().toULong()
            0xff.toUByte() -> readULong()
            else -> throw RuntimeException("not possible")
        }
    }
}

@kotlin.time.ExperimentalTime
class Block(stream: XorFileStream) {
    val time: Instant

    init {
        // Mainnet Magic bytes
        check(stream.readNBytes(4).contentEquals(byteArrayOf(0xf9.toByte(), 0xbe.toByte(), 0xb4.toByte(), 0xd9.toByte())))

        val blockSize = stream.readUInt()

        // Version / readiness signal for soft forks
        val version = stream.readNBytes(4)
        val previousBlock = stream.readNBytes(32)
        val merkleRoot = stream.readNBytes(32)
        time = Instant.fromEpochSeconds(stream.readUInt().toLong())
        // (Target) Bits
        val bits = stream.readNBytes(4)
        val nonce = stream.readNBytes(4)

        val transactionCount = stream.readCompactSize()
        check(transactionCount >= 1U)

        // The first transaction is a special transaction called the coinbase transaction,
        // that miners place inside the block to collect the block reward (block subsidy & transaction fees)

        // FIXME
        Transaction(stream, true)
    }
}

class Transaction(stream: XorFileStream, coinbase: Boolean = false) {
    val version: UInt
    val marker: Byte?
    var flag: Byte?

    init {
        version = stream.readUInt()
        check(version == 1U || version == 2U)

        // Segregated Witness (SegWit) was introduced in 2017 and introducing some ambiguity to transaction parsing.
        // Ref: https://learnmeabitcoin.com/technical/upgrades/segregated-witness/

        val byte = stream.readByte()
        val inputCount = if (byte == 0.toByte()) {
            marker = byte
            flag = stream.readByte()
            stream.readCompactSize()
        } else {
            marker = null
            flag = null
            stream.readCompactSize(byte)
        }

        // FIXME STOPPED
        println("$marker $flag $inputCount")
    }
}
