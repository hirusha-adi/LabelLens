package com.hirusha.labellens.data

import android.os.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the additive catalogue from the Label Lens server.
 *
 * Each request tells the server one thing about the phone, its make and model, in
 * X-Device-Type. The server keeps that, the address the request came from and what
 * was asked for. Nothing the person has set up or scanned is ever sent.
 */
class Api(private val baseUrl: String) {
    private val deviceType = "${Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }} ${Build.MODEL.orEmpty()}".trim().ifEmpty { "Android" }

    /** A response body with the validator the server sent for it. */
    private class Answer(val text: String, val etag: String?)

    /**
     * The whole list with its ETag, or null when the copy the phone holds, identified
     * by [etag], is still current.
     */
    suspend fun catalogue(etag: String?): Pair<Catalogue, String?>? =
        get("/additives", ifNoneMatch = etag)?.let { AppJson.decodeFromString<Catalogue>(it.text) to it.etag }

    /** One additive's page, or null if the server does not have it. */
    suspend fun detail(code: String): AdditiveDetail? = get("/additives/$code")?.let { AppJson.decodeFromString<AdditiveDetail>(it.text) }

    /**
     * Null for "not modified" and "not found". Anything else that is not a success is
     * an [IOException]. The platform asks for gzip and unpacks it by itself.
     */
    private suspend fun get(path: String, ifNoneMatch: String? = null): Answer? = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Device-Type", deviceType)
            if (!ifNoneMatch.isNullOrEmpty()) connection.setRequestProperty("If-None-Match", ifNoneMatch)
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED, HttpURLConnection.HTTP_NOT_FOUND -> null
                in 200..299 -> Answer(connection.inputStream.bufferedReader().use { it.readText() }, connection.getHeaderField("ETag"))
                else -> throw IOException("Server answered $status")
            }
        } finally {
            connection.disconnect()
        }
    }
}
