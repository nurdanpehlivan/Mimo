package com.example.logio.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class LinkPreview(val title: String?, val imageUrl: String?)

/**
 * Bir web sayfasının paylaşım önizlemesini (Open Graph / Twitter kartı) okur.
 * Yalnızca sayfanın başı indirilir; meta etiketleri <head> içinde olduğu için yeterlidir.
 */
@Singleton
class LinkPreviewFetcher @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetch(rawUrl: String): LinkPreview? = withContext(Dispatchers.IO) {
        val url = (if (rawUrl.startsWith("http")) rawUrl else "https://$rawUrl").toHttpUrlOrNull()
            ?: return@withContext null
        // YouTube/Instagram önizleme etiketlerini yalnızca bilinen önizleme botuna verir;
        // bazı siteler ise botları engeller, onlar için tarayıcı kimliğiyle bir kez daha denenir
        var fallback: LinkPreview? = null
        for (userAgent in USER_AGENTS) {
            val preview = fetchWith(url, userAgent, rawUrl) ?: continue
            if (preview.imageUrl != null) return@withContext preview
            if (fallback == null) fallback = preview
        }
        fallback
    }

    private fun fetchWith(url: okhttp3.HttpUrl, userAgent: String, rawUrl: String): LinkPreview? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                // Görsel linki doğrudan paylaşıldıysa kendisi önizlemedir
                if (body.contentType()?.type == "image") {
                    return LinkPreview(title = null, imageUrl = response.request.url.toString())
                }
                val head = body.source().let { source ->
                    source.request(MAX_BYTES)
                    source.buffer.snapshot().utf8()
                }
                val finalUrl = response.request.url
                val image = metaContent(head, "og:image:secure_url", "og:image", "twitter:image", "twitter:image:src")
                    ?.let { finalUrl.resolve(it.replace("&amp;", "&"))?.toString() }
                val title = metaContent(head, "og:title", "twitter:title")
                    ?: TitleTagRegex.find(head)?.groupValues?.get(1)
                return LinkPreview(title = title?.let(::decodeEntities)?.trim()?.takeIf { it.isNotBlank() }, imageUrl = image)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Link önizlemesi alınamadı: $rawUrl")
            return null
        }
    }

    private fun metaContent(html: String, vararg keys: String): String? {
        for (key in keys) {
            val escaped = Regex.escape(key)
            // property/name önce ya da content önce gelebilir
            val patterns = listOf(
                Regex("""<meta[^>]+(?:property|name)\s*=\s*["']$escaped["'][^>]*content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]*(?:property|name)\s*=\s*["']$escaped["']""", RegexOption.IGNORE_CASE)
            )
            patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }?.let { return it }
        }
        return null
    }

    private fun decodeEntities(text: String): String = text
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private companion object {
        const val MAX_BYTES = 256L * 1024
        val USER_AGENTS = listOf(
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        )
        val TitleTagRegex = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
    }
}
