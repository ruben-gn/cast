package cast.android.network

import retrofit2.HttpException
import retrofit2.Response

/**
 * Mirrors the failure semantics of Retrofit's body-returning calls, which throw [HttpException] on a
 * non-2xx response. Endpoints declared as `Response<Unit>` instead return normally on failure, so
 * callers must opt in to the same behavior to avoid silently treating a server error as success.
 */
internal fun Response<Unit>.orThrow() {
    if (!isSuccessful) throw HttpException(this)
}
