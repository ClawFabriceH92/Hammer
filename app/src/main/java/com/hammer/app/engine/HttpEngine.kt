package com.hammer.app.engine

import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object HttpEngine {

    fun buildClient(config: RunConfig): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = (config.concurrency * 2).coerceAtLeast(config.concurrency)
            maxRequestsPerHost = config.concurrency
        }
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)

        // Addendum 16.2: certificate bypass is only ever wired up for RFC1918 targets, never websites.
        if (config.ignoreTlsErrors && config.target is TargetConfig.LocalIp) {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustAll)
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    fun buildUrl(target: TargetConfig): String = when (target) {
        is TargetConfig.LocalIp ->
            "${scheme(target.useTls)}://${target.ip}:${target.port}${normalizePath(target.path)}"
        is TargetConfig.Website ->
            "${scheme(target.useTls)}://${target.host}:${target.port}${normalizePath(target.path)}"
    }

    private fun scheme(useTls: Boolean) = if (useTls) "https" else "http"

    private fun normalizePath(path: String): String = if (path.startsWith("/")) path else "/$path"

    fun execute(client: OkHttpClient, config: RunConfig): RequestOutcome {
        val requestBuilder = Request.Builder().url(buildUrl(config.target))
        config.customHeaders.forEach { (name, value) -> requestBuilder.addHeader(name, value) }

        when (config.protocol) {
            Protocol.HTTP_GET -> requestBuilder.get()
            Protocol.HTTP_POST -> {
                val body = (config.postBody ?: ByteArray(0)).toRequestBody(config.postContentType.toMediaTypeOrNull())
                requestBuilder.post(body)
            }
            Protocol.TCP_RAW -> error("HttpEngine cannot execute TCP_RAW requests")
        }

        val startNanos = System.nanoTime()
        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val category = when {
                    response.isSuccessful -> ErrorCategory.NONE
                    response.code in 400..499 -> ErrorCategory.HTTP_4XX
                    response.code in 500..599 -> ErrorCategory.HTTP_5XX
                    else -> ErrorCategory.OTHER
                }
                RequestOutcome(
                    success = response.isSuccessful,
                    latencyMillis = elapsedMillis(startNanos),
                    errorCategory = category,
                    httpStatusCode = response.code
                )
            }
        } catch (e: SocketTimeoutException) {
            RequestOutcome(false, elapsedMillis(startNanos), ErrorCategory.TIMEOUT)
        } catch (e: SSLException) {
            RequestOutcome(false, elapsedMillis(startNanos), ErrorCategory.SSL_ERROR)
        } catch (e: ConnectException) {
            RequestOutcome(false, elapsedMillis(startNanos), ErrorCategory.CONNECTION_REFUSED)
        } catch (e: java.io.IOException) {
            val category = if (e.message?.contains("reset", ignoreCase = true) == true) {
                ErrorCategory.RESET
            } else {
                ErrorCategory.OTHER
            }
            RequestOutcome(false, elapsedMillis(startNanos), category)
        }
    }

    private fun elapsedMillis(startNanos: Long) = (System.nanoTime() - startNanos) / 1_000_000
}
