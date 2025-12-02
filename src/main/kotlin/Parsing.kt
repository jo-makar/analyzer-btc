package com.github.jo_makar

import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
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
    val version: ByteArray
    val previousBlock: ByteArray
    val merkleRoot: ByteArray
    val time: Instant
    val bits: ByteArray
    val nonce: ByteArray
    val coinbase: Transaction

    init {
        // Mainnet Magic bytes
        check(stream.readNBytes(4).contentEquals(byteArrayOf(0xf9.toByte(), 0xbe.toByte(), 0xb4.toByte(), 0xd9.toByte())))

        val blockSize = stream.readUInt()

        // Version / readiness signal for soft forks
        version = stream.readNBytes(4)
        previousBlock = stream.readNBytes(32)
        merkleRoot = stream.readNBytes(32)
        time = Instant.fromEpochSeconds(stream.readUInt().toLong())
        // (Target) Bits
        bits = stream.readNBytes(4)
        nonce = stream.readNBytes(4)

        val transactionCount = stream.readCompactSize()
        check(transactionCount >= 1u)

        // The first transaction is a special transaction called the coinbase transaction,
        // that miners place inside the block to collect the block reward (block subsidy & transaction fees)
        coinbase = Transaction(stream, true)

        // TODO Stopped implementation here
        // In order to identify transfers, a mapping of TXIDs to transactions would have to be maintained.
        // For now, just identify coinbase transactions to show transfers of newly minted block rewards.
        for (i in 0uL..<(transactionCount-1uL))
            Transaction(stream)

        // TODO Verify the blockSize value is correct
        // TODO Validate the merkleRoot value
    }
}

class Transaction(stream: XorFileStream, val coinbase: Boolean = false) {
    val version: UInt
    val marker: Byte?
    val flag: Byte?
    val inputs: List<Input>
    val outputs: List<Output>
    val lockTime: UInt
    val txid: ByteArray

    init {
        version = stream.readUInt()
        check(version == 1u || version == 2u)

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

        if (coinbase) {
            check(inputCount == 1uL)

            val txid = stream.readNBytes(32)
            check(txid.contentEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
            val vout = stream.readUInt()
            check(vout == 0xffffffffu)
            val scriptSigSize = stream.readCompactSize()
            val scriptSig = stream.readNBytes(scriptSigSize.toInt())
            val sequence = stream.readUInt()
            check(sequence == 0xffffffffu)

            inputs = listOf(Input(txid, vout, Script(scriptSig), sequence))

            val outputCount = stream.readCompactSize()
            check(outputCount >= 1uL)

            val outputsList = mutableListOf<Output>()
            for (i in 0uL..<outputCount) {
                val amount = stream.readULong()
                val scriptPubKeySize = stream.readCompactSize()
                val scriptPubKey = stream.readNBytes(scriptPubKeySize.toInt())
                outputsList.add(Output(amount, Script(scriptPubKey)))
            }

            outputs = outputsList.toList()

            if (marker != null)
                throw RuntimeException("TODO implement segwit support")

            lockTime = stream.readUInt()
        }

        else {
            check(inputCount >= 1uL)

            val inputsList = mutableListOf<Input>()
            for (i in 0uL..<inputCount) {
                val txid = stream.readNBytes(32)
                val vout = stream.readUInt()
                val scriptSigSize = stream.readCompactSize()
                val scriptSig = stream.readNBytes(scriptSigSize.toInt())
                val sequence = stream.readUInt()
                inputsList.add(Input(txid, vout, Script(scriptSig), sequence))
            }

            inputs = inputsList.toList()

            val outputCount = stream.readCompactSize()
            check(outputCount >= 1uL)

            val outputsList = mutableListOf<Output>()
            for (i in 0uL..<outputCount) {
                val amount = stream.readULong()
                val scriptPubKeySize = stream.readCompactSize()
                val scriptPubKey = stream.readNBytes(scriptPubKeySize.toInt())
                outputsList.add(Output(amount, Script(scriptPubKey)))
            }

            outputs = outputsList.toList()

            if (marker != null)
                throw RuntimeException("TODO implement segwit support")

            lockTime = stream.readUInt()
        }

        val sha256 = MessageDigest.getInstance("SHA256")
        val sha256Hash = fun(input: ByteArray): ByteArray {
            sha256.reset()
            return sha256.digest(input)
        }

        // NB This will appear in reverse order when seen on blockchain explorers
        txid = sha256Hash(sha256Hash(serialize()))
    }

    fun serialize(): ByteArray {
        var buffer = version.toByteArray() +
                inputs.size.toULong().compactSizeToByteArray()

        for (input in inputs)
            buffer += input.txid +
                    input.vout.toByteArray() +
                    input.scriptSig.rawScript.size.toULong().compactSizeToByteArray() +
                    input.scriptSig.rawScript +
                    input.sequence.toByteArray()

        buffer += outputs.size.toULong().compactSizeToByteArray()
        for (output in outputs)
            buffer += output.amount.toByteArray() +
                    output.scriptPubKey.rawScript.size.toULong().compactSizeToByteArray() +
                    output.scriptPubKey.rawScript

        buffer += lockTime.toByteArray()

        return buffer
    }

    fun transferTo(): List<Pair<String?, ULong>> {
        // TODO Stopped implementation here
        // In order to identify transfers, a mapping of TXIDs to transactions would have to be maintained.
        // For now, just identify coinbase transactions to show transfers of newly minted block rewards.

        check(coinbase)

        return outputs
            .map { Pair(
                it.scriptPubKey.scriptPubKeyTarget(),
                it.amount)
            }
            .toList()
    }

    class Input(val txid: ByteArray, val vout: UInt, val scriptSig: Script, val sequence: UInt) {
        init { check(txid.size == 32) }
    }

    class Output(val amount: ULong, val scriptPubKey: Script)
}

fun UShort.toByteArray(bigEndian: Boolean = false): ByteArray {
    val byteArray = ByteArray(UShort.SIZE_BYTES)
    var value = this.toUInt()

    for (i in 0..<UShort.SIZE_BYTES) {
        val index = if (bigEndian) UShort.SIZE_BYTES - 1 - i else i
        byteArray[index] = (value and 0x0ffu).toByte()
        value = value shr 8
    }

    return byteArray
}

fun UInt.toByteArray(bigEndian: Boolean = false): ByteArray {
    val byteArray = ByteArray(UInt.SIZE_BYTES)
    var value = this

    for (i in 0..<UInt.SIZE_BYTES) {
        val index = if (bigEndian) UInt.SIZE_BYTES - 1 - i else i
        byteArray[index] = (value and 0x0ffu).toByte()
        value = value shr 8
    }

    return byteArray
}

fun ULong.toByteArray(bigEndian: Boolean = false): ByteArray {
    val byteArray = ByteArray(ULong.SIZE_BYTES)
    var value = this

    for (i in 0..<ULong.SIZE_BYTES) {
        val index = if (bigEndian) ULong.SIZE_BYTES - 1 - i else i
        byteArray[index] = (value and 0x0ffu).toByte()
        value = value shr 8
    }

    return byteArray
}

fun ULong.compactSizeToByteArray(bigEndian: Boolean = false): ByteArray {
    return when (this) {
        in 0u..252u -> byteArrayOf(this.toByte())
        in 253u..65535u -> byteArrayOf(0xfd.toByte()) + this.toUShort().toByteArray()
        in 65536u..4294967295u -> byteArrayOf(0xfe.toByte()) + this.toUInt().toByteArray()
        in 4294967296u..18446744073709551615u -> byteArrayOf(0xff.toByte()) + this.toByteArray()
        else -> throw RuntimeException("not possible")
    }
}
