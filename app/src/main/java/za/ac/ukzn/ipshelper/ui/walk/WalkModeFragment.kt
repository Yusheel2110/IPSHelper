package za.ac.ukzn.ipshelper.ui.walk

import android.Manifest
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class WalkModeFragment : Fragment() {

    // UI
    private lateinit var txtStatus: TextView
    private lateinit var txtHeading: TextView
    private lateinit var txtSteps: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtNextAnchor: TextView
    private lateinit var txtOffsetValue: TextView
    private lateinit var txtCurrentNode: TextView
    private lateinit var edtManualHeading: EditText
    private lateinit var btnSetHeading: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnMarkAnchor: Button
    private lateinit var btnAddOffset: Button
    private lateinit var btnSubOffset: Button
    private lateinit var btnResetOffset: Button
    private lateinit var btnNextAnchor: Button
    private lateinit var btnPrevAnchor: Button
    private lateinit var compassImage: ImageView
    private lateinit var spinnerStartLabel: Spinner
    private lateinit var spinnerEndLabel: Spinner

    // Core
    private var wifiScanner: WifiScanner? = null
    private var pdrManager: PDRManager? = null
    private lateinit var headingManager: HeadingManager

    // Session
    private var sessionId = ""
    private var isWalking = false
    private var startTime = 0L
    private var walkData = JSONArray()
    private var eventsArray = JSONArray()
    private var stepCount = 0
    private var lastHeading = 0.0
    private var headingOffset = 0.0
    private var manualHeadingOverride: Double? = null
    private var timer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var movingForward = true
    private var anchorIndex = 0
    private var currentAnchorIndex = 0

    // ✅ Anchor metadata (expandable)
    private val anchorPoints = mapOf(
        "C1" to Pair(0.0, 0.0),
        "C4" to Pair(1.0154, 4.6353),
        "C9" to Pair(16.3398, 4.6369),
        "C14" to Pair(27.0163, 4.6369)
    )
    private val anchorList = anchorPoints.keys.sorted()

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

        txtStatus = view.findViewById(R.id.txtStatus)
        txtHeading = view.findViewById(R.id.txtHeading)
        txtSteps = view.findViewById(R.id.txtSteps)
        txtDuration = view.findViewById(R.id.txtDuration)
        txtNextAnchor = view.findViewById(R.id.txtNextAnchor)
        txtOffsetValue = view.findViewById(R.id.txtOffsetValue)
        txtCurrentNode = view.findViewById(R.id.txtCurrentNode)
        edtManualHeading = view.findViewById(R.id.edtManualHeading)
        btnSetHeading = view.findViewById(R.id.btnSetHeading)
        btnStart = view.findViewById(R.id.btnStartWalk)
        btnStop = view.findViewById(R.id.btnStopWalk)
        btnMarkAnchor = view.findViewById(R.id.btnMarkAnchor)
        btnAddOffset = view.findViewById(R.id.btnAddOffset)
        btnSubOffset = view.findViewById(R.id.btnSubOffset)
        btnResetOffset = view.findViewById(R.id.btnResetOffset)
        btnNextAnchor = view.findViewById(R.id.btnNextAnchor)
        btnPrevAnchor = view.findViewById(R.id.btnPrevAnchor)
        compassImage = view.findViewById(R.id.compassImage)
        spinnerStartLabel = view.findViewById(R.id.spinnerStartLabel)
        spinnerEndLabel = view.findViewById(R.id.spinnerEndLabel)

        // ✅ Populate start/end spinners dynamically
        val coordLabels = anchorList
        val labelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, coordLabels)
        labelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStartLabel.adapter = labelAdapter
        spinnerEndLabel.adapter = labelAdapter

        spinnerStartLabel.setSelection(0)
        spinnerEndLabel.setSelection(coordLabels.size - 1)

        wifiScanner = WifiScanner(requireContext())
        pdrManager = PDRManager(requireContext())
        headingManager = HeadingManager(requireContext()) { heading ->
            updateCompass(heading)
        }

        // Offset + manual heading
        btnAddOffset.setOnClickListener {
            headingOffset += 5
            updateOffsetDisplay()
        }
        btnSubOffset.setOnClickListener {
            headingOffset -= 5
            updateOffsetDisplay()
        }
        btnResetOffset.setOnClickListener {
            headingOffset = 0.0
            manualHeadingOverride = null
            updateOffsetDisplay()
        }
        btnSetHeading.setOnClickListener {
            val value = edtManualHeading.text.toString().toDoubleOrNull()
            if (value != null) {
                manualHeadingOverride = (value % 360 + 360) % 360
                lastHeading = manualHeadingOverride!!
                compassImage.rotation = -manualHeadingOverride!!.toFloat()
                txtHeading.text = "Heading: %.1f° (manual)".format(manualHeadingOverride)
            } else {
                Toast.makeText(requireContext(), "Enter valid number", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Anchor cycling now updates correctly
        btnNextAnchor.setOnClickListener {
            currentAnchorIndex = (currentAnchorIndex + 1) % anchorList.size
            updateCurrentNodeText()
            updateNextAnchorText()
        }
        btnPrevAnchor.setOnClickListener {
            currentAnchorIndex = if (currentAnchorIndex - 1 < 0) anchorList.size - 1 else currentAnchorIndex - 1
            updateCurrentNodeText()
            updateNextAnchorText()
        }

        btnStart.setOnClickListener { checkPermissionsAndStart() }
        btnStop.setOnClickListener { stopWalk() }
        btnMarkAnchor.setOnClickListener { markAnchor() }

        updateCurrentNodeText()
        startGleamEffect()
        updateNextAnchorText()
    }

    private fun updateCompass(rawHeading: Float) {
        val heading = manualHeadingOverride ?: rawHeading.toDouble()
        val adjusted = ((heading + headingOffset + 360) % 360)
        lastHeading = adjusted
        compassImage.rotation = -adjusted.toFloat()
        txtHeading.text = "Heading: %.1f°".format(adjusted)
    }

    private fun updateOffsetDisplay() {
        txtOffsetValue.text = "Offset: %.0f°".format(headingOffset)
    }

    private fun updateCurrentNodeText() {
        val currentNode = anchorList[currentAnchorIndex]
        txtCurrentNode.text = "Current: $currentNode"
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
        val nextIndex = (currentAnchorIndex + 1) % anchorList.size
        val nextAnchor = anchorList[nextIndex]
        txtNextAnchor.text = "Next Anchor: $nextAnchor"
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
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

        pdrManager?.reset() // ✅ Reset step baseline before walk
        txtSteps.text = "Steps: 0"
        txtDuration.text = "Duration: 0 s"
        txtStatus.text = "Session ID: $sessionId\nCollecting data..."

        pdrManager?.start { _, _, heading ->
            lastHeading = heading
            stepCount = pdrManager?.stepCount ?: 0
        }

        timer = fixedRateTimer("walkScan", true, 0L, 1000L) {
            handler.post {
                performScan()
                updateDuration()
            }
        }

        Toast.makeText(requireContext(), "Walk started", Toast.LENGTH_SHORT).show()
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

        val currentNode = anchorList[currentAnchorIndex]
        val coords = anchorPoints[currentNode] ?: return
        val anchorJson = JSONObject().apply {
            put("type", "anchor_marker")
            put("label", currentNode)
            put("timestamp", System.currentTimeMillis())
            put("step_count", stepCount)
            put("heading", lastHeading)
            put("x", coords.first)
            put("y", coords.second)
        }

        eventsArray.put(anchorJson)
        txtStatus.text = "Anchor marked: $currentNode"
        Toast.makeText(requireContext(), "Anchor $currentNode saved.", Toast.LENGTH_SHORT).show()
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
        pdrManager?.reset() // ✅ Reset again after stop to clear step baseline

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
    }

    override fun onResume() {
        super.onResume()
        headingManager.start()
        txtStatus.text = "Compass active — ready to walk"
    }

    override fun onPause() {
        super.onPause()
        headingManager.stop()
        if (isWalking) stopWalk()
    }
}
