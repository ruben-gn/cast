package cast.android.network

import cast.android.domain.model.Settings
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseUrlInterceptor @Inject constructor() : Interceptor {

    @Volatile var baseUrl: String = Settings.DEFAULT_SERVER_URL

    override fun intercept(chain: Interceptor.Chain): Response {
        val normalized = if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            baseUrl
        } else {
            "http://$baseUrl"
        }
        val base = runCatching { normalized.toHttpUrl() }
            .getOrElse { Settings.DEFAULT_SERVER_URL.toHttpUrl() }
        val url = chain.request().url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}
