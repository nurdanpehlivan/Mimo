/*
 * SearchViewModel: Film, kitap, müzik ve konum arama pencerelerinin ViewModel'i.
 *
 * Kullanıcı yazarken her harfte istek atılmaz: önceki arama iptal edilir, 500 ms beklenir
 * (debounce), sonra SearchRepository'den sonuç istenir. Sonuçlar ekrana StateFlow ile verilir.
 * Seçilen sonucu günlüğe kaydetmek TimelineViewModel'in işidir (saveFilmEntry vb.).
 */
package com.example.logio.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.logio.data.search.BookInfo
import com.example.logio.data.search.LocationSearchResult
import com.example.logio.data.search.MusicTrack
import com.example.logio.data.search.SearchRepository
import com.example.logio.data.search.TvMazeShow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 500L

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _movieSearchResults = MutableStateFlow<List<TvMazeShow>>(emptyList())
    val movieSearchResults: StateFlow<List<TvMazeShow>> = _movieSearchResults.asStateFlow()

    private val _bookSearchResults = MutableStateFlow<List<BookInfo>>(emptyList())
    val bookSearchResults: StateFlow<List<BookInfo>> = _bookSearchResults.asStateFlow()

    private val _musicSearchResults = MutableStateFlow<List<MusicTrack>>(emptyList())
    val musicSearchResults: StateFlow<List<MusicTrack>> = _musicSearchResults.asStateFlow()

    private val _locationSearchResults = MutableStateFlow<List<LocationSearchResult>>(emptyList())
    val locationSearchResults: StateFlow<List<LocationSearchResult>> = _locationSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    fun searchMovies(query: String) = search(query, _movieSearchResults) { searchRepository.searchMovies(it) }

    fun searchBooks(query: String) = search(query, _bookSearchResults) { searchRepository.searchBooks(it) }

    fun searchMusic(query: String) = search(query, _musicSearchResults) { searchRepository.searchMusic(it) }

    // Konumda tek harfle arama yapılmaz (çok fazla anlamsız sonuç döner)
    fun searchLocations(query: String) = search(query, _locationSearchResults, minLength = 2) { searchRepository.searchLocations(it) }

    /**
     * Dört aramanın ortak akışı: önceki aramayı iptal et, kısa sorguda listeyi boşalt,
     * bekle, sonucu getir. İstek başarısızsa (null) önceki sonuçlar ekranda kalır.
     */
    private fun <T> search(
        query: String,
        results: MutableStateFlow<List<T>>,
        minLength: Int = 1,
        fetch: suspend (String) -> List<T>?
    ) {
        searchJob?.cancel()
        if (query.isBlank() || query.trim().length < minLength) {
            results.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        searchJob = viewModelScope.launch {
            try {
                delay(SEARCH_DEBOUNCE_MS)
                fetch(query)?.let { results.value = it }
            } finally {
                // İptal edilen eski arama, yerine başlayan yeni aramanın yükleniyor durumunu kapatmasın
                if (searchJob === coroutineContext.job) _isSearching.value = false
            }
        }
    }
}
