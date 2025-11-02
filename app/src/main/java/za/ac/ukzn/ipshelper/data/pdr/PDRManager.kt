package za.ac.ukzn.ipshelper.data.pdr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import za.ac.ukzn.ipshelper.data.sensors.HeadingManager
import kotlin.math.*

/**
 * Pedestrian Dead Reckoning (PDR) Manager
 * Counts steps and estimates X/Y displacement.
 * Uses accelerometer for steps and HeadingManager for orientation.
 */
class PDRManager(private val context: Context) : SensorEventListener {

    // --- State ---
    var currentX: Double = 0.0
    var currentY: Double = 0.0
    var currentZ: Int = 0
    var headingDeg: Double = 0.0
    var stepCount: Int = 0

    // --- Step detection ---
    private var sensorManager: SensorManager? = null
    private var accelValues = FloatArray(3)
    private var lastStepTime = 0L
    private val stepLength = 0.75   // meters
    private val stepThreshold = 11.0
    private val minStepInterval = 500L // ms

    // --- Heading integration ---
    private lateinit var headingManager: HeadingManager

    // --- Optional callback for live updates ---
    private var stepListener: ((Double, Double, Double) -> Unit)? = null

    // =====================================================
    // Public API
    // =====================================================

    fun start(onStep: ((x: Double, y: Double, heading: Double) -> Unit)? = null) {
        stepListener = onStep
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Register accelerometer for step detection
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Start HeadingManager for smoother heading
        headingManager = HeadingManager(context) { heading ->
            headingDeg = heading.toDouble()
        }
        headingManager.start()
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        headingManager.stop()
        sensorManager = null
        stepListener = null
    }

    fun resetTo(x: Double, y: Double, z: Int) {
        currentX = x
        currentY = y
        currentZ = z
        headingDeg = 0.0
        stepCount = 0
    }

    // =====================================================
    // Sensor Handling
    // =====================================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelValues = event.values.clone()
            detectStep(accelValues)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // =====================================================
    // Step Detection Logic
    // =====================================================

    private fun detectStep(accel: FloatArray) {
        val magnitude = sqrt(accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2])
        val now = System.currentTimeMillis()

        if (magnitude > stepThreshold && (now - lastStepTime) > minStepInterval) {
            lastStepTime = now
            stepCount++
            advancePosition()
        }
    }

    private fun advancePosition() {
        val rad = Math.toRadians(headingDeg)
        // Local coordinate system (0° = +Y)
        val dx = stepLength * sin(rad)
        val dy = stepLength * cos(rad)

        currentX += dx
        currentY += dy

        stepListener?.invoke(currentX, currentY, headingDeg)
        Log.d("PDRManager", "Step #$stepCount → x=$currentX, y=$currentY, heading=$headingDeg")
    }

    // =====================================================
    // Getter
    // =====================================================
    fun getHeading(): Double = headingDeg
}
