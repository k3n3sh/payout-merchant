package co.kp.merchantpayout.mock


import okhttp3.mockwebserver.MockWebServer

object MockServerManager {

    private val server = MockWebServer()

    @Volatile
    private var cachedBaseUrl: String = ""

    val baseUrl: String get() = cachedBaseUrl

    fun start() {
        server.dispatcher = MockDispatcher()
        server.start()
        cachedBaseUrl = server.url("/").toString()
    }

    fun shutdown() = runCatching { server.shutdown() }
}