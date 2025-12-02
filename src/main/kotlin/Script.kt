package com.github.jo_makar

import io.github.novacrypto.base58.Base58
import java.security.MessageDigest
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

sealed class Opcode(val byte: Byte) {
    data class OP_PUSHBYTES_N(val n: Int, val arg: ByteArray) : Opcode(n.toByte()) {
        init { check(n == arg.size) }
    }

    class OP_CHECKSIG() : Opcode(0xac.toByte())

    companion object {
        fun parseScript(script: ArrayList<Byte>): Opcode? {
            try {
                return when (val byte = script.removeFirst().toUByte()) {
                    in 0x01.toUByte()..0x4b.toUByte() -> {
                        val arg = script.subList(0, byte.toInt()).toByteArray()
                        script.subList(0, byte.toInt()).clear()
                        OP_PUSHBYTES_N(byte.toInt(), arg)
                    }

                    0xac.toUByte() -> OP_CHECKSIG()

                    else -> {
                        //System.err.println("unhandled opcode $byte")
                        null
                    }
                }
            } catch (e: Exception) {
                System.err.println(e)
                return null
            }
        }
    }
}

class Script(val rawScript: ByteArray) {
    val parsedScript: List<Opcode>

    init {
        val script: ArrayList<Byte> = ArrayList(rawScript.toList())
        val parsed: MutableList<Opcode> = mutableListOf()
        while (script.isNotEmpty()) {
            val opcode = Opcode.parseScript(script) ?: break
            parsed.add(opcode)
        }
        parsedScript = parsed.toList()
    }

    // Refs: https://learnmeabitcoin.com/technical/transaction/input/scriptsig/
    //       https://learnmeabitcoin.com/technical/script/p2pk/

    fun scriptPubKeyTarget(): String? {
        val sha256 = MessageDigest.getInstance("SHA256")
        val sha256Hash = fun(input: ByteArray): ByteArray {
            sha256.reset()
            return sha256.digest(input)
        }

        Security.addProvider(BouncyCastleProvider())
        val ripemd160 = MessageDigest.getInstance("RIPEMD160")
        val ripemd160Hash = fun(input: ByteArray): ByteArray {
            ripemd160.reset()
            return ripemd160.digest(input)
        }

        if (parsedScript.size == 2 && parsedScript[1] is Opcode.OP_CHECKSIG) {
            val firstOpcode = parsedScript[0]
            if (firstOpcode is Opcode.OP_PUSHBYTES_N) {
                when (firstOpcode.n) {
                    65 -> { // Uncompressed public key
                        check(firstOpcode.arg[0] == 0x04.toByte())

                        val hash = byteArrayOf(0x00) + ripemd160Hash(sha256Hash(firstOpcode.arg))
                        val checksum = sha256Hash(sha256Hash(hash))
                        val address = hash + checksum.sliceArray(0..<4)
                        return Base58.base58Encode(address)
                    }
                    33 -> { // Compressed public key
                        throw RuntimeException("TODO handle compressed pubkey")
                    }
                    else -> throw RuntimeException("unexpected pubkey length")
                }
            }
        }

        System.err.println("unknown ScriptPubKey target: $parsedScript")
        return null
    }
}
