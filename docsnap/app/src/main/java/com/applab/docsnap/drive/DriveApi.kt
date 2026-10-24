package com.applab.docsnap.drive

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DriveFolder(val id: String, val name: String)

class DriveException(val code: Int, message: String) : IOException(message)

/**
 * Small Google Drive v3 REST client (no heavy SDK). Every call is blocking: run on Dispatchers.IO.
 * supportsAllDrives=true so a folder inside a Shared Drive works too.
 */
class DriveApi(private val token: String) {

    private val api = "https://www.googleapis.com/drive/v3"
    private val upload = "https://www.googleapis.com/upload/drive/v3"

    fun myEmail(): String =
        JSONObject(get("$api/about?fields=user(emailAddress)")).getJSONObject("user").optString("emailAddress")

    /** Name + whether this account may add files into it. Throws DriveException(404) if not shared with them. */
    fun folder(id: String): Pair<DriveFolder, Boolean> {
        val j = JSONObject(get("$api/files/${enc(id)}?fields=id,name,mimeType,trashed,capabilities(canAddChildren)&supportsAllDrives=true"))
        if (j.optString("mimeType") != FOLDER_MIME) throw DriveException(400, "That link is a file, not a folder")
        if (j.optBoolean("trashed")) throw DriveException(410, "That folder is in the Drive bin")
        val canAdd = j.optJSONObject("capabilities")?.optBoolean("canAddChildren") ?: false
        return DriveFolder(j.getString("id"), j.getString("name")) to canAdd
    }

    fun subfolders(parentId: String): List<DriveFolder> {
        val out = mutableListOf<DriveFolder>()
        var pageToken: String? = null
        do {
            val q = "'$parentId' in parents and mimeType='$FOLDER_MIME' and trashed=false"
            val url = "$api/files?q=${enc(q)}&fields=nextPageToken,files(id,name)&pageSize=1000&orderBy=name" +
                "&supportsAllDrives=true&includeItemsFromAllDrives=true" + (pageToken?.let { "&pageToken=${enc(it)}" } ?: "")
            val j = JSONObject(get(url))
            val files = j.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) files.getJSONObject(i).let { out += DriveFolder(it.getString("id"), it.getString("name")) }
            pageToken = j.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        return out
    }

    fun findChild(parentId: String, name: String, folder: Boolean): String? {
        val q = buildString {
            append("'$parentId' in parents and name='${name.replace("'", "\\'")}' and trashed=false")
            if (folder) append(" and mimeType='$FOLDER_MIME'")
        }
        val j = JSONObject(get("$api/files?q=${enc(q)}&fields=files(id)&pageSize=1&supportsAllDrives=true&includeItemsFromAllDrives=true"))
        return j.optJSONArray("files")?.takeIf { it.length() > 0 }?.getJSONObject(0)?.getString("id")
    }

    fun createFolder(parentId: String, name: String): String {
        val body = JSONObject().put("name", name).put("mimeType", FOLDER_MIME).put("parents", JSONArray().put(parentId))
        return JSONObject(send("POST", "$api/files?fields=id&supportsAllDrives=true", body.toString().toByteArray(), JSON_MIME))
            .getString("id")
    }

    fun ensureFolder(parentId: String, name: String): String = findChild(parentId, name, true) ?: createFolder(parentId, name)

    /** Already uploaded by anyone with the same content? (sha256 stored in appProperties) */
    fun findBySha(parentId: String, sha: String): String? {
        val q = "'$parentId' in parents and appProperties has { key='sha256' and value='$sha' } and trashed=false"
        val j = JSONObject(get("$api/files?q=${enc(q)}&fields=files(id)&pageSize=1&supportsAllDrives=true&includeItemsFromAllDrives=true"))
        return j.optJSONArray("files")?.takeIf { it.length() > 0 }?.getJSONObject(0)?.getString("id")
    }

    /** Resumable upload (works for big PDFs). Returns the new file id. */
    fun uploadFile(parentId: String, name: String, file: File, mime: String, appProps: Map<String, String>): String {
        val meta = JSONObject().put("name", name).put("parents", JSONArray().put(parentId))
            .put("appProperties", JSONObject(appProps))
        val init = open("POST", "$upload/files?uploadType=resumable&supportsAllDrives=true&fields=id").apply {
            setRequestProperty("Content-Type", "$JSON_MIME; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", mime)
            setRequestProperty("X-Upload-Content-Length", file.length().toString())
            doOutput = true
            outputStream.use { it.write(meta.toString().toByteArray()) }
        }
        check(init)
        val session = init.getHeaderField("Location") ?: throw DriveException(500, "No upload session")
        init.disconnect()

        val put = (URL(session).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 30_000; readTimeout = 120_000
            setRequestProperty("Content-Type", mime)
            setFixedLengthStreamingMode(file.length())
            doOutput = true
            outputStream.use { out -> file.inputStream().use { it.copyTo(out, 256 * 1024) } }
        }
        val text = check(put)
        return JSONObject(text).getString("id")
    }

    /** Create or overwrite a small text file (CSV / JSON logs) by name inside a folder. */
    fun upsertText(parentId: String, name: String, content: ByteArray, mime: String): String {
        val existing = findChild(parentId, name, false)
        return if (existing != null) {
            send("PATCH", "$upload/files/${enc(existing)}?uploadType=media&supportsAllDrives=true&fields=id", content, mime)
            existing
        } else {
            val boundary = "docsnap" + System.nanoTime()
            val meta = JSONObject().put("name", name).put("parents", JSONArray().put(parentId)).toString()
            val body = ("--$boundary\r\nContent-Type: $JSON_MIME; charset=UTF-8\r\n\r\n$meta\r\n" +
                "--$boundary\r\nContent-Type: $mime\r\n\r\n").toByteArray() + content + "\r\n--$boundary--".toByteArray()
            JSONObject(send("POST", "$upload/files?uploadType=multipart&supportsAllDrives=true&fields=id", body,
                "multipart/related; boundary=$boundary")).getString("id")
        }
    }

    // ---------------------------------------------------------------- http

    private fun open(method: String, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
            connectTimeout = 30_000; readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun get(url: String): String = check(open("GET", url))

    private fun send(method: String, url: String, body: ByteArray, mime: String): String {
        val c = open(method, url).apply {
            setRequestProperty("Content-Type", mime)
            doOutput = true
            outputStream.use { it.write(body) }
        }
        return check(c)
    }

    private fun check(c: HttpURLConnection): String {
        val code = c.responseCode
        if (code in 200..299) return c.inputStream.bufferedReader().use { it.readText() }
        val err = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault(err.take(200))
        throw DriveException(code, msg.ifBlank { "HTTP $code" })
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val JSON_MIME = "application/json"
        const val SCOPE = "https://www.googleapis.com/auth/drive"

        /** Accepts a full Drive folder link, an ?id= link, or a bare folder id. */
        fun folderIdFromLink(text: String): String? {
            val t = text.trim()
            Regex("/folders/([A-Za-z0-9_-]{10,})").find(t)?.let { return it.groupValues[1] }
            Regex("[?&]id=([A-Za-z0-9_-]{10,})").find(t)?.let { return it.groupValues[1] }
            return t.takeIf { Regex("^[A-Za-z0-9_-]{10,}$").matches(it) }
        }
    }
}
