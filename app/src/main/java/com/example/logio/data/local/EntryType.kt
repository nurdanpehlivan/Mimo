package com.example.logio.data.local

enum class EntryType {
    TEXT,       // Klasik günlük yazısı
    MOOD,       // Ruh hali kaydı
    MEAL,       // Gemini AI ile analiz edilen yemek/kalori girdisi
    EXPENSE,    // Günlük harcama/bütçe kaydı
    WATER,      // Su içme kaydı
    LOCATION,   // Konum kaydı
    VOICE,      // Ses kaydı
    PHOTO       // Fotoğraflı anı
}