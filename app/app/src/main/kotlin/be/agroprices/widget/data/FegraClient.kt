package be.agroprices.widget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * fegra.be only publishes on business days (~9:30), so a short date range
 * can span a long weekend/holiday stretch with zero rows. 21 days
 * comfortably covers that while keeping the request small.
 */
private const val LOOKBACK_DAYS = 21L
private const val ENDPOINT = "https://fegra.be/Home/DownloadPrices"

class FegraClient {

    suspend fun downloadCsv(): String = withContext(Dispatchers.IO) {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val today = LocalDate.now()
        val from = today.minusDays(LOOKBACK_DAYS)
        val body = "DateFrom=${from.format(formatter)}&DateTo=${today.format(formatter)}"

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.US_ASCII)) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("fegra.be gaf HTTP ${connection.responseCode} terug")
            }

            // Response Content-Type is text/csv, but the body is actually
            // UTF-16LE-encoded -- decoding as UTF-8 here would corrupt the
            // MAÏS diaeresis and silently misalign columns.
            val bytes = connection.inputStream.use { it.readBytes() }
            String(bytes, Charsets.UTF_16LE)
        } finally {
            connection.disconnect()
        }
    }
}
