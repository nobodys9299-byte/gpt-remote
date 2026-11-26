package com.gptremote.mitv.wifi

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal dependency-free Protobuf wire format encoder/decoder for Android TV Remote v2.
 */
object ProtoWire {
    const val WIRE_VARINT = 0
    const val WIRE_64BIT = 1
    const val WIRE_LENGTH_DELIMITED = 2
    const val WIRE_32BIT = 5

    fun writeVarint(out: OutputStream, value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v.toInt() and 0x7F) or 0x80))
            v = v ushr 7
        }
    }

    fun readVarint(input: InputStream): Long {
        var shift = 0
        var result = 0L
        while (shift < 64) {
            val b = input.read()
            if (b == -1) throw EOFException("Unexpected EOF while reading varint")
            result = result or ((b.toLong() and 0x7FL) shl shift)
            if ((b and 0x80) == 0) {
                return result
            }
            shift += 7
        }
        throw IllegalStateException("Malformed varint (> 64 bits)")
    }

    fun writeFrame(out: OutputStream, payload: ByteArray) {
        writeVarint(out, payload.size.toLong())
        out.write(payload)
        out.flush()
    }

    fun readFrame(input: InputStream): ByteArray {
        val length = readVarint(input).toInt()
        require(length in 0..(1024 * 1024)) { "Invalid frame length: $length" }
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read == -1) throw EOFException("Unexpected EOF while reading frame body")
            offset += read
        }
        return buffer
    }

    class Writer {
        private val buffer = ByteArrayOutputStream()

        fun writeVarintField(fieldNumber: Int, value: Int) {
            writeVarintField(fieldNumber, value.toLong())
        }

        fun writeVarintField(fieldNumber: Int, value: Long) {
            val tag = (fieldNumber shl 3) or WIRE_VARINT
            writeVarint(buffer, tag.toLong())
            writeVarint(buffer, value)
        }

        fun writeBytesField(fieldNumber: Int, data: ByteArray) {
            val tag = (fieldNumber shl 3) or WIRE_LENGTH_DELIMITED
            writeVarint(buffer, tag.toLong())
            writeVarint(buffer, data.size.toLong())
            buffer.write(data)
        }

        fun writeStringField(fieldNumber: Int, value: String) {
            writeBytesField(fieldNumber, value.toByteArray(Charsets.UTF_8))
        }

        fun writeMessageField(fieldNumber: Int, block: Writer.() -> Unit) {
            val nested = Writer().apply(block).toByteArray()
            writeBytesField(fieldNumber, nested)
        }

        fun toByteArray(): ByteArray = buffer.toByteArray()
    }

    data class Field(
        val number: Int,
        val wireType: Int,
        val varint: Long = 0L,
        val bytes: ByteArray = ByteArray(0)
    )

    fun parseFields(payload: ByteArray): List<Field> {
        val input = payload.inputStream()
        val fields = mutableListOf<Field>()
        while (input.available() > 0) {
            val tag = readVarint(input).toInt()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (wireType) {
                WIRE_VARINT -> {
                    fields += Field(fieldNumber, wireType, varint = readVarint(input))
                }
                WIRE_LENGTH_DELIMITED -> {
                    val len = readVarint(input).toInt()
                    val data = ByteArray(len)
                    val read = input.read(data)
                    if (read != len) throw EOFException("Truncated length-delimited field")
                    fields += Field(fieldNumber, wireType, bytes = data)
                }
                WIRE_64BIT -> {
                    val skip = ByteArray(8)
                    input.read(skip)
                    fields += Field(fieldNumber, wireType, bytes = skip)
                }
                WIRE_32BIT -> {
                    val skip = ByteArray(4)
                    input.read(skip)
                    fields += Field(fieldNumber, wireType, bytes = skip)
                }
                else -> throw IllegalStateException("Unsupported wire type: $wireType")
            }
        }
        return fields
    }
}
