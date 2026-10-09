package app.labellens.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The server answered, but with an error status. */
class ApiException(val status: Int, val code: String, message: String) : Exception(message)

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Minimal JSON client for the API in /api. Network failures surface as [IOException]. */
class Api(private val baseUrl: String) {
    @Volatile
    var token: String? = null

    suspend inline fun <reified T> get(path: String): T = AppJson.decodeFromString(request("GET", path, null))

    suspend inline fun <reified T> delete(path: String): T = AppJson.decodeFromString(request("DELETE", path, null))

    suspend inline fun <reified B, reified T> post(path: String, body: B): T =
        AppJson.decodeFromString(request("POST", path, AppJson.encodeToString(body)))

    suspend inline fun <reified B, reified T> put(path: String, body: B): T =
        AppJson.decodeFromString(request("PUT", path, AppJson.encodeToString(body)))

    suspend inline fun <reified T> post(path: String): T = AppJson.decodeFromString(request("POST", path, "{}"))

    suspend inline fun <reified T> put(path: String): T = AppJson.decodeFromString(request("PUT", path, "{}"))

    /** GET that returns null when the server says the copy tagged [version] is still current. */
    suspend inline fun <reified T> getIfChanged(path: String, version: String): T? =
        request("GET", path, null, ifNoneMatch = version.takeIf { it.isNotEmpty() })?.let { AppJson.decodeFromString<T>(it) }

    suspend fun request(method: String, path: String, body: String?): String =
        request(method, path, body, ifNoneMatch = null) ?: ""

    suspend fun request(method: String, path: String, body: String?, ifNoneMatch: String?): String? = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 4_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            ifNoneMatch?.let { connection.setRequestProperty("If-None-Match", "\"$it\"") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) return@withContext null
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = runCatching { AppJson.decodeFromString<ApiError>(text) }.getOrDefault(ApiError())
                throw ApiException(status, error.error, error.message.ifBlank { "Something went wrong." })
            }
            text
        } finally {
            connection.disconnect()
        }
    }
}
