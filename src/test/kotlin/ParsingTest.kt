package com.github.jo_makar

import java.io.ByteArrayInputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class XorFileStreamTest {
    @Test fun readUShortMax() {
        val stream = XorFileStream(
            ByteArrayInputStream(byteArrayOf(0xff.toByte(), 0xff.toByte())),
            byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        )
        assertEquals(65535.toUShort(), stream.readUShort())
    }

    @Test fun readUIntMax() {
        val stream = XorFileStream(
            ByteArrayInputStream(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte())),
            byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        )
        assertEquals(4294967295.toUInt(), stream.readUInt())
    }

    @Test fun readULongMax() {
        val stream = XorFileStream(
            ByteArrayInputStream(byteArrayOf(
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte()
            )),
            byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        )
        assertEquals(18446744073709551615UL, stream.readULong())
    }
}
