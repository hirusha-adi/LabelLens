package app.labellens.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Trades for a new [token] when the current one expires, which it does every hour. */
    @Volatile
    var refreshToken: String? = null

    /** Called with the new pair after a refresh, so they can be saved. */
    var onSessionRefreshed: ((token: String, refreshToken: String?) -> Unit)? = null

    private val refreshLock = Mutex()

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

    suspend fun request(method: String, path: String, body: String?, ifNoneMatch: String?): String? {
        val sentWith = token
        try {
            return send(method, path, body, ifNoneMatch)
        } catch (e: ApiException) {
            // An expired token is renewed once, quietly, and the request sent again.
            if (e.status != 401 || sentWith == null || path.startsWith("/auth/") || !refreshSession(sentWith)) throw e
            return send(method, path, body, ifNoneMatch)
        }
    }

    private suspend fun refreshSession(expired: String): Boolean = refreshLock.withLock {
        // Another request may have refreshed while this one waited.
        if (token != null && token != expired) return@withLock true
        val refresh = refreshToken ?: return@withLock false
        try {
            val text = send("POST", "/auth/refresh", AppJson.encodeToString(RefreshBody(refresh)), null) ?: return@withLock false
            val fresh = AppJson.decodeFromString<AuthResponse>(text)
            val newToken = fresh.token ?: return@withLock false
            token = newToken
            refreshToken = fresh.refreshToken
            onSessionRefreshed?.invoke(newToken, fresh.refreshToken)
            true
        } catch (e: ApiException) {
            false
        }
    }

    private suspend fun send(method: String, path: String, body: String?, ifNoneMatch: String?): String? = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
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
