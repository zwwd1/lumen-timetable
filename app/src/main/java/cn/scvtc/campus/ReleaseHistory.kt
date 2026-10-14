package cn.scvtc.campus

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseHistoryEntry(val version: String, val date: String, val body: String)

/** Local history opens immediately. GitHub supplements it at most once per day. */
object ReleaseHistory {
    private val versionPattern = Regex("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}")
    private fun parse(text: String, github: Boolean): List<ReleaseHistoryEntry> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            if (github && (item.optBoolean("draft") || item.optBoolean("prerelease"))) return@mapNotNull null
            val version = if (github) item.optString("tag_name").removePrefix("v") else item.optString("version")
            val body = item.optString("body").trim().take(16000)
            if (!versionPattern.matches(version) || body.isBlank()) null else ReleaseHistoryEntry(
                version, if (github) item.optString("published_at").take(10) else item.optString("date"), body)
        }
    }

    fun local(context: Context): List<ReleaseHistoryEntry> =
        runCatching { context.assets.open("release-history.json").bufferedReader().use { parse(it.readText(), false) } }
            .getOrDefault(emptyList())

    private fun merge(local: List<ReleaseHistoryEntry>, remote: List<ReleaseHistoryEntry>) =
        (remote + local).distinctBy { it.version }.sortedWith(compareByDescending<ReleaseHistoryEntry> {
            val v = it.version.split('.').map(String::toLong)
            v[0] * 100000000L + v[1] * 10000L + v[2]
        })

    suspend fun cached(context: Context): List<ReleaseHistoryEntry> = withContext(Dispatchers.IO) {
        val saved = context.getSharedPreferences("release_history", Context.MODE_PRIVATE).getString("github", null)
        merge(local(context), saved?.let { runCatching { parse(it, true) }.getOrDefault(emptyList()) }.orEmpty())
    }

    suspend fun refresh(context: Context, repository: String): List<ReleaseHistoryEntry> = withContext(Dispatchers.IO) {
        require(repository in setOf("gnomon-timetable", "lumen-timetable"))
        val prefs = context.getSharedPreferences("release_history", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val elapsed = now - prefs.getLong("attempt", 0)
        if (elapsed in 0..86400000L) return@withContext cached(context)
        prefs.edit().putLong("attempt", now).apply()
        val connection = URL("https://api.github.com/repos/zwwd1/$repository/releases?per_page=40")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "SCVTC-Timetable")
            if (connection.responseCode == 200) {
                val bytes = connection.inputStream.use { it.readBytesLimited() }
                val text = bytes.toString(Charsets.UTF_8)
                val entries = parse(text, true)
                if (entries.isNotEmpty()) prefs.edit().putString("github", text).apply()
            }
        } catch (_: java.io.IOException) {
            // Offline/rate limited: retain both bundled history and the last successful response.
        } catch (_: org.json.JSONException) {
            // A changed response never replaces readable history.
        } finally {
            connection.disconnect()
        }
        cached(context)
    }

    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > 1024 * 1024) throw java.io.IOException("Release history is too large")
            output.write(buffer, 0, count)
        }
    }

    fun plainNotes(body: String): String = body.lineSequence()
        .filterNot { it.startsWith("SHA-256") || it.startsWith("安装包：") }
        .joinToString("\n") { it.trimStart('#', ' ').replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").replace("**", "").replace("`", "") }
        .trim()
}

@Composable
fun rememberReleaseHistory(repository: String): List<ReleaseHistoryEntry> {
    val context = LocalContext.current.applicationContext
    var entries by remember(repository) { mutableStateOf(ReleaseHistory.local(context)) }
    LaunchedEffect(repository) {
        entries = ReleaseHistory.cached(context)
        entries = ReleaseHistory.refresh(context, repository)
    }
    return entries
}
