/*
 * SearchRepository: Film, kitap, müzik ve konum aramalarını yapan sınıf.
 *
 * Film, kitap ve müzik için bir servise (TvMaze, Open Library, iTunes) HTTP isteği atılır ve
 * gelen JSON SearchModels.kt'deki modellere çevrilir. Konum, telefonun Google konum servisiyle
 * (Android Geocoder) aranır. İstekler arka planda çalışır.
 * Hata olursa null döner; ekranda önceki sonuçlar kalır.
 * Kullanıcı yazarken bekleme (debounce) ve iptal işleri SearchViewModel'dedir.
 */
package com.example.logio.data.search

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.logio.R
import com.example.logio.util.AppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val MAX_LOCATION_RESULTS = 10

@Singleton
class SearchRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val httpClient = OkHttpClient()

    private fun encode(query: String): String = URLEncoder.encode(query.trim(), "UTF-8")

    suspend fun searchMovies(query: String): List<TvMazeShow>? =
        fetch(Request.Builder().url("https://api.tvmaze.com/search/shows?q=${encode(query)}").build()) { body ->
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i).getJSONObject("show")
                val imageObj = s.optJSONObject("image")
                TvMazeShow(
                    name = s.getString("name"),
                    summary = s.optString("summary", "").replace(Regex("<[^>]*>"), ""),
                    imageUrl = imageObj?.optString("medium"),
                    rating = s.optJSONObject("rating")?.optString("average", "N/A")
                )
            }
        }

    suspend fun searchBooks(query: String): List<BookInfo>? {
        val url = "https://openlibrary.org/search.json?q=${encode(query)}&limit=10&fields=title,author_name,cover_i,isbn,first_sentence,first_publish_year"
        val unknownAuthor = AppLocale.wrap(context).getString(R.string.entry_unknown_author)
        return fetch(Request.Builder().url(url).header("User-Agent", "LogioApp/1.0").build()) { body ->
            val docs = JSONObject(body).optJSONArray("docs") ?: return@fetch emptyList()
            (0 until docs.length()).mapNotNull { i ->
                val doc = docs.optJSONObject(i) ?: return@mapNotNull null
                val authors = doc.optJSONArray("author_name")?.let { arr -> List(arr.length()) { arr.getString(it) }.joinToString(", ") } ?: unknownAuthor
                val coverId = doc.optLong("cover_i", -1L)
                val coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null
                BookInfo(doc.optString("title", "Bilinmeyen Kitap"), authors, doc.optJSONArray("first_sentence")?.optString(0) ?: "", coverUrl)
            }
        }
    }

    suspend fun searchMusic(query: String): List<MusicTrack>? =
        fetch(Request.Builder().url("https://itunes.apple.com/search?term=${encode(query)}&entity=song&limit=10").build()) { body ->
            val items = JSONObject(body).getJSONArray("results")
            List(items.length()) { i ->
                val item = items.getJSONObject(i)
                val artwork = item.optString("artworkUrl100").replace("100x100bb", "600x600bb")
                MusicTrack(item.getString("trackName"), item.getString("artistName"), item.optString("collectionName"), artwork)
            }
        }

    /**
     * Yer adını telefonun Google konum servisiyle (Android Geocoder) arar.
     * Anahtar ya da ücret gerekmez; sonuçlar uygulamanın seçili dilinde gelir.
     */
    suspend fun searchLocations(query: String): List<LocationSearchResult>? {
        if (!Geocoder.isPresent()) return null
        val locale = AppLocale.wrap(context).resources.configuration.locales[0]
        val geocoder = Geocoder(context, locale)
        val addresses = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: sonuç beklenirken iş parçacığını kilitlemeyen yeni yöntem
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query.trim(), MAX_LOCATION_RESULTS, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                        override fun onError(errorMessage: String?) {
                            Timber.w("Konum araması başarısız: $errorMessage")
                            cont.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query.trim(), MAX_LOCATION_RESULTS)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Konum araması başarısız")
            null
        } ?: return null

        return addresses.map { address ->
            val fullAddress = (0..address.maxAddressLineIndex)
                .mapNotNull { address.getAddressLine(it) }
                .joinToString(", ")
                .ifBlank { query.trim() }
            // Kısa ad: yerin kendi adı (sadece kapı numarası değilse), yoksa semt / ilçe / il
            val shortName = address.featureName?.takeIf { name -> name.isNotBlank() && !name.all { it.isDigit() } }
                ?: address.subLocality ?: address.locality ?: address.subAdminArea ?: address.adminArea
                ?: fullAddress.substringBefore(",")
            LocationSearchResult(fullAddress, shortName, address.latitude, address.longitude)
        }
    }

    // Ortak istek akışı: IO'da çalıştır, başarılıysa gövdeyi [parse] ile çevir, hata olursa null
    private suspend fun <T> fetch(request: Request, parse: (String) -> T): T? = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).execute().use { res ->
                if (res.isSuccessful) parse(res.body?.string().orEmpty()) else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Arama isteği başarısız: ${request.url.host}")
            null
        }
    }
}
