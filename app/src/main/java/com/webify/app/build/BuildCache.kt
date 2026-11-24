package com.webify.app.build

import java.io.File
import java.security.MessageDigest

/**
 * Cache build inkremental per proyek di filesDir/webify_build_cache/<projectId>/.
 *
 * Setiap tahap mahal menyimpan keluarannya bersama sidik jari (SHA-256) seluruh inputnya.
 * Bila sidik jari build berikutnya sama, keluaran disalin dari cache dan tahap dilewati:
 *  - aapt2 compile  : input = isi folder res/
 *  - ecj            : input = seluruh .java + JAR library di classpath
 *  - d8 (library)   : input = JAR library + minSdk + mode → pre-dex disimpan terpisah,
 *                     sehingga build berikutnya hanya men-dex kode aplikasi lalu menggabungkan.
 */
class BuildCache(root: File) {

    val dir: File = root.apply { mkdirs() }

    /** Sidik jari isi folder (path relatif + isi file), urutan deterministik. */
    fun hashDir(vararg dirs: File, extra: String = ""): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(extra.toByteArray())
        for (d in dirs) {
            if (!d.exists()) continue
            d.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(d).path }.forEach { f ->
                md.update(f.relativeTo(d).path.toByteArray())
                md.update(0)
                f.inputStream().use { ins ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        md.update(buf, 0, n)
                    }
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Sidik jari ringan untuk file besar yang tidak berubah isinya tanpa berubah ukuran/waktu (JAR di cache Maven). */
    fun hashFilesMeta(files: List<File>, extra: String = ""): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(extra.toByteArray())
        files.sortedBy { it.absolutePath }.forEach { f ->
            md.update("${f.absolutePath}|${f.length()}|${f.lastModified()}\n".toByteArray())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun keyFile(name: String) = File(dir, "$name.key")
    fun slot(name: String) = File(dir, name)

    /** true bila cache [name] cocok dengan [key] dan keluarannya ada. */
    fun hit(name: String, key: String): Boolean {
        val k = keyFile(name)
        val out = slot(name)
        return k.exists() && k.readText() == key && out.exists() &&
            (out.isFile && out.length() > 0 || out.isDirectory && !out.listFiles().isNullOrEmpty())
    }

    /** Simpan [source] (file/folder) ke slot [name] dengan kunci [key]. */
    fun store(name: String, key: String, source: File) {
        val out = slot(name)
        val k = keyFile(name)
        k.delete()
        out.deleteRecursively()
        if (source.isDirectory) source.copyRecursively(out, overwrite = true) else source.copyTo(out, overwrite = true)
        k.writeText(key)
    }

    /** Salin isi slot [name] ke [target] (folder dikosongkan dulu). */
    fun restore(name: String, target: File) {
        val src = slot(name)
        if (src.isDirectory) {
            target.deleteRecursively()
            src.copyRecursively(target, overwrite = true)
        } else {
            target.parentFile?.mkdirs()
            src.copyTo(target, overwrite = true)
        }
    }

    fun sizeBytes(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        fun forProject(filesDir: File, projectId: String) = BuildCache(File(filesDir, "webify_build_cache/$projectId"))

        fun clearAll(filesDir: File): Long {
            val d = File(filesDir, "webify_build_cache")
            val size = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            d.deleteRecursively()
            return size
        }

        fun totalSize(filesDir: File): Long =
            File(filesDir, "webify_build_cache").walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
