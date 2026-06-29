package com.streamflow.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class OkHttpDownloader private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
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

        val okResponse = client.newCall(builder.build()).execute()
        val responseBody = okResponse.body?.string()

        return Response(
            okResponse.code,
            okResponse.message,
            okResponse.headers.toMultimap(),
            responseBody,
            okResponse.request.url.toString()
        )
    }

    companion object {
        val instance: OkHttpDownloader by lazy { OkHttpDownloader() }
    }
}
