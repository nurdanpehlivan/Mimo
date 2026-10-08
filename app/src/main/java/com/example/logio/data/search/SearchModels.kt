/*
 * SearchModels: İnternetteki arama servislerinden dönen sonuçların veri modelleri.
 *
 * Bunlar veritabanı tablosu değildir; yalnızca arama penceresinde listelenir.
 * Kullanıcı bir sonucu seçince TimelineViewModel onu bir günlük kaydına (JournalEntryEntity) çevirir.
 */
package com.example.logio.data.search

/** TvMaze dizi/film sonucu. */
data class TvMazeShow(val name: String, val summary: String, val imageUrl: String?, val rating: String?)

/** Open Library kitap sonucu. */
data class BookInfo(val title: String, val authors: String, val description: String, val imageUrl: String?)

/** iTunes şarkı sonucu. */
data class MusicTrack(val trackName: String, val artistName: String, val collectionName: String?, val imageUrl: String?)

/** Android Geocoder (Google konum servisi) yer sonucu. */
data class LocationSearchResult(val displayName: String, val shortName: String, val latitude: Double, val longitude: Double)
