package com.webify.app.build

import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

// =============================================================================
// Model
// =============================================================================

data class MavenCoordinate(val group: String, val artifact: String, val version: String) {
    val key get() = "$group:$artifact"
    override fun toString() = "$group:$artifact:$version"

    companion object {
        fun parse(raw: String): MavenCoordinate? {
            val parts = raw.trim().removeSuffix("@aar").split(':')
            if (parts.size < 3 || parts.any { it.isBlank() }) return null
            return MavenCoordinate(parts[0].trim(), parts[1].trim(), parts[2].trim())
        }
    }
}

/** Satu library hasil ekstraksi (AAR) atau JAR polos. */
data class ExtractedLibrary(
    val name: String,
    val jars: List<File>,
    val resDir: File?,
    val manifest: File?,
    val packageName: String?,
    val assetsDir: File?,
    val jniDir: File?,
    val minSdk: Int?,
    val root: File
)

/** Hasil tahap dependensi untuk dipakai tahap aapt2/ecj/d8/package. */
data class DependencySet(
    val libraries: List<ExtractedLibrary>,
    val compiledResZips: List<File>,
    val extraPackages: List<String>
) {
    val jars: List<File> get() = libraries.flatMap { it.jars }
    val isEmpty: Boolean get() = libraries.isEmpty()

    companion object { val EMPTY = DependencySet(emptyList(), emptyList(), emptyList()) }
}

/** Katalog plugin SDK siap pakai (versi dapat diubah lewat dependensi tambahan). */
object PluginCatalog {
    const val FCM = "com.google.firebase:firebase-messaging:23.4.1"
    const val ADMOB = "com.google.android.gms:play-services-ads:23.0.0"
    const val ZXING = "com.google.zxing:core:3.5.3"
}

// =============================================================================
// Versi Maven
// =============================================================================

object MavenVersion {
    /** Ambil versi konkret dari notasi Maven: "[1.2.3]" → 1.2.3, "[1.0,2.0)" → 1.0. */
    fun normalize(raw: String): String {
        val v = raw.trim()
        if (v.startsWith("[") || v.startsWith("(")) {
            val inner = v.trim('[', ']', '(', ')')
            return inner.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: inner
        }
        return v
    }

    /** Perbandingan versi gaya Maven sederhana: angka dibanding numerik, qualifier (alpha/beta/rc) < rilis. */
    fun compare(a: String, b: String): Int {
        val pa = tokenize(a)
        val pb = tokenize(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i)
            val y = pb.getOrNull(i)
            if (x == y) continue
            if (x == null) return if (y is String) 1 else -1
            if (y == null) return if (x is String) -1 else 1
            val c = when {
                x is Long && y is Long -> x.compareTo(y)
                x is Long -> 1
                y is Long -> -1
                else -> qualifierRank(x as String).compareTo(qualifierRank(y as String)).let { if (it != 0) it else x.compareTo(y) }
            }
            if (c != 0) return c
        }
        return 0
    }

    private fun tokenize(v: String): List<Any> = v.lowercase().split('.', '-', '_')
        .filter { it.isNotEmpty() }
        .flatMap { part ->
            Regex("\\d+|[a-z]+").findAll(part).map { m -> m.value.toLongOrNull() ?: m.value }.toList()
        }

    private fun qualifierRank(q: String) = when {
        q.startsWith("alpha") || q == "a" -> 1
        q.startsWith("beta") || q == "b" -> 2
        q.startsWith("rc") || q.startsWith("cr") -> 3
        q == "snapshot" -> 0
        else -> 4
    }
}

// =============================================================================
// POM
// =============================================================================

data class PomInfo(
    val coordinate: MavenCoordinate,
    val packaging: String,
    val dependencies: List<MavenCoordinate>,
    val unresolved: List<String>
)

object PomParser {
    /**
     * @param parentLookup memuat POM parent (properti & dependencyManagement diwariskan).
     */
    fun parse(xml: String, self: MavenCoordinate, parentLookup: (MavenCoordinate) -> String?): PomInfo {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder()
            .parse(xml.byteInputStream())
        val project = doc.documentElement
        val props = HashMap<String, String>()
        val managed = HashMap<String, String>()

        // Warisan dari parent (maks 5 tingkat)
        var parentEl = child(project, "parent")
        var depth = 0
        val parentChain = ArrayList<Element>()
        while (parentEl != null && depth < 5) {
            val pc = MavenCoordinate(text(parentEl, "groupId") ?: break, text(parentEl, "artifactId") ?: break, text(parentEl, "version") ?: break)
            val pxml = parentLookup(pc) ?: break
            val pdoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pxml.byteInputStream()).documentElement
            parentChain.add(0, pdoc)
            props["project.parent.version"] = pc.version
            parentEl = child(pdoc, "parent")
            depth++
        }
        for (p in parentChain) {
            collectProps(p, props)
            collectManaged(p, props, managed)
        }
        collectProps(project, props)
        props["project.groupId"] = self.group
        props["project.artifactId"] = self.artifact
        props["project.version"] = self.version
        props["pom.version"] = self.version
        props["version"] = self.version
        collectManaged(project, props, managed)

        val packaging = text(project, "packaging")?.let { resolve(it, props) } ?: "jar"
        val deps = ArrayList<MavenCoordinate>()
        val unresolved = ArrayList<String>()
        child(project, "dependencies")?.let { depsEl ->
            for (d in children(depsEl, "dependency")) {
                val scope = text(d, "scope")?.trim()?.lowercase()
                val optional = text(d, "optional")?.trim()?.equals("true", true) == true
                if (optional || (scope != null && scope !in setOf("compile", "runtime"))) continue
                val type = text(d, "type")?.trim()?.lowercase()
                if (type != null && type !in setOf("jar", "aar", "bundle")) continue
                val g = text(d, "groupId")?.let { resolve(it, props) } ?: continue
                val a = text(d, "artifactId")?.let { resolve(it, props) } ?: continue
                val v = text(d, "version")?.let { resolve(it, props) } ?: managed["$g:$a"]
                if (v.isNullOrBlank() || v.contains("\${")) {
                    unresolved += "$g:$a"
                    continue
                }
                deps += MavenCoordinate(g, a, MavenVersion.normalize(v))
            }
        }
        return PomInfo(self, packaging, deps, unresolved)
    }

    private fun collectProps(root: Element, props: MutableMap<String, String>) {
        child(root, "properties")?.let { p ->
            val nodes = p.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                if (n is Element) props[n.tagName] = n.textContent.trim()
            }
        }
        text(root, "version")?.let { props.putIfAbsent("project.version", it) }
    }

    private fun collectManaged(root: Element, props: Map<String, String>, managed: MutableMap<String, String>) {
        val dm = child(root, "dependencyManagement")?.let { child(it, "dependencies") } ?: return
        for (d in children(dm, "dependency")) {
            val g = text(d, "groupId")?.let { resolve(it, props) } ?: continue
            val a = text(d, "artifactId")?.let { resolve(it, props) } ?: continue
            val v = text(d, "version")?.let { resolve(it, props) } ?: continue
            managed["$g:$a"] = MavenVersion.normalize(v)
        }
    }

    private fun resolve(value: String, props: Map<String, String>): String {
        var v = value.trim()
        repeat(5) {
            val m = Regex("\\$\\{([^}]+)}").find(v) ?: return v
            val replacement = props[m.groupValues[1]] ?: return v
            v = v.replaceRange(m.range, replacement)
        }
        return v
    }

    private fun child(e: Element, name: String): Element? {
        val nodes = e.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element && n.tagName == name) return n
        }
        return null
    }

    private fun children(e: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = e.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element && n.tagName == name) out += n
        }
        return out
    }

    private fun text(e: Element, name: String): String? = child(e, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}

// =============================================================================
// Manager
// =============================================================================

/**
 * Pengelola library eksternal di filesDir/webify_sdk_repo/:
 *  1. Resolusi transitif Maven (Google Maven + Maven Central), versi tertinggi menang.
 *  2. Unduh & cache .aar/.jar + .pom.
 *  3. Ekstraksi AAR → classes.jar, libs/(semua .jar), res/, AndroidManifest.xml, assets/, jni/.
 *  4. Kompilasi resource tiap AAR dengan aapt2 (di-cache).
 */
class AarDependencyManager(private val repoRoot: File, private val aapt2: Aapt2Wrapper) {

    private val repos = listOf(
        "https://dl.google.com/dl/android/maven2/",
        "https://repo1.maven.org/maven2/"
    )
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Artefak yang tidak perlu dibundel (anotasi waktu-kompilasi / stub kosong). */
    private val skip = setOf(
        "com.google.guava:listenablefuture",
        "org.jetbrains:annotations",
        "com.google.errorprone:error_prone_annotations",
        "com.google.code.findbugs:jsr305",
        "org.checkerframework:checker-qual",
        "org.checkerframework:checker-compat-qual",
        "com.google.j2objc:j2objc-annotations",
        "org.codehaus.mojo:animal-sniffer-annotations",
        "javax.inject:javax.inject"
    )

    suspend fun prepare(
        coordinates: List<MavenCoordinate>,
        localFiles: List<File>,
        projectMinSdk: Int,
        log: BuildLogger
    ): DependencySet {
        if (coordinates.isEmpty() && localFiles.isEmpty()) return DependencySet.EMPTY
        repoRoot.mkdirs()

        val resolved = resolve(coordinates, log)
        val libs = ArrayList<ExtractedLibrary>()
        for (c in resolved) {
            val file = download(c, log)
            libs += extract(file, "$c")
        }
        for (f in localFiles) {
            if (!f.exists()) throw BuildStageException(BuildStage.DEPENDENCIES, "Library lokal tidak ditemukan: ${f.name}")
            libs += extract(f, f.name)
        }

        libs.filter { (it.minSdk ?: 0) > projectMinSdk }.forEach {
            log.warn("${it.name} membutuhkan minSdk ${it.minSdk} (proyek: $projectMinSdk) — naikkan minSdk agar tidak crash di perangkat lama")
        }

        // Kompilasi resource tiap AAR (urutan: dependensi terdalam dulu → overlay aplikasi terakhir)
        val resZips = ArrayList<File>()
        for (lib in libs.asReversed()) {
            val res = lib.resDir ?: continue
            if (res.listFiles().isNullOrEmpty()) continue
            val out = File(lib.root, "compiled_res.zip")
            if (!out.exists() || out.length() == 0L) {
                aapt2.compileDir(res, out, log)
            }
            resZips += out
        }
        val packages = libs.mapNotNull { it.packageName }.distinct()
        log.info("Dependensi siap: ${libs.size} library, ${libs.sumOf { it.jars.size }} JAR, ${resZips.size} paket resource, ${packages.size} paket R")
        return DependencySet(libs, resZips, packages)
    }

    // ------------------------------------------------------------------ Resolusi
    private fun resolve(roots: List<MavenCoordinate>, log: BuildLogger): List<MavenCoordinate> {
        val chosen = LinkedHashMap<String, MavenCoordinate>()
        val queue = ArrayDeque(roots)
        val visited = HashSet<String>()
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 2000) {
            val c = queue.removeFirst()
            if (c.key in skip) continue
            val current = chosen[c.key]
            if (current != null && MavenVersion.compare(current.version, c.version) >= 0) continue
            chosen[c.key] = c
            if (!visited.add(c.toString())) continue
            val pom = try {
                PomParser.parse(fetchText(pomPath(c)), c) { p -> runCatching { fetchText(pomPath(p)) }.getOrNull() }
            } catch (e: IOException) {
                throw BuildStageException(BuildStage.DEPENDENCIES, "POM $c tidak dapat diunduh: ${e.message}. Periksa koneksi internet (hanya diperlukan sekali, lalu di-cache).", e)
            }
            pom.unresolved.forEach { log.warn("Versi tidak ditemukan untuk $it (dilewati)") }
            pomPackaging[c.toString()] = pom.packaging
            queue.addAll(pom.dependencies)
        }
        log.info("Resolusi Maven: ${chosen.size} artefak (${roots.joinToString { it.artifact }})")
        return chosen.values.toList()
    }

    private val pomPackaging = HashMap<String, String>()

    private fun pomPath(c: MavenCoordinate) = "${c.group.replace('.', '/')}/${c.artifact}/${c.version}/${c.artifact}-${c.version}.pom"

    private fun fetchText(path: String): String = String(fetchBytes(path, cache = true), Charsets.UTF_8)

    private fun fetchBytes(path: String, cache: Boolean): ByteArray {
        val local = File(repoRoot, "maven/$path")
        if (cache && local.exists() && local.length() > 0) return local.readBytes()
        var lastError: Exception? = null
        for (base in repos) {
            try {
                http.newCall(Request.Builder().url(base + path).build()).execute().use { resp ->
                    if (resp.code == 404) return@use
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} dari $base")
                    val bytes = resp.body?.bytes() ?: throw IOException("Respons kosong")
                    local.parentFile?.mkdirs()
                    val tmp = File(local.parentFile, local.name + ".part")
                    tmp.writeBytes(bytes)
                    tmp.renameTo(local)
                    return bytes
                }
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw IOException("Tidak ditemukan di repositori: $path", lastError)
    }

    private fun download(c: MavenCoordinate, log: BuildLogger): File {
        val packaging = pomPackaging[c.toString()] ?: "jar"
        val exts = if (packaging == "aar") listOf("aar", "jar") else listOf("jar", "aar")
        for (ext in exts) {
            val path = "${c.group.replace('.', '/')}/${c.artifact}/${c.version}/${c.artifact}-${c.version}.$ext"
            val local = File(repoRoot, "maven/$path")
            if (local.exists() && local.length() > 0) return local
            try {
                fetchBytes(path, cache = true)
                log.debug("↓ $c ($ext, ${local.length() / 1024} KB)")
                return local
            } catch (_: IOException) {
                // coba ekstensi berikutnya
            }
        }
        throw BuildStageException(BuildStage.DEPENDENCIES, "Artefak $c tidak dapat diunduh")
    }

    // ------------------------------------------------------------------ Ekstraksi
    private fun extract(file: File, name: String): ExtractedLibrary {
        if (file.extension.equals("jar", true)) {
            return ExtractedLibrary(name, listOf(file), null, null, null, null, null, null, file.parentFile ?: repoRoot)
        }
        val out = File(repoRoot, "extracted/" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val stamp = File(out, ".stamp")
        val stampValue = "${file.length()}-${file.lastModified()}"
        if (!stamp.exists() || stamp.readText() != stampValue) {
            out.deleteRecursively()
            out.mkdirs()
            val rootCanonical = out.canonicalPath + File.separator
            ZipFile(file).use { zip ->
                for (e in zip.entries().toList()) {
                    if (e.isDirectory) continue
                    val target = File(out, e.name)
                    if (!target.canonicalPath.startsWith(rootCanonical)) continue
                    target.parentFile?.mkdirs()
                    zip.getInputStream(e).use { ins -> target.outputStream().use { ins.copyTo(it) } }
                }
            }
            stamp.writeText(stampValue)
        }
        val jars = buildList {
            File(out, "classes.jar").takeIf { it.exists() && it.length() > 22 }?.let { add(it) }
            File(out, "libs").listFiles { f -> f.extension == "jar" }?.sortedBy { it.name }?.let { addAll(it) }
        }
        val manifest = File(out, "AndroidManifest.xml").takeIf { it.exists() }
        val (pkg, minSdk) = manifest?.let { readManifestInfo(it) } ?: (null to null)
        return ExtractedLibrary(
            name = name,
            jars = jars,
            resDir = File(out, "res").takeIf { it.isDirectory },
            manifest = manifest,
            packageName = pkg,
            assetsDir = File(out, "assets").takeIf { it.isDirectory },
            jniDir = File(out, "jni").takeIf { it.isDirectory },
            minSdk = minSdk,
            root = out
        )
    }

    private fun readManifestInfo(f: File): Pair<String?, Int?> = try {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(f)
        val root = doc.documentElement
        val pkg = root.getAttribute("package").takeIf { it.isNotBlank() }
        val usesSdk = root.getElementsByTagName("uses-sdk").let { if (it.length > 0) it.item(0) as Element else null }
        val min = usesSdk?.getAttributeNS(AarManifestMerger.ANDROID_NS, "minSdkVersion")?.toIntOrNull()
        pkg to min
    } catch (_: Exception) {
        null to null
    }
}
