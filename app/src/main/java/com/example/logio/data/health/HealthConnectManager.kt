/*
 * HealthConnectManager: Google Health Connect'ten adım ve uyku okur.
 *
 * Health Connect'in telefonda kurulu olup olmadığını ve izinleri kontrol eder; günlük adım toplamlarını
 * (readDailySteps) ve dün geceki uykuyu (readLastNightSleep, uyanık geçen süreler düşülerek) döndürür.
 * Her veri türü yalnızca kendi iznine bakar: kullanıcı sadece adıma izin verirse adımlar yine okunur.
 * Logio adımın tek kaynağı olarak bunu kullanır. Hilt'e di/HealthModule.kt üzerinden verilir.
 */
package com.example.logio.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period

data class SleepSummary(
    val startTime: Instant,
    val endTime: Instant,
    val hours: Double
)

class HealthConnectManager(private val context: Context) {

    companion object {
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }

    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    private val stepsReadPermission =
        HealthPermission.getReadPermission(StepsRecord::class)

    private val sleepReadPermission =
        HealthPermission.getReadPermission(SleepSessionRecord::class)

    val permissions = setOf(
        stepsReadPermission,
        sleepReadPermission
    )

    fun getAvailabilityStatus(): Int {
        return HealthConnectClient.getSdkStatus(
            context,
            HEALTH_CONNECT_PACKAGE
        )
    }

    fun isAvailable(): Boolean {
        return getAvailabilityStatus() == HealthConnectClient.SDK_AVAILABLE
    }

    fun openPlayStoreForHealthConnect() {
        val uriString =
            "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(uriString)
        ).apply {
            setPackage("com.android.vending")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"
                )
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(webIntent)
            } catch (e: Exception) {
                Timber.e(e, "Health Connect mağaza sayfası açılamadı")
            }
        }
    }

    suspend fun hasStepsPermission(): Boolean {
        return stepsReadPermission in getGrantedPermissions()
    }

    suspend fun hasSleepPermission(): Boolean {
        return sleepReadPermission in getGrantedPermissions()
    }

    suspend fun getGrantedPermissions(): Set<String> {
        if (!isAvailable()) return emptySet()

        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "İzinler okunamadı")
            emptySet()
        }
    }

    suspend fun readSleep(
        startTime: Instant,
        endTime: Instant
    ): List<SleepSessionRecord> {

        if (!isAvailable() || !startTime.isBefore(endTime)) {
            return emptyList()
        }

        if (!hasSleepPermission()) {
            Timber.w("Uyku okuma izni verilmemiş.")
            return emptyList()
        }

        return try {
            val records = mutableListOf<SleepSessionRecord>()
            var pageToken: String? = null

            do {
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            startTime,
                            endTime
                        ),
                        pageToken = pageToken
                    )
                )

                records += response.records
                pageToken = response.pageToken

            } while (!pageToken.isNullOrEmpty())

            records
                .filter { it.endTime.isAfter(it.startTime) }
                .sortedBy { it.endTime }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Uyku verisi okunamadı")
            emptyList()
        }
    }


    /**
     * Son 24 saatteki en son uyku oturumunu özetler (genelde dün gece).
     * Uyku evreleri varsa uyanık geçen süreler düşülür.
     * İzin yoksa, Health Connect yoksa ya da kayıt bulunamazsa null döner.
     */
    suspend fun readLastNightSleep(now: Instant = Instant.now()): SleepSummary? {
        val session = readSleep(now.minus(Duration.ofHours(24)), now).lastOrNull() ?: return null

        val awakeStages = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        )
        val totalMinutes = Duration.between(session.startTime, session.endTime).toMinutes()
        val asleepMinutes = if (session.stages.isEmpty()) {
            totalMinutes
        } else {
            session.stages
                .filter { it.stage !in awakeStages }
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                .takeIf { it > 0 } ?: totalMinutes
        }

        return SleepSummary(
            startTime = session.startTime,
            endTime = session.endTime,
            hours = Math.round(asleepMinutes / 60.0 * 10) / 10.0
        )
    }

    /**
     * [firstDay] ile [lastDay] arasındaki her gün için o günün toplam adımı (gün → adım).
     * Bütün günler TEK istekte gelir ve izin bir kez sorulur (Health Connect'in istek sınırı var).
     *
     * - Toplam (aggregate) kullanılır: Samsung Health ve telefonun kendi sayacı aynı adımları saysa da
     *   Health Connect kaynak önceliğine göre tekrarları ayıklar.
     * - Her dilim tam gündür (00:00–24:00, telefonun saatine göre). Samsung Health günün adımlarını
     *   tek kayıt olarak yazdığı için yarım gün sorulursa Health Connect kaydı oranlar (öğlen 8.405 → 4.555).
     * İzin yoksa, Health Connect yoksa ya da hata olursa boş harita döner.
     */
    suspend fun readDailySteps(firstDay: LocalDate, lastDay: LocalDate): Map<LocalDate, Long> {
        if (!isAvailable() || lastDay.isBefore(firstDay)) return emptyMap()

        if (!hasStepsPermission()) {
            Timber.w("Adım okuma izni verilmemiş.")
            return emptyMap()
        }

        return try {
            healthConnectClient.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        firstDay.atStartOfDay(),
                        lastDay.plusDays(1).atStartOfDay()
                    ),
                    timeRangeSlicer = Period.ofDays(1)
                )
            ).associate { slice ->
                slice.startTime.toLocalDate() to (slice.result[StepsRecord.COUNT_TOTAL] ?: 0L)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Adım verisi okunamadı")
            emptyMap()
        }
    }
}
