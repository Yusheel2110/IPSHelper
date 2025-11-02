package za.ac.ukzn.ipshelper.data.autoscan

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import za.ac.ukzn.ipshelper.data.pdr.PDRManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AutoScanManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private val pdr = PDRManager(context)

    private var running = false
    private var logArray = JSONArray()
    private var currentFile: File? = null

    private val scanInterval = 5000L // 5s

    fun startAutoScan() {
        if (running) return
        running = true
        logArray = JSONArray()

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val dir = File(context.getExternalFilesDir(null), "scans")
        if (!dir.exists()) dir.mkdirs()
        currentFile = File(dir, "scan_run_${timestamp}.json")

        pdr.start { x, y, heading ->
            // record PDR update (without Wi-Fi)
            val entry = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("x", x)
                put("y", y)
                put("z", 0)
                put("heading", heading)
                put("wifi_data", JSONArray())
            }
            logArray.put(entry)
        }

        handler.post(scanLoop)
        Log.d("AutoScan", "Auto-scan started, logging to ${currentFile?.absolutePath}")
    }

    fun stopAutoScan() {
        running = false
        pdr.stop()
        handler.removeCallbacks(scanLoop)

        try {
            FileOutputStream(currentFile!!).use {
                it.write(logArray.toString(4).toByteArray())
            }
            Log.d("AutoScan", "Saved ${logArray.length()} entries to ${currentFile!!.name}")
        } catch (e: Exception) {
            Log.e("AutoScan", "Error saving scan", e)
        }
    }

    private val scanLoop = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                wifiManager.startScan()
                val results = wifiManager.scanResults
                if (logArray.length() > 0) {
                    val last = logArray.getJSONObject(logArray.length() - 1)
                    val wifiArray = JSONArray()
                    for (r in results) {
                        val obj = JSONObject()
                        obj.put("bssid", r.BSSID)
                        obj.put("ssid", r.SSID)
                        obj.put("rssi", r.level)
                        wifiArray.put(obj)
                    }
                    last.put("wifi_data", wifiArray)
                }
            } catch (e: Exception) {
                Log.e("AutoScan", "Scan failed", e)
            }
            handler.postDelayed(this, scanInterval)
        }
    }
}
