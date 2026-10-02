package dev.smoreg.raa.data

import dev.smoreg.raa.wake.USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Station(val name: String, val url: String, val country: String, val codec: String, val bitrate: Int)

/** Search in the open radio-browser.info directory. Only the typed name leaves the phone. */
object Stations {
    private const val API = "https://all.api.radio-browser.info/json/stations/search"
    private const val TIMEOUT_MS = 10_000

    suspend fun search(name: String): List<Station> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val conn = URL("$API?name=$q&hidebroken=true&order=clickcount&reverse=true&limit=30").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val json = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            (0 until json.length()).map { json.getJSONObject(it) }.mapNotNull { o ->
                // url_resolved is the stream itself; url may be a playlist file.
                val url = o.optString("url_resolved").ifBlank { o.optString("url") }
                if (url.isBlank()) null
                else Station(o.optString("name").trim(), url, o.optString("countrycode"), o.optString("codec"), o.optInt("bitrate"))
            }.distinctBy { it.url }
        } finally {
            conn.disconnect()
        }
    }
}
