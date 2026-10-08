package com.example.logio.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant

data class SleepSummary(
    val startTime: Instant,
    val endTime: Instant,
    val hours: Double
)

class HealthConnectManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnectManager"
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
                Log.e(
                    TAG,
                    "Health Connect mağaza sayfası açılamadı: ${e.message}"
                )
            }
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = getGrantedPermissions()
        val missing = permissions - granted

        Log.d(TAG, "İstenen izinler: $permissions")
        Log.d(TAG, "Verilen izinler: $granted")
        Log.d(TAG, "Eksik izinler: $missing")

        return missing.isEmpty()
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
            Log.e(TAG, "İzinler okunamadı: ${e.message}")
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
            Log.w(TAG, "Uyku okuma izni verilmemiş.")
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
            Log.e(TAG, "Uyku verisi okunamadı: ${e.message}")
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

    suspend fun readSteps(
        startTime: Instant,
        endTime: Instant
    ): Long {

        if (!isAvailable() || !startTime.isBefore(endTime)) {
            return 0L
        }

        if (!hasStepsPermission()) {
            Log.w(TAG, "Adım okuma izni verilmemiş.")
            return 0L
        }

        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        startTime,
                        endTime
                    )
                )
            )

            response[StepsRecord.COUNT_TOTAL] ?: 0L

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Adım verisi okunamadı: ${e.message}")
            0L
        }
    }
}
