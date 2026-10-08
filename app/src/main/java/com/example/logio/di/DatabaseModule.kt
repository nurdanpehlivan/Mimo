package com.example.logio.di

import android.content.Context
import androidx.room.Room
import com.example.logio.data.local.DatabaseSecurity
import com.example.logio.data.local.JournalDao
import com.example.logio.data.local.LogioDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DB_NAME = "logio_database.db"

    @Provides
    @Singleton
    fun provideLogioDatabase(
        @ApplicationContext context: Context
    ): LogioDatabase {
        System.loadLibrary("sqlcipher")

        val passphrase = DatabaseSecurity.passphrase(context)
            ?: run {
                DatabaseSecurity.resetUnrecoverable(context, DB_NAME)
                DatabaseSecurity.passphrase(context)
            }

        // Eski şifresiz veritabanı varsa kayıtlar korunarak şifrelenir; başarısız olursa şifresiz açılır
        val encrypted = passphrase != null && DatabaseSecurity.encryptIfPlaintext(context, DB_NAME, passphrase)

        return Room.databaseBuilder(context, LogioDatabase::class.java, DB_NAME)
            .apply { if (encrypted) openHelperFactory(SupportOpenHelperFactory(passphrase)) }
            // Sürüm artırılırsa LogioDatabase'e Migration yazılmalı; yazılmazsa kayıtlar SİLİNMEZ, uygulama hata verir.
            // Yalnızca eski bir sürüme geri dönülürse (downgrade) tablo sıfırlanır.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideJournalDao(database: LogioDatabase): JournalDao {
        return database.journalDao()
    }
}
