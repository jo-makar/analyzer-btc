package com.github.jo_makar

import java.io.File
import java.io.FileInputStream
import kotlin.experimental.xor

class XorFileStream(file: File, val xorKey: ByteArray) {
    private val stream = FileInputStream(file)
    private var xorKeyIdx = 0
    init { check(xorKey.size == 8) }

    fun readNBytes(n: Int): ByteArray {
        val buffer = stream.readNBytes(n)
        check(buffer.size == n)

        for (i in 0..<buffer.size) {
            buffer[i] = buffer[i] xor xorKey[xorKeyIdx]
            xorKeyIdx = (xorKeyIdx + 1) % 8
        }
        return buffer
    }
}

