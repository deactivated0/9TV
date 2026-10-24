package com.deactivated0.tv9

import java.io.ByteArrayOutputStream

class Gua {

    fun verify(str: String): Boolean {
        if (str.isEmpty()) return false
        val len = minOf(str.length, 16)
        var validCount = 0
        for (i in 0 until len) {
            val c = str[i].code
            if (c in 0x4DC0..0x4DFF || c == 0x3007) {
                validCount++
            } else if (!str[i].isWhitespace()) {
                return false
            }
        }
        return validCount > 0
    }

    fun decode(str: String): String {
        val len = str.length
        val out = ByteArrayOutputStream((len * 3) / 4 + 4)
        val buf = IntArray(4)
        var count = 0

        for (i in 0 until len) {
            val code = str[i].code
            val v = when {
                code in 0x4DC0..0x4DFF -> code - 0x4DC0
                code == 0x3007 -> -1
                else -> continue
            }
            buf[count++] = v
            if (count == 4) {
                out.write(((buf[0] and 0x3F) shl 2) or ((buf[1] ushr 4) and 0x03))
                if (buf[2] != -1) {
                    out.write(((buf[1] and 0x0F) shl 4) or ((buf[2] ushr 2) and 0x0F))
                }
                if (buf[3] != -1) {
                    out.write(((buf[2] and 0x03) shl 6) or (buf[3] and 0x3F))
                }
                count = 0
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    fun encode(str: String): String {
        val bytes = str.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1

            sb.append((0x4DC0 + (b0 ushr 2)).toChar())
            if (b1 != -1) {
                sb.append((0x4DC0 + (((b0 and 0x03) shl 4) or (b1 ushr 4))).toChar())
                if (b2 != -1) {
                    sb.append((0x4DC0 + (((b1 and 0x0F) shl 2) or (b2 ushr 6))).toChar())
                    sb.append((0x4DC0 + (b2 and 0x3F)).toChar())
                } else {
                    sb.append((0x4DC0 + ((b1 and 0x0F) shl 2)).toChar())
                    sb.append('\u3007')
                }
            } else {
                sb.append((0x4DC0 + ((b0 and 0x03) shl 4)).toChar())
                sb.append('\u3007')
                sb.append('\u3007')
            }
            i += 3
        }
        return sb.toString()
    }
}
