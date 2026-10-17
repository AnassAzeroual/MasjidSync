package com.mosquetracker.app.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2

/**
 * Phone heading from the accelerometer + magnetometer pair.
 * Emits nothing on devices without both sensors — the UI shows an unavailable note instead.
 * No permission needed; fully offline.
 */
@Singleton
class CompassProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** Azimuth in degrees clockwise from north, in [0, 360). */
    fun azimuthDegrees(): Flow<Float> = callbackFlow {
        val manager = sensorManager ?: run {
            close()
            return@callbackFlow
        }
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accelerometer == null || magnetometer == null) {
            close()
            return@callbackFlow
        }

        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> gravity = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values.clone()
                }
                val g = gravity ?: return
                val m = geomagnetic ?: return
                val rotation = FloatArray(9)
                val inclination = FloatArray(9)
                if (!SensorManager.getRotationMatrix(rotation, inclination, g, m)) return
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotation, orientation)
                val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                trySend((degrees + 360f) % 360f)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        manager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI,
        )
        manager.registerListener(
            listener,
            magnetometer,
            SensorManager.SENSOR_DELAY_UI,
        )
        awaitClose { manager.unregisterListener(listener) }
    }
}
