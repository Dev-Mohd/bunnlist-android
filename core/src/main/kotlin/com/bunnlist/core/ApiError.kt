package com.bunnlist.core

/** The API's HTTP outcomes, mapped the same way as the iOS client. */
sealed class ApiError(message: String) : Exception(message) {
    data object Unauthorized : ApiError("unauthorized")
    data object Forbidden : ApiError("forbidden")
    data object NotFound : ApiError("not_found")
    data object Conflict : ApiError("conflict")
    data class Unprocessable(val reason: String?) : ApiError("unprocessable")
    data object RateLimited : ApiError("rate_limited")
    data class Server(val status: Int) : ApiError("server_$status")
    data object Malformed : ApiError("malformed_response")
    data class Network(val causeMessage: String?) : ApiError("network")

    companion object {
        fun from(status: Int, reason: String?): ApiError = when (status) {
            401 -> Unauthorized
            403 -> Forbidden
            404 -> NotFound
            409 -> Conflict
            422 -> Unprocessable(reason)
            429 -> RateLimited
            else -> Server(status)
        }
    }
}
