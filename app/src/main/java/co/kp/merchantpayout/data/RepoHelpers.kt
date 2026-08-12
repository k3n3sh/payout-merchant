package co.kp.merchantpayout.data

import co.kp.merchantpayout.domain.DomainError
import co.kp.merchantpayout.domain.Outcome
import retrofit2.HttpException
import java.io.IOException

// run api call and turn exception into DomainError so UI dont see okhttp/retrofit types.
// I originally caught Throwable at the top level but IOException + HttpException covers
// what actually comes out of Retrofit's suspend calls. leaving the Throwable branch as a
// safety net for now.
internal suspend fun <T> runApi(block: suspend () -> T): Outcome<T> {
    try {
        val result = block()
        return Outcome.Ok(result)
    } catch (e: HttpException) {
        val code = e.code()
        val serverCode = readServerCode(e)
        return Outcome.Err(mapHttp(code, serverCode))
    } catch (e: IOException) {
        return Outcome.Err(DomainError.Network)
    } catch (t: Throwable) {
        return Outcome.Err(DomainError.Unknown(t))
    }
}

private fun mapHttp(code: Int, serverCode: String?): DomainError {
    if (serverCode == "INSUFFICIENT_FUNDS")
        return DomainError.InsufficientFunds

    if (serverCode == "SERVICE_UNAVAILABLE" || code == 503)
        return DomainError.ServiceUnavailable

    return DomainError.Http(code)
}

// server error body look like {"error":"...","code":"..."} — pull out the code string.
private fun readServerCode(e: HttpException): String? {
    try {
        val body = e.response()?.errorBody()?.string()
        if (body == null)
            return null

        val match = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"").find(body)
        if (match == null)
            return null

        return match.groupValues[1]
    } catch (t: Throwable) {
        return null
    }
}