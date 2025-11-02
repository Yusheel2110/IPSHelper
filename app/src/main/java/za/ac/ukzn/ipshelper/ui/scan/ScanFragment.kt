package za.ac.ukzn.ipshelper.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import org.json.JSONArray
import org.json.JSONObject
import za.ac.ukzn.ipshelper.R
import za.ac.ukzn.ipshelper.data.sensors.HeadingManager
import za.ac.ukzn.ipshelper.data.wifi.WifiScanner
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class ScanFragment : Fragment() {

    // ---------- UI ----------
    private lateinit var spinner: Spinner
    private lateinit var spinnerDirection: Spinner
    private lateinit var txtCoords: TextView
    private lateinit var txtResults: TextView
    private lateinit var txtHeading: TextView
    private lateinit var compassImage: ImageView
    private lateinit var btnScan: Button
    private lateinit var btnSave: Button
    private lateinit var btnFlag: Button
    private lateinit var btnZero: Button

    // ---------- Heading ----------
    private lateinit var headingManager: HeadingManager
    private var currentHeading = 0f

    // ---------- Scan Data ----------
    private var lastScanResults: List<ScanResult> = emptyList()
    private var selectedEntry: CoordEntry? = null
    private var movingForward = true
    private var directionIndex = 0
    private var flagCounter = 0

    private val coordList = listOf(
        CoordEntry("1-01", 1.0f, 0.0f),
        CoordEntry("1-02", 0.1f, 1.0f),
        CoordEntry("1-03", 0.1f, 4.3f),
        CoordEntry("1-04", 1.0f, 5.5f),
        CoordEntry("big doors", 2.0f, 4.6f),
        CoordEntry("1-05", 3.0f, 5.5f),
        CoordEntry("elevator", 3.3f, 3.9f),
        CoordEntry("1-06", 7.5f, 5.5f),
        CoordEntry("1-07", 13.0f, 5.5f),
        CoordEntry("1-08", 16.3f, 5.5f),
        CoordEntry("1-08A", 19.8f, 5.5f),
        CoordEntry("staircase", 20.6f, 4.0f),
        CoordEntry("1-17", 22.0f, 4.0f),
        CoordEntry("1-09", 23.0f, 5.5f),
        CoordEntry("end of hall", 25.99f, 4.64f),
        CoordEntry("1-10", 27.0f, 5.5f),
        CoordEntry("1-11", 28.0f, 4.5f),
        CoordEntry("1-16", 26.0f, 2.5f),
        CoordEntry("1-12", 28.0f, 0.5f),
        CoordEntry("1-13", 27.0f, -3.5f)
    )

    // ---------- Permissions ----------
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) performScan()
        else Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show()
    }

    // ---------- Lifecycle ----------
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_scan, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinner = view.findViewById(R.id.spinnerLabels)
        spinnerDirection = view.findViewById(R.id.spinnerDirection)
        txtCoords = view.findViewById(R.id.txtCoords)
        txtResults = view.findViewById(R.id.txtResults)
        txtHeading = view.findViewById(R.id.txtHeading)
        compassImage = view.findViewById(R.id.compassImage)
        btnScan = view.findViewById(R.id.btnScan)
        btnSave = view.findViewById(R.id.btnSaveScan)
        btnFlag = view.findViewById(R.id.btnFlag)
        btnZero = view.findViewById(R.id.btnZeroHeading) // from XML

        // Spinner setup
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, coordList.map { it.label })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                selectedEntry = coordList[pos]
                txtCoords.text = "Coordinates: (x=${selectedEntry!!.x}, y=${selectedEntry!!.y}, z=1)"
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val dirAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.directions, android.R.layout.simple_spinner_item)
        dirAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDirection.adapter = dirAdapter
        spinnerDirection.setSelection(directionIndex)

        // Heading Manager init
        headingManager = HeadingManager(requireContext()) { heading ->
            txtHeading.text = "Heading: ${heading.roundToInt()}°"
            compassImage.rotation = -heading
            currentHeading = heading
        }

        // Buttons
        btnScan.setOnClickListener { handleScanClick() }
        btnSave.setOnClickListener { handleSaveScanClick() }
        btnFlag.setOnClickListener { handleFlagClick() }
        btnZero.setOnClickListener { headingManager.zero() }
    }

    override fun onResume() {
        super.onResume()
        headingManager.start()
    }

    override fun onPause() {
        super.onPause()
        headingManager.stop()
    }

    // ---------- Wi-Fi ----------
    private fun handleScanClick() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) performScan() else requestPermissionLauncher.launch(permissions)
    }

    private fun performScan() {
        val scanner = WifiScanner(requireContext())
        lastScanResults = scanner.scanBlocking()
        val lines = lastScanResults.joinToString("\n") { "${it.SSID} | ${it.BSSID} | ${it.level} dBm" }
        txtResults.text = "Scan Results:\n${if (lines.isEmpty()) "No networks found" else lines}"
        Toast.makeText(requireContext(), "Scan complete.", Toast.LENGTH_SHORT).show()
    }

    // ---------- Save ----------
    private fun handleSaveScanClick() {
        val entry = selectedEntry ?: run {
            Toast.makeText(requireContext(), "Select a location first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (lastScanResults.isEmpty()) {
            Toast.makeText(requireContext(), "No scan data to save.", Toast.LENGTH_SHORT).show()
            return
        }

        val direction = spinnerDirection.selectedItem.toString()
        val heading = when (direction) {
            "N" -> 0.0; "E" -> 90.0; "S" -> 180.0; "W" -> 270.0; else -> 0.0
        }

        val scanData = JSONObject().apply {
            put("label", entry.label)
            put("x", entry.x)
            put("y", entry.y)
            put("z", 1)
            put("heading", heading)
            put("timestamp", System.currentTimeMillis())
            val readings = JSONArray()
            lastScanResults.forEach {
                readings.put(JSONObject().apply {
                    put("bssid", it.BSSID)
                    put("rssi", it.level)
                })
            }
            put("wifi_data", readings)
        }

        val file = File(requireContext().getExternalFilesDir(null), "ips_scans.json")
        val array = if (file.exists()) JSONArray(file.readText()) else JSONArray()
        array.put(scanData)
        FileOutputStream(file).use { it.write(array.toString(4).toByteArray()) }

        Snackbar.make(requireView(), "Scan saved for ${entry.label}", Snackbar.LENGTH_LONG).show()
        moveToNextCoordinate(entry)
    }

    private fun moveToNextCoordinate(entry: CoordEntry) {
        val currentIndex = coordList.indexOf(entry)
        var nextIndex = currentIndex

        if (movingForward) {
            if (currentIndex < coordList.size - 1) nextIndex++
            else {
                movingForward = false; nextIndex--; directionIndex = (directionIndex + 1) % 4
                spinnerDirection.setSelection(directionIndex)
                Toast.makeText(requireContext(), "Reached end – reversing direction", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (currentIndex > 0) nextIndex--
            else {
                movingForward = true; nextIndex++; directionIndex = (directionIndex + 1) % 4
                spinnerDirection.setSelection(directionIndex)
                Toast.makeText(requireContext(), "Reached start – reversing direction", Toast.LENGTH_SHORT).show()
            }
        }

        selectedEntry = coordList[nextIndex]
        spinner.setSelection(nextIndex)
        txtCoords.text = "Coordinates: (x=${selectedEntry!!.x}, y=${selectedEntry!!.y}, z=1)"
    }

    private fun handleFlagClick() {
        flagCounter++
        val flagData = JSONObject().apply { put("flag", "flag$flagCounter") }
        val file = File(requireContext().getExternalFilesDir(null), "ips_scans.json")
        val array = if (file.exists()) JSONArray(file.readText()) else JSONArray()
        array.put(flagData)
        FileOutputStream(file).use { it.write(array.toString(4).toByteArray()) }
        Snackbar.make(requireView(), "Flag #$flagCounter saved", Snackbar.LENGTH_LONG).show()
    }
}

data class CoordEntry(val label: String, val x: Float, val y: Float)
