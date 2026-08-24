package be.agroprices.widget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Fetches the small beef.json summary published by the backend/ scraper via GitHub Pages. */
class BeefClient(private val url: String) {

    suspend fun downloadJson(): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Beef-data endpoint gaf HTTP ${connection.responseCode} terug")
            }

            connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}
