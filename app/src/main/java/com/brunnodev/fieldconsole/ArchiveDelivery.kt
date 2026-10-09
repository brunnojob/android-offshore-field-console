package com.brunnodev.fieldconsole

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ArchiveDelivery(private val token: String, endpoint: String = "https://vercel-home-telemetry-api.vercel.app/api/runs") {
    private val destination = URL(endpoint).also { require(it.protocol == "https" && it.userInfo == null) }
    suspend fun send(key: String, kind: String, payload: String): Boolean = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && token.length <= 8192 && !token.contains('\n'))
        val body = JSONObject().put("project", "android-offshore-field-console").put("kind", kind).put("clientKey", key).put("result", JSONObject(payload)).toString().toByteArray(Charsets.UTF_8)
        val connection = (destination.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
            setFixedLengthStreamingMode(body.size)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) return@withContext false
            val response = connection.inputStream.use { val bytes = ByteArray(4096)
                var offset = 0
                while (offset < bytes.size) { val count = it.read(bytes, offset, bytes.size - offset); if (count < 0) break; offset += count }
                bytes.copyOf(offset).toString(Charsets.UTF_8) }
            JSONObject(response).optBoolean("persisted", false)
        } finally { connection.disconnect() }
    }
}
