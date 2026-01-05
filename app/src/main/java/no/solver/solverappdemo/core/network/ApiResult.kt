package no.solver.solverappdemo.core.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val exception: ApiException) : ApiResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): ApiException? = (this as? Error)?.exception

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (ApiException) -> Unit): ApiResult<T> {
        if (this is Error) action(exception)
        return this
    }

    companion object {
        inline fun <T> runCatching(block: () -> T): ApiResult<T> {
            return try {
                Success(block())
            } catch (e: ApiException) {
                Error(e)
            } catch (e: Exception) {
                Error(ApiException.Unknown(e.message ?: "Unknown error", e))
            }
        }
    }
}

sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class Network(override val message: String, override val cause: Throwable? = null) : ApiException(message, cause)
    data class Unauthorized(override val message: String = "Authentication required") : ApiException(message)
    data class Forbidden(override val message: String = "Access denied") : ApiException(message)
    data class NotFound(override val message: String = "Resource not found") : ApiException(message)
    data class Server(val code: Int, override val message: String) : ApiException(message)
    data class Unknown(override val message: String, override val cause: Throwable? = null) : ApiException(message, cause)

    companion object {
        fun fromHttpCode(code: Int, message: String? = null): ApiException = when (code) {
            401 -> Unauthorized(message ?: "Authentication required")
            403 -> Forbidden(message ?: "Access denied")
            404 -> NotFound(message ?: "Resource not found")
            in 500..599 -> Server(code, message ?: "Server error")
            else -> Unknown(message ?: "HTTP error $code")
        }
    }
}
