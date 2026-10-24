package com.applab.docsnap.data

import android.content.Context
import android.net.Uri
import android.os.Build
import com.applab.docsnap.scan.Metrics
import com.applab.docsnap.scan.PageMetric
import com.applab.docsnap.scan.PdfMaker
import com.applab.docsnap.scan.Quality
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Mode { SINGLE, MULTI }

data class Folder(val name: String, val code: String)

data class Draft(
    val id: String,
    val folder: Folder,
    val mode: Mode,
    val startedAt: Long,
    val pages: List<File>,
    val deletedCount: Int,
)

data class Doc(
    val fileName: String,
    val folderName: String,
    val transcript: String,
    val pages: Int,
    val minMp: Double,
    val quality: Quality,
    val createdAt: Long,
    val pdf: File,
)

/**
 * Local-first storage. Everything lives in the app's private files dir:
 *  drafts/<id>/          pages of a document still being scanned (saved after every batch)
 *  docs/<folder>/        finished, named PDFs + a .json with details
 *  analytics/            CSV + JSON logs that will be uploaded to Drive's _DocSnap_Analytics
 */
class Store(private val context: Context) {

    private val root = context.filesDir
    private val draftsDir = File(root, "drafts").apply { mkdirs() }
    private val docsDir = File(root, "docs").apply { mkdirs() }
    val analyticsDir = File(root, "analytics").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("docsnap", Context.MODE_PRIVATE)

    // ---------- settings ----------
    var initials: String
        get() = prefs.getString("initials", "").orEmpty()
        set(v) = prefs.edit().putString("initials", v.uppercase(Locale.ROOT).take(3)).apply()

    var tutorialSeen: Boolean
        get() = prefs.getBoolean("tutorialSeen", false)
        set(v) = prefs.edit().putBoolean("tutorialSeen", v).apply()

    var language: String
        get() = prefs.getString("language", "en-IN") ?: "en-IN"
        set(v) = prefs.edit().putString("language", v).apply()

    val appVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

    // ---------- folders ----------
    /** Test build: Drive connect comes next. Until then, one local test folder. */
    fun folders(): List<Folder> = listOf(Folder("Test", "TEST"))

    // ---------- drafts ----------
    fun newDraft(folder: Folder, mode: Mode): Draft {
        val id = UUID.randomUUID().toString().take(8)
        val dir = File(draftsDir, id).apply { mkdirs() }
        JSONObject().apply {
            put("folder", folder.name); put("code", folder.code)
            put("mode", mode.name); put("startedAt", System.currentTimeMillis())
            put("deleted", 0)
        }.let { File(dir, "draft.json").writeText(it.toString()) }
        return draft(id)!!
    }

    fun draft(id: String): Draft? {
        val dir = File(draftsDir, id)
        val meta = File(dir, "draft.json").takeIf { it.exists() } ?: return null
        val j = JSONObject(meta.readText())
        val pages = dir.listFiles { f -> f.name.startsWith("page_") && f.name.endsWith(".jpg") }
            ?.sortedBy { it.name }.orEmpty()
        return Draft(
            id = id,
            folder = Folder(j.getString("folder"), j.getString("code")),
            mode = Mode.valueOf(j.getString("mode")),
            startedAt = j.getLong("startedAt"),
            pages = pages,
            deletedCount = j.optInt("deleted", 0),
        )
    }

    /** Drafts left behind by a crash, a call or the phone dying. Newest first. */
    fun unfinishedDrafts(): List<Draft> =
        draftsDir.listFiles()?.mapNotNull { draft(it.name) }
            ?.filter { it.pages.isNotEmpty() }
            ?.sortedByDescending { it.startedAt }.orEmpty()

    /** Copies scanner pages into the draft straight away, so a crash loses nothing already returned. */
    fun addPages(draftId: String, uris: List<Uri>): Draft {
        val dir = File(draftsDir, draftId)
        var next = (draft(draftId)?.pages?.size ?: 0) + 1
        for (uri in uris) {
            val target = File(dir, "page_%03d.jpg".format(next++))
            val tmp = File(dir, target.name + ".part")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.renameTo(target)
        }
        val d = draft(draftId)!!
        d.pages.lastOrNull()?.let { last ->
            if (Metrics.page(last).quality == Quality.LOW) event("low_quality_page", "${d.id} ${last.name}")
        }
        return d
    }

    fun deletePage(draftId: String, page: File): File {
        val trash = File(File(draftsDir, draftId), "trash").apply { mkdirs() }
        val moved = File(trash, page.name)
        page.renameTo(moved)
        renumber(draftId)
        bumpDeleted(draftId, +1)
        return moved
    }

    fun undoDelete(draftId: String, trashed: File) {
        val dir = File(draftsDir, draftId)
        // "page_007.jpg" goes back between the current pages 6 and 7
        val n = trashed.name.filter { it.isDigit() }.toIntOrNull() ?: 999
        trashed.renameTo(File(dir, "page_%03d_z.jpg".format(n - 1)))
        renumber(draftId)
        bumpDeleted(draftId, -1)
    }

    private fun renumber(draftId: String) {
        val dir = File(draftsDir, draftId)
        val pages = dir.listFiles { f -> f.name.startsWith("page_") && f.name.endsWith(".jpg") }
            ?.sortedBy { it.name }.orEmpty()
        val temps = pages.mapIndexed { i, f -> File(dir, "tmp_%03d.jpg".format(i + 1)).also { f.renameTo(it) } }
        temps.forEachIndexed { i, f -> f.renameTo(File(dir, "page_%03d.jpg".format(i + 1))) }
    }

    private fun bumpDeleted(draftId: String, by: Int) {
        val meta = File(File(draftsDir, draftId), "draft.json")
        val j = JSONObject(meta.readText())
        j.put("deleted", (j.optInt("deleted", 0) + by).coerceAtLeast(0))
        meta.writeText(j.toString())
    }

    fun discardDraft(draftId: String) {
        File(draftsDir, draftId).deleteRecursively()
        event("draft_discarded", draftId)
    }

    // ---------- finishing a document ----------
    fun previewFileName(draft: Draft, spoken: String): String =
        "%s_%s_%04d_%s.pdf".format(
            draft.folder.code, initials.ifBlank { "XX" }, nextSerial(draft.folder), slug(spoken),
        )

    /** Builds the PDF, writes details + analytics, then removes the draft. Throws if anything fails. */
    fun finish(draft: Draft, spoken: String, nameSource: String): Doc {
        require(spoken.isNotBlank()) { "Name is required" }
        val folderDir = File(docsDir, draft.folder.name).apply { mkdirs() }
        val fileName = previewFileName(draft, spoken)
        val pdf = File(folderDir, fileName)
        PdfMaker.make(draft.pages, pdf)

        val metrics = draft.pages.map { Metrics.page(it) }
        val seconds = ((System.currentTimeMillis() - draft.startedAt) / 1000).toInt()
        val worst = metrics.minByOrNull { it.megapixels }!!
        val createdAt = System.currentTimeMillis()

        JSONObject().apply {
            put("file_name", fileName); put("folder", draft.folder.name)
            put("transcript", spoken.trim()); put("language", language)
            put("name_source", nameSource); put("mode", draft.mode.name)
            put("pages", metrics.size); put("seconds", seconds)
            put("pages_deleted", draft.deletedCount); put("created_at", createdAt)
            put("person", initials); put("status", "LOCAL")
            put("page_metrics", JSONArray(metrics.map { m ->
                JSONObject().put("w", m.widthPx).put("h", m.heightPx)
                    .put("mp", "%.1f".format(m.megapixels)).put("dpi", m.estDpi)
                    .put("bytes", m.bytes).put("quality", m.quality.name)
            }))
        }.let { File(folderDir, fileName.removeSuffix(".pdf") + ".json").writeText(it.toString(2)) }

        logDoc(draft, fileName, spoken, nameSource, metrics, seconds, pdf.length())
        File(draftsDir, draft.id).deleteRecursively()
        return Doc(fileName, draft.folder.name, spoken, metrics.size, worst.megapixels, worst.quality, createdAt, pdf)
    }

    fun docs(folder: Folder): List<Doc> {
        val dir = File(docsDir, folder.name)
        return dir.listFiles { f -> f.name.endsWith(".json") }?.mapNotNull { meta ->
            runCatching {
                val j = JSONObject(meta.readText())
                val pm = j.getJSONArray("page_metrics")
                val mps = (0 until pm.length()).map { pm.getJSONObject(it).getString("mp").toDouble() }
                val minMp = mps.minOrNull() ?: 0.0
                Doc(
                    fileName = j.getString("file_name"),
                    folderName = j.getString("folder"),
                    transcript = j.getString("transcript"),
                    pages = j.getInt("pages"),
                    minMp = minMp,
                    quality = when {
                        minMp >= PageMetric.GOOD_MP -> Quality.GOOD
                        minMp >= PageMetric.OK_MP -> Quality.OK
                        else -> Quality.LOW
                    },
                    createdAt = j.getLong("created_at"),
                    pdf = File(dir, j.getString("file_name")),
                )
            }.getOrNull()
        }?.sortedByDescending { it.createdAt }.orEmpty()
    }

    fun docsToday(): Int {
        val start = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        return folders().sumOf { f ->
            docs(f).count { SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(it.createdAt)) == start }
        }
    }

    private fun nextSerial(folder: Folder): Int {
        val prefix = "${folder.code}_${initials.ifBlank { "XX" }}_"
        val max = File(docsDir, folder.name).listFiles()
            ?.filter { it.name.startsWith(prefix) && it.name.endsWith(".pdf") }
            ?.mapNotNull { it.name.removePrefix(prefix).take(4).toIntOrNull() }
            ?.maxOrNull() ?: 0
        return max + 1
    }

    // ---------- analytics (uploaded to Drive _DocSnap_Analytics in the Drive build) ----------
    private fun who() = initials.ifBlank { "XX" }
    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun append(name: String, header: String, row: String) {
        val f = File(analyticsDir, name)
        if (!f.exists()) f.writeText(header + "\n")
        f.appendText(row + "\n")
    }

    private fun logDoc(
        draft: Draft, fileName: String, spoken: String, nameSource: String,
        metrics: List<PageMetric>, seconds: Int, bytes: Long,
    ) {
        append(
            "docs_${who()}.csv",
            "time,file_name,folder,mode,pages,min_mp,avg_mp,min_dpi,worst_quality,page_sizes,pdf_bytes,seconds,pages_deleted,name_source,language,transcript,status",
            listOf(
                now(), fileName, draft.folder.name, draft.mode.name, metrics.size.toString(),
                "%.1f".format(metrics.minOf { it.megapixels }),
                "%.1f".format(metrics.map { it.megapixels }.average()),
                metrics.minOf { it.estDpi }.toString(),
                metrics.minByOrNull { it.megapixels }!!.quality.name,
                csv(metrics.joinToString(" ") { "${it.widthPx}x${it.heightPx}" }),
                bytes.toString(), seconds.toString(), draft.deletedCount.toString(),
                nameSource, language, csv(spoken.trim()), "LOCAL",
            ).joinToString(","),
        )
    }

    fun event(type: String, detail: String) =
        append("events_${who()}.csv", "time,type,detail", listOf(now(), type, csv(detail)).joinToString(","))

    fun feedback(text: String) =
        append("feedback_${who()}.csv", "time,model,text", listOf(now(), csv(Build.MODEL), csv(text)).joinToString(","))

    fun writeDeviceInfo() {
        val model = Build.MODEL.replace(Regex("[^A-Za-z0-9]+"), "-")
        File(analyticsDir, "device_${who()}_$model.json")
            .writeText(Metrics.device(context, who(), appVersion).toString(2))
    }

    /** Average and minimum megapixels of every page scanned on this phone so far. */
    fun pageStats(): Pair<Double, Double>? {
        val all = docsDir.listFiles()?.flatMap { d ->
            d.listFiles { f -> f.name.endsWith(".json") }?.toList().orEmpty()
        }?.flatMap { meta ->
            runCatching {
                val pm = JSONObject(meta.readText()).getJSONArray("page_metrics")
                (0 until pm.length()).map { pm.getJSONObject(it).getString("mp").toDouble() }
            }.getOrDefault(emptyList())
        }.orEmpty()
        if (all.isEmpty()) return null
        return all.average() to all.min()
    }

    companion object {
        /** "survey 123 Bijapur 7/12 extract" -> "survey-123-bijapur-7-12-extract" (Kannada -> Latin letters). */
        fun slug(text: String): String {
            var s = text.trim()
            if (Build.VERSION.SDK_INT >= 29) {
                s = runCatching {
                    android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII").transliterate(s)
                }.getOrDefault(s)
            }
            return s.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(60)
                .trimEnd('-')
                .ifBlank { "unnamed" }
        }
    }
}
