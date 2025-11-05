package za.ac.ukzn.ipshelper.ui.walk

import android.Manifest
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import za.ac.ukzn.ipshelper.R
import za.ac.ukzn.ipshelper.data.pdr.PDRManager
import za.ac.ukzn.ipshelper.data.sensors.HeadingManager
import za.ac.ukzn.ipshelper.data.wifi.WifiScanner
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.math.roundToInt
import android.hardware.SensorManager

data class AnchorPoint(val x: Double, val y: Double)

class WalkModeFragment : Fragment() {

    // --- UI ---
    private lateinit var txtStatus: TextView
    private lateinit var txtHeading: TextView
    private lateinit var txtSteps: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtNextAnchor: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnMarkAnchor: Button
    private lateinit var btnZeroHeading: Button

    private lateinit var compassImage: ImageView
    private lateinit var spinnerStartLabel: Spinner
    private lateinit var spinnerEndLabel: Spinner

    // --- Core modules ---
    private var wifiScanner: WifiScanner? = null
    private var pdrManager: PDRManager? = null
    private lateinit var headingManager: HeadingManager

    // --- Session ---
    private var sessionId = ""
    private var isWalking = false
    private var startTime = 0L
    private var walkData = JSONArray()
    private var eventsArray = JSONArray()
    private var stepCount = 0
    private var lastHeading = 0.0
    private var timer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var movingForward = true
    private var anchorIndex = 0

    // --- Logging ---
    private var csvLogger: CsvLogger? = null

    // --- All coordinate labels (for spinners) ---
    private val allCoordLabels = listOf(
        "Stairs", "1-01", "1-02", "1-03", "1-04", "1-05", "1-06",
        "1-07", "1-08", "1-08A", "1-09", "1-10", "1-11", "1-12", "1-13",
        "big doors", "elevator", "staircase", "end of hall",
        "C1", "C4", "C5", "C7", "C8", "C9", "C10", "C13", "C14", "C16", "C17"
    )

    // --- Anchors to be recorded in JSON ---
    private val anchorPoints = mapOf(
        "Stairs" to AnchorPoint(1.9433, 0.6749),
        "C1" to AnchorPoint(1.0187, 0.6741),
        "C4" to AnchorPoint(1.0154, 4.6353),
        "C5" to AnchorPoint(2.9398, 4.6369),
        "C7" to AnchorPoint(7.4118, 4.6369),
        "C8" to AnchorPoint(12.9858, 4.6369),
        "C9" to AnchorPoint(16.3398, 4.6369),
        "C10" to AnchorPoint(19.6938, 4.6369),
        "C13" to AnchorPoint(23.0568, 4.6369),
        "C14" to AnchorPoint(27.0163, 4.6369),
        "C16" to AnchorPoint(27.0398, 2.4684),
        "C17" to AnchorPoint(27.0320, 0.4515)
    )

    private val anchorsForward = listOf(
        "Stairs", "C1", "C4", "C5", "C7", "C8",
        "C9", "C10", "C13", "C14", "C16", "C17"
    )
    private val anchorsReverse = anchorsForward.reversed()

    private val accuracyCheckHandler = Handler(Looper.getMainLooper())
    private val accuracyCheckRunnable = object : Runnable {
        override fun run() {
            updateAccuracyIndicator()
            accuracyCheckHandler.postDelayed(this, 1000)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) startWalk()
        else Toast.makeText(requireContext(), "Permission denied.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_walk_mode, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- UI binding ---
        txtStatus = view.findViewById(R.id.txtStatus)
        txtHeading = view.findViewById(R.id.txtHeading)
        txtSteps = view.findViewById(R.id.txtSteps)
        txtDuration = view.findViewById(R.id.txtDuration)
        txtNextAnchor = view.findViewById(R.id.txtNextAnchor)
        btnStart = view.findViewById(R.id.btnStartWalk)
        btnStop = view.findViewById(R.id.btnStopWalk)
        btnZeroHeading = view.findViewById(R.id.btnZeroHeading)
        btnMarkAnchor = view.findViewById(R.id.btnMarkAnchor)
        compassImage = view.findViewById(R.id.compassImage)
        spinnerStartLabel = view.findViewById(R.id.spinnerStartLabel)
        spinnerEndLabel = view.findViewById(R.id.spinnerEndLabel)

        val labelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, allCoordLabels)
        labelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStartLabel.adapter = labelAdapter
        spinnerEndLabel.adapter = labelAdapter

        updateDirectionLabels()
        startGleamEffect()
        updateNextAnchorText()

        wifiScanner = WifiScanner(requireContext())
        pdrManager = PDRManager(requireContext())

        headingManager = HeadingManager(
            context = requireContext(),
            onHeadingUpdate = { heading -> updateCompass(heading) },
            enableLogging = false
        )

        btnStart.setOnClickListener { checkPermissionsAndStart() }
        btnStop.setOnClickListener { stopWalk() }
        btnMarkAnchor.setOnClickListener { markAnchor() }
        btnZeroHeading.setOnClickListener {
            headingManager.zero()
            Toast.makeText(requireContext(), "Heading zeroed — current direction set as 0°", Toast.LENGTH_SHORT).show()
        }


        Toast.makeText(requireContext(), "📍 Compass active — wave phone in figure-8 before walking!", Toast.LENGTH_LONG).show()
    }

    private var lastCompassRotation = 0f

    private fun updateCompass(rawHeading: Float) {
        lastHeading = rawHeading.toDouble()
        val newRotation = -rawHeading

        var delta = newRotation - lastCompassRotation
        if (delta > 180) delta -= 360
        else if (delta < -180) delta += 360
        val smoothRotation = lastCompassRotation + delta

        compassImage.rotation = smoothRotation
        lastCompassRotation = smoothRotation
        txtHeading.text = "Heading: %.1f°".format(lastHeading)
    }

    private fun updateAccuracyIndicator() {
        when (headingManager.getLastAccuracy()) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                txtHeading.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                compassImage.alpha = if (System.currentTimeMillis() % 1000 < 500) 0.3f else 1.0f
            }
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> {
                txtHeading.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                compassImage.alpha = 1.0f
            }
            else -> {
                txtHeading.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                compassImage.alpha = 1.0f
            }
        }
    }

    private fun updateDirectionLabels() {
        val startLabel = if (movingForward) "Stairs" else "1-13"
        val endLabel = if (movingForward) "1-13" else "Stairs"

        spinnerStartLabel.setSelection(allCoordLabels.indexOf(startLabel))
        spinnerEndLabel.setSelection(allCoordLabels.indexOf(endLabel))
    }

    private fun startGleamEffect() {
        ObjectAnimator.ofFloat(txtNextAnchor, "alpha", 0.3f, 1f).apply {
            duration = 1000
            interpolator = LinearInterpolator()
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun updateNextAnchorText() {
        val list = if (movingForward) anchorsForward else anchorsReverse
        val nextAnchor = if (anchorIndex < list.size) list[anchorIndex] else "None"
        txtNextAnchor.text = "Next Anchor: $nextAnchor"
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startWalk() else requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startWalk() {
        if (isWalking) return
        isWalking = true

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        sessionId = sdf.format(Date())
        startTime = System.currentTimeMillis()
        walkData = JSONArray()
        eventsArray = JSONArray()
        stepCount = 0
        anchorIndex = 0

        csvLogger = CsvLogger(requireContext(), sessionId)
        csvLogger?.logEvent("start_walk", "session_id=$sessionId,direction=${if (movingForward) "forward" else "reverse"}")

        pdrManager?.start { _, _, heading ->
            lastHeading = heading
            stepCount = pdrManager?.stepCount ?: 0
        }

        txtStatus.text = "Session ID: $sessionId\nCollecting data..."
        txtSteps.text = "Steps: 0"
        txtDuration.text = "Duration: 0 s"

        timer = fixedRateTimer("walkScan", true, 0L, 1000L) {
            handler.post {
                performScan()
                updateDuration()
            }
        }

        Toast.makeText(requireContext(), "Walk started", Toast.LENGTH_SHORT).show()
        updateNextAnchorText()
    }

    private fun performScan() {
        if (!isWalking) return
        val results: List<ScanResult> = wifiScanner?.scanBlocking() ?: emptyList()
        val timestamp = System.currentTimeMillis()
        stepCount = pdrManager?.stepCount ?: 0
        val distance = stepCount * 0.75

        val wifiArray = JSONArray()
        results.forEach {
            wifiArray.put(JSONObject().apply {
                put("ssid", it.SSID)
                put("bssid", it.BSSID)
                put("rssi", it.level)
            })
        }

        val entry = JSONObject().apply {
            put("timestamp", timestamp)
            put("session_id", sessionId)
            put("step_count", stepCount)
            put("distance", distance)
            put("heading", lastHeading)
            put("phone_orientation", "front_portrait")
            put("wifi_data", wifiArray)
        }

        walkData.put(entry)
        txtSteps.text = "Steps: $stepCount"
        txtStatus.text = "Scanning... ${results.size} networks"
    }

    private fun markAnchor() {
        if (!isWalking) {
            Toast.makeText(requireContext(), "Start walking first.", Toast.LENGTH_SHORT).show()
            return
        }

        val list = if (movingForward) anchorsForward else anchorsReverse
        if (anchorIndex >= list.size) {
            Toast.makeText(requireContext(), "No anchors left in this direction.", Toast.LENGTH_SHORT).show()
            return
        }

        val label = list[anchorIndex]
        val anchorData = anchorPoints[label] ?: return
        val anchorJson = JSONObject().apply {
            put("type", "anchor_marker")
            put("label", label)
            put("timestamp", System.currentTimeMillis())
            put("step_count", stepCount)
            put("heading", lastHeading)
            put("x", anchorData.x)
            put("y", anchorData.y)
        }

        eventsArray.put(anchorJson)
        txtStatus.text = "Anchor marked: $label"
        Toast.makeText(requireContext(), "Anchor $label saved.", Toast.LENGTH_SHORT).show()

        anchorIndex++
        updateNextAnchorText()
    }

    private fun updateDuration() {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        txtDuration.text = "Duration: $elapsed s"
    }

    private fun stopWalk() {
        if (!isWalking) return
        isWalking = false
        timer?.cancel()
        pdrManager?.stop()
        pdrManager?.resetTo(0.0, 0.0, 0)

        csvLogger?.logEvent("stop_walk", "samples=${walkData.length()},events=${eventsArray.length()}")
        csvLogger?.close()

        val startLabel = spinnerStartLabel.selectedItem.toString()
        val endLabel = spinnerEndLabel.selectedItem.toString()

        val sessionJson = JSONObject().apply {
            put("session_id", sessionId)
            put("collector", "Yusheel")
            put("start_label", startLabel)
            put("end_label", endLabel)
            put("stride_length_m", 0.75)
            put("samples", walkData)
            put("events", eventsArray)
        }

        val file = File(requireContext().getExternalFilesDir(null), "walk_${sessionId}.json")
        FileOutputStream(file).use { it.write(sessionJson.toString(4).toByteArray()) }

        txtStatus.text = "Session saved: ${file.name}"
        Toast.makeText(requireContext(), "Walk data saved to ${file.name}", Toast.LENGTH_LONG).show()

        movingForward = !movingForward
        anchorIndex = 0
        stepCount = 0
        updateDirectionLabels()
        updateNextAnchorText()
    }

    override fun onResume() {
        super.onResume()
        headingManager.start()
        accuracyCheckHandler.post(accuracyCheckRunnable)
        txtStatus.text = "Compass active — ready to walk"
    }

    override fun onPause() {
        super.onPause()
        headingManager.stop()
        accuracyCheckHandler.removeCallbacks(accuracyCheckRunnable)
        if (isWalking) stopWalk()
    }
}

// --- CSV Logger Class ---
class CsvLogger(context: Context, sessionId: String) {
    private val file: File
    private val writer: FileOutputStream

    init {
        val dir = File(context.getExternalFilesDir(null), "logs")
        if (!dir.exists()) dir.mkdirs()
        file = File(dir, "walk_${sessionId}_log.csv")
        writer = FileOutputStream(file, true)
        writer.write("timestamp,event_type,heading,step_count,wifi_count,details\n".toByteArray())
    }

    fun logEvent(eventType: String, details: String) {
        val line = "${System.currentTimeMillis()},$eventType,,,,$details\n"
        writer.write(line.toByteArray())
        writer.flush()
    }

    fun close() {
        writer.close()
    }
}
