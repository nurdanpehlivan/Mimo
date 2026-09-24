package com.example.logio.data.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepCounterManager @Inject constructor(
    @ApplicationContext context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val prefs = context.getSharedPreferences("step_counter_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        StepCounterUiState(
            isSensorAvailable = stepCounterSensor != null || stepDetectorSensor != null,
            activeSensorName = when {
                stepCounterSensor != null -> "STEP_COUNTER"
                stepDetectorSensor != null -> "STEP_DETECTOR"
                else -> null
            }
        )
    )
    val uiState = _uiState.asStateFlow()

    fun startTracking() {
        if (_uiState.value.isTracking) return

        val sensorToUse = stepCounterSensor ?: stepDetectorSensor
        if (sensorToUse == null) {
            _uiState.value = _uiState.value.copy(
                isSensorAvailable = false,
                isTracking = false,
                errorMessage = "Bu cihazda adim sensoru yok."
            )
            return
        }

        val registered = sensorManager?.registerListener(
            this,
            sensorToUse,
            SensorManager.SENSOR_DELAY_UI
        ) ?: false

        _uiState.value = _uiState.value.copy(
            isTracking = registered,
            activeSensorName = when (sensorToUse.type) {
                Sensor.TYPE_STEP_COUNTER -> "STEP_COUNTER"
                Sensor.TYPE_STEP_DETECTOR -> "STEP_DETECTOR"
                else -> null
            },
            errorMessage = if (registered) null else "Adim takibi baslatilamadi."
        )
    }

    fun stopTracking() {
        if (_uiState.value.isTracking) {
            sensorManager?.unregisterListener(this)
        }
        _uiState.value = _uiState.value.copy(isTracking = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorType = event?.sensor?.type ?: return
        val todayKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        when (sensorType) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalStepsSinceBoot = event.values.firstOrNull()?.toLong() ?: return

                var baseline = prefs.getLong(KEY_BASELINE, -1L)
                val storedDate = prefs.getString(KEY_DATE, "")

                if (storedDate != todayKey || baseline < 0L || totalStepsSinceBoot < baseline) {
                    baseline = totalStepsSinceBoot
                    prefs.edit {
                        putString(KEY_DATE, todayKey)
                        putLong(KEY_BASELINE, baseline)
                        putInt(KEY_DETECTOR_STEPS, 0)
                    }
                }

                val todaySteps = (totalStepsSinceBoot - baseline).coerceAtLeast(0L).toInt()
                _uiState.value = _uiState.value.copy(
                    todaySteps = todaySteps,
                    errorMessage = null
                )
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                val storedDate = prefs.getString(KEY_DATE, "")
                if (storedDate != todayKey) {
                    prefs.edit {
                        putString(KEY_DATE, todayKey)
                        putLong(KEY_BASELINE, -1L)
                        putInt(KEY_DETECTOR_STEPS, 0)
                    }
                }

                val increment = event.values.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
                val updated = prefs.getInt(KEY_DETECTOR_STEPS, 0) + increment
                prefs.edit { putInt(KEY_DETECTOR_STEPS, updated) }

                _uiState.value = _uiState.value.copy(
                    todaySteps = updated,
                    errorMessage = null
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val KEY_DATE = "today_date"
        const val KEY_BASELINE = "today_baseline"
        const val KEY_DETECTOR_STEPS = "detector_steps"
    }
}

data class StepCounterUiState(
    val isSensorAvailable: Boolean,
    val isTracking: Boolean = false,
    val todaySteps: Int = 0,
    val activeSensorName: String? = null,
    val errorMessage: String? = null
)
