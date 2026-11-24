package com.webify.app.build

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Penulis ZIP dengan zipalign bawaan: setiap entri STORED (tidak terkompresi) data-nya
 * diletakkan pada batas kelipatan 4 byte (dan 16 KiB untuk .so) memakai extra field
 * padding 0xD935 — identik dengan perilaku `zipalign -p 4`.
 */
class AlignedZipWriter(file: File) : AutoCloseable {

    private class CountingStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private val counting = CountingStream(file.outputStream().buffered(256 * 1024))
    private val zos = ZipOutputStream(counting)
    private val written = HashSet<String>()

    fun has(name: String) = name in written

    /** @param compress false → STORED + aligned. */
    fun put(name: String, data: ByteArray, compress: Boolean) {
        require(written.add(name)) { "Entri duplikat: $name" }
        val entry = ZipEntry(name)
        entry.time = FIXED_TIME
        if (compress) {
            entry.method = ZipEntry.DEFLATED
            zos.putNextEntry(entry)
        } else {
            val crc = CRC32().apply { update(data) }
            entry.method = ZipEntry.STORED
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            entry.crc = crc.value
            val alignment = if (name.endsWith(".so")) 16384 else 4
            val nameLen = name.toByteArray(Charsets.UTF_8).size
            val headerEnd = counting.count + 30 + nameLen
            // extra field minimal 6 byte (id 2 + size 2 + alignment 2)
            var extraLen = 6
            while ((headerEnd + extraLen) % alignment != 0L) extraLen++
            val extra = ByteArray(extraLen)
            extra[0] = 0x35; extra[1] = 0xD9.toByte()           // header ID 0xD935 (little-endian)
            val dataSize = extraLen - 4
            extra[2] = (dataSize and 0xFF).toByte(); extra[3] = ((dataSize shr 8) and 0xFF).toByte()
            extra[4] = (alignment and 0xFF).toByte(); extra[5] = ((alignment shr 8) and 0xFF).toByte()
            entry.extra = extra
            zos.putNextEntry(entry)
        }
        zos.write(data)
        zos.closeEntry()
    }

    override fun close() {
        zos.close()
    }

    companion object {
        const val FIXED_TIME = 347155200000L

        /** Ekstensi yang dibiarkan tidak terkompresi (mengikuti default AGP). */
        private val NO_COMPRESS = setOf(
            "arsc", "png", "jpg", "jpeg", "gif", "webp", "mp3", "mp4", "ogg", "wav", "m4a", "aac",
            "3gp", "mkv", "webm", "woff", "woff2", "ttf", "otf", "so"
        )

        fun shouldStore(name: String): Boolean {
            if (name == "resources.arsc") return true // wajib STORED & aligned untuk targetSdk ≥ 30
            return name.substringAfterLast('.', "").lowercase() in NO_COMPRESS
        }
    }
}

/** Verifikasi alignment ala `zipalign -c 4`. Mengembalikan daftar entri yang tidak sejajar. */
object ZipAlignVerifier {
    fun misaligned(file: File): List<String> {
        val bad = mutableListOf<String>()
        java.io.RandomAccessFile(file, "r").use { raf ->
            java.util.zip.ZipFile(file).use { zip ->
                // Baca central directory secara manual via local header offset tidak tersedia di API;
                // jadi scan local headers berurutan.
                var pos = 0L
                val len = raf.length()
                val buf = ByteArray(30)
                while (pos + 30 <= len) {
                    raf.seek(pos); raf.readFully(buf)
                    val sig = le32(buf, 0)
                    if (sig != 0x04034b50L) break
                    val flags = le16(buf, 6)
                    val method = le16(buf, 8)
                    val compSize = le32(buf, 18)
                    val nameLen = le16(buf, 26)
                    val extraLen = le16(buf, 28)
                    val nameBytes = ByteArray(nameLen); raf.readFully(nameBytes)
                    val name = String(nameBytes, Charsets.UTF_8)
                    val dataStart = pos + 30 + nameLen + extraLen
                    if (method == 0) {
                        val a = if (name.endsWith(".so")) 16384 else 4
                        if (dataStart % a != 0L) bad += name
                    }
                    var size = compSize
                    if (flags and 0x08 != 0 && method != 0) {
                        size = zip.getEntry(name)?.compressedSize ?: break
                    }
                    pos = dataStart + size
                    if (flags and 0x08 != 0) {
                        // data descriptor (opsional bersignature)
                        raf.seek(pos)
                        val d = ByteArray(4); raf.readFully(d)
                        pos += if (le32(d, 0) == 0x08074b50L) 16 else 12
                    }
                }
            }
        }
        return bad
    }

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
}
