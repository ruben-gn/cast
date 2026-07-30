package cast.android.network

import cast.android.domain.model.Settings
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Accepts a bare host ("192.168.1.2:8100") as well as a full URL. Null if it is neither. */
internal fun normalizeBaseUrl(raw: String): HttpUrl? {
    if (raw.isBlank()) return null
    val normalized = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
    return normalized.toHttpUrlOrNull()
}

@Singleton
class BaseUrlInterceptor @Inject constructor() : Interceptor {

    @Volatile var baseUrl: String = Settings.DEFAULT_SERVER_URL

    override fun intercept(chain: Interceptor.Chain): Response {
        // An IOException (rather than the IllegalArgumentException a bad URL would raise) so an
        // unconfigured server surfaces as an ordinary call failure and shows the offline state,
        // instead of crashing the caller.
        val base = normalizeBaseUrl(baseUrl)
            ?: normalizeBaseUrl(Settings.DEFAULT_SERVER_URL)
            ?: throw IOException("No server URL configured")
        val url = chain.request().url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}
