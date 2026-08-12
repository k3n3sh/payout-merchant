package co.kp.merchantpayout.mock

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject

internal class MockDispatcher : Dispatcher() {

    override fun dispatch(request: RecordedRequest): MockResponse {
        // *Mock Changes: tightened latency 500..2000 → 400..1200 so pagination to give feel of loading and simulating latency .
        Thread.sleep((400L..1200L).random())
        val path = request.path ?: return notFound()
        val cleanPath = path.substringBefore("?")
        val query = path.substringAfter("?", "")
        return when {
            request.method == "GET" && cleanPath == "/api/merchant" -> getMerchant()
            request.method == "GET" && cleanPath == "/api/merchant/activity" -> getActivity(query)
            request.method == "POST" && cleanPath == "/api/payouts" -> createPayout(request)
            request.method == "GET" && cleanPath == "/api/devices" -> getDevice()

            // *Mock Changes: Commented out — Polling not asked in challenge, avoiding over engineering, but deffo require in real app
            request.method == "GET" && cleanPath.startsWith("/api/payouts/") -> {
                val id = cleanPath.removePrefix("/api/payouts/")
                MockData.getPayoutById(id)?.let { jsonResponse(it.toJson()) } ?: notFound()
            }

            else -> notFound()
        }
    }

    private fun getMerchant(): MockResponse {
        val recent = MockData.activities.take(3).joinToString(",") { it.toJson() }
        return jsonResponse(
            // *Mock Changes: read live balance getters (availableBalance / pendingBalance) instead of
            // const AVAILABLE_BALANCE / PENDING_BALANCE so Home reflects money movement after payouts.
            """
            {
              "available_balance": ${MockData.availableBalance},
              "pending_balance": ${MockData.pendingBalance},
              "currency": "GBP",
              "activity": [$recent]
            }
            """.trimIndent()
        )
    }

    private fun getActivity(query: String): MockResponse {
        // *Mock Changes: inlined parseQuery helper — one call site didn't earn its own function.
        val params = query.split("&").mapNotNull {
            it.split("=", limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] }
        }.toMap()
        val cursor = params["cursor"]
        val limit = params["limit"]?.toIntOrNull() ?: 15
        val result = MockData.getPage(cursor, limit)
        val items = result.items.joinToString(",") { it.toJson() }
        val nextCursor = result.nextCursor?.let { "\"$it\"" } ?: "null"
        return jsonResponse(
            """{ "items": [$items], "next_cursor": $nextCursor, "has_more": ${result.hasMore} }""".trimIndent()
        )
    }

    private fun createPayout(request: RecordedRequest): MockResponse {
        val body = JSONObject(request.body.readUtf8())
        return when (val amount = body.getInt("amount")) {
            99999 -> MockResponse().setResponseCode(503)
                .setBody("""{"error":"Service unavailable","code":"SERVICE_UNAVAILABLE"}""")
                .addHeader("Content-Type", "application/json")

            88888 -> MockResponse().setResponseCode(400)
                .setBody("""{"error":"Insufficient funds","code":"INSUFFICIENT_FUNDS"}""")
                .addHeader("Content-Type", "application/json")

            else -> {
                val currency = body.getString("currency")
                val iban = body.getString("iban")
                val ref = body.getString("ref")
                jsonResponse(MockData.createPayout(amount, currency, iban, ref).toJson())
            }
        }
    }

    private fun getDevice(): MockResponse =
        jsonResponse("""{"device_id":"${MockData.deviceId}"}""")

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setBody(body)
        .addHeader("Content-Type", "application/json")

    private fun notFound() = MockResponse().setResponseCode(404)

    // *Mock Changes: JSON extensions compacted to one-line templates — the payload isn't hand-read.
    private fun MockData.ActivityRecord.toJson() =
        """{"id":"$id","type":"$type","amount":$amount,"currency":"GBP","date":"$date","description":"$description","status":"$status"}"""

    private fun MockData.PayoutRecord.toJson() =
        """{"id":"$id","status":"$status","amount":$amount,"currency":"$currency","iban":"$iban","ref":"$ref","created_at":"${
            java.time.Instant.ofEpochMilli(
                createdAt
            )
        }"}"""
}