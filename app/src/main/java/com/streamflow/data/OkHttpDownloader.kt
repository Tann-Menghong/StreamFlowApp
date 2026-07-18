package com.streamflow.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class OkHttpDownloader private constructor() : Downloader() {

    // Shared app-wide client: warm connection pool + high per-host parallelism.
    // NewPipe extraction fires several requests at the same hosts per video, so
    // connection reuse is the biggest lever on video load time.
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
        .followRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())

        request.headers()?.forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        when (request.httpMethod()) {
            "POST" -> {
                val contentType = request.headers()
                    ?.entries?.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                    ?.value?.firstOrNull()
                    ?: "application/json"
                builder.post(
                    (request.dataToSend() ?: ByteArray(0))
                        .toRequestBody(contentType.toMediaType())
                )
            }
            else -> builder.get()
        }

        // .use{} — body?.string() closes the body on success, but a null-body
        // response used to leak the connection out of the shared pool
        return client.newCall(builder.build()).execute().use { okResponse ->
            Response(
                okResponse.code,
                okResponse.message,
                okResponse.headers.toMultimap(),
                okResponse.body?.string(),
                okResponse.request.url.toString()
            )
        }
    }

    companion object {
        val instance: OkHttpDownloader by lazy { OkHttpDownloader() }
    }
}
