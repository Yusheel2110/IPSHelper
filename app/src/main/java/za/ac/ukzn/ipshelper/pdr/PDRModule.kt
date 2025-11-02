package za.ac.ukzn.ipshelper.pdr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight PDR (Pedestrian Dead Reckoning) helper.
 * - Uses Rotation Vector (fused accel+gyro+mag) for heading.
 * - Very simple step detection on accelerometer magnitude (peak threshold).
 * - Integrates steps into (x,y) using a fixed step length.
 *
 * Coordinate system: relative to the start point (x0,y0) that YOU define.
 * Heading 0° = facing geographic North; movement projected in that frame.
 */
class PDRModule(
    context: Context,
    private val stepLengthMeters: Double = 0.70,   // tune per user/environment if needed
    private val headingLowPass: Double = 0.15      // 0..1; higher = more responsive, lower = smoother
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Public state
    @Volatile var currentX: Double = 0.0
        private set
    @Volatile var currentY: Double = 0.0
        private set
    @Volatile var currentZ: Int = 0
    @Volatile var headingDeg: Double = 0.0
        private set
    @Volatile var stepCount: Long = 0
        private set

    // Internals
    private var lastAccelMag = 0.0
    private var lastAccelTimeNs = 0L
    private var filteredHeadingRad = 0.0
    private var stepCooldownNs = 250_000_000L // 250ms between steps
    private var lastStepNs = 0L

    // naive peak detection
    private val stepThresholdHigh = 12.0    // m/s^2 ( >g indicates push-off peak, tune if needed)
    private val stepThresholdLow  = 9.5     // m/s^2 ( drop below to re-arm )

    private var armedForStep = true

    fun start() {
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        sm.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    fun resetTo(x: Double, y: Double, z: Int = 0) {
        currentX = x
        currentY = y
        currentZ = z
        stepCount = 0
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> updateHeading(event.values)
            Sensor.TYPE_ACCELEROMETER -> detectStep(event.timestamp, event.values)
        }
    }

    private fun updateHeading(rv: FloatArray) {
        // Rotation vector -> azimuth (heading) using Android helpers
        val rotMat = FloatArray(9)
        val orient = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotMat, rv)
        SensorManager.getOrientation(rotMat, orient)
        // Azimuth (orient[0]) is in radians, relative to magnetic north
        val rawHeading = orient[0].toDouble() // radians

        // Low-pass filter the heading to reduce jitter
        filteredHeadingRad = wrapAngleRad(
            headingLowPass * rawHeading + (1 - headingLowPass) * filteredHeadingRad
        )
        headingDeg = Math.toDegrees(filteredHeadingRad)
        if (headingDeg < 0) headingDeg += 360.0
    }

    private fun detectStep(tNs: Long, acc: FloatArray) {
        val ax = acc[0].toDouble()
        val ay = acc[1].toDouble()
        val az = acc[2].toDouble()
        val mag = kotlin.math.sqrt(ax*ax + ay*ay + az*az)

        // Simple peak/valley approach with cooldown to avoid double-count
        if (armedForStep && mag > stepThresholdHigh && (tNs - lastStepNs) > stepCooldownNs) {
            // Register a step
            stepCount += 1
            lastStepNs = tNs

            // Move one step in the current heading
            val dx = stepLengthMeters * cos(filteredHeadingRad)
            val dy = stepLengthMeters * sin(filteredHeadingRad)
            currentX += dx
            currentY += dy

            armedForStep = false
        } else if (mag < stepThresholdLow) {
            // Re-arm when below low threshold
            armedForStep = true
        }

        lastAccelMag = mag
        lastAccelTimeNs = tNs
    }

    private fun wrapAngleRad(a: Double): Double {
        var x = a
        while (x <= -Math.PI) x += 2.0 * Math.PI
        while (x >   Math.PI) x -= 2.0 * Math.PI
        return x
    }
}
