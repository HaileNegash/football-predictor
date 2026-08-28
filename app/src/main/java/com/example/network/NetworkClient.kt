package com.example.network

import android.os.Build
import android.util.Log
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * SocketFactory that explicitly enables TLS 1.2 on devices where it's supported
 * but not enabled by default (API 16-21).
 */
class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
    companion object {
        private val TLS_V12_ONLY = arrayOf("TLSv1.2")
    }

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        return patch(delegate.createSocket(s, host, port, autoClose))
    }

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(host: String, port: Int): Socket {
        return patch(delegate.createSocket(host, port))
    }

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        return patch(delegate.createSocket(host, port, localHost, localPort))
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket {
        return patch(delegate.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        return patch(delegate.createSocket(address, port, localAddress, localPort))
    }

    private fun patch(s: Socket): Socket {
        if (s is SSLSocket) {
            val supported = s.supportedProtocols ?: emptyArray()
            val enabled = if (supported.contains("TLSv1.2")) {
                if (supported.contains("TLSv1.3")) arrayOf("TLSv1.3", "TLSv1.2") else arrayOf("TLSv1.2")
            } else {
                supported
            }
            s.enabledProtocols = enabled
        }
        return s
    }
}

object NetworkClient {
    private const val BASE_URL = "https://v3.football.api-sports.io/"

    val okHttpClient: OkHttpClient by lazy {
        createOkHttpClient()
    }

    val apiFootballService: ApiFootballService by lazy {
        createRetrofit(BASE_URL).create(ApiFootballService::class.java)
    }

    val moshi: com.squareup.moshi.Moshi by lazy {
        com.squareup.moshi.Moshi.Builder().build()
    }

    fun createRetrofit(baseUrl: String): Retrofit {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        
        // User-Agent and headers interceptor
        builder.addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "FootballPredictor/2.4 (Android; en-US)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }

        // Timeouts
        builder.connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        builder.readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        builder.writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)

        // Logging for debug
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        builder.addInterceptor(logging)

        // Enable TLS 1.2 for API 21 and below
        if (Build.VERSION.SDK_INT in 16..21) {
            try {
                val sc = SSLContext.getInstance("TLSv1.2")
                sc.init(null, null, null)
                
                val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trustManagerFactory.init(null as KeyStore?)
                val trustManagers = trustManagerFactory.trustManagers
                check(!(trustManagers.size != 1 || trustManagers[0] !is X509TrustManager)) {
                    "Unexpected default trust managers:" + trustManagers.contentToString()
                }
                val trustManager = trustManagers[0] as X509TrustManager
                
                builder.sslSocketFactory(Tls12SocketFactory(sc.socketFactory), trustManager)

                val cs = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build()
                
                val specs = listOf(cs, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)
                builder.connectionSpecs(specs)
            } catch (e: Exception) {
                Log.e("NetworkClient", "Error while setting TLS 1.2", e)
            }
        }

        return builder.build()
    }
}
