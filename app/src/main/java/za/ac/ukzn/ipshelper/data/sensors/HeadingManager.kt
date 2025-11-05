package za.ac.ukzn.ipshelper.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.roundToInt

/**
 * HeadingManager — gets a smooth, accurate heading using the fused Rotation Vector sensor.
 * Works like the phone's compass app, with optional "zero" calibration.
 */
class HeadingManager(
    private val context: Context,
    private val onHeadingUpdate: (Float) -> Unit,
    private val enableLogging: Boolean = false
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var currentHeading = 0f
    private var offset = 0f
    private var lastAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

    /** Start listening to rotation vector sensor. */
    fun start() {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Log.e("HeadingManager", "Rotation Vector Sensor not available")
        }
    }

    /** Stop listening to sensors. */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Set current heading as zero reference. */
    fun zero() {
        offset = currentHeading
    }

    /** Add a value to the current offset. */
    fun addOffset(deltaDeg: Float) {
        offset = (offset + deltaDeg + 360f) % 360f
    }

    /** Get the current offset. */
    fun getOffset(): Float {
        return offset
    }

    /** Get the last sensor accuracy status. */
    fun getLastAccuracy(): Int {
        return lastAccuracy
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        val orientationVals = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationVals)

        var azimuth = Math.toDegrees(orientationVals[0].toDouble()).toFloat()
        azimuth = (azimuth + 360) % 360

        val adjusted = ((azimuth - offset + 360) % 360)
        currentHeading = 0.9f * currentHeading + 0.1f * adjusted

        onHeadingUpdate(currentHeading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        lastAccuracy = accuracy
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.w("HeadingManager", "⚠️ Compass accuracy low — move device in figure 8 to recalibrate.")
        }
    }
}