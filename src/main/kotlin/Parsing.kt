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

class XorFileStream(val stream: InputStream, val xorKey: ByteArray) {
    private var xorKeyIdx = 0
    init { check(xorKey.size == 8) }

    constructor(file: File, xorKey: ByteArray) : this(FileInputStream(file), xorKey)

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
        return when (val firstByte = stream.read()) {
            -1 -> throw EOFException()
            in 0..0xfc -> firstByte.toULong()
            0xfd -> readUShort().toULong()
            0xfe -> readUInt().toULong()
            0xff -> readULong()
            else -> throw RuntimeException("unexpected first byte ${String.format("%02", firstByte)}")
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
        stream.readNBytes(4)

        // Previous block
        stream.readNBytes(32)

        val merkleRoot = stream.readNBytes(32)

        time = Instant.fromEpochSeconds(stream.readUInt().toLong())

        // (Target) Bits
        stream.readNBytes(4)

        // Nonce
        stream.readNBytes(4)

        val transactionCount = stream.readCompactSize()

        // FIXME STOPPED
    }
}