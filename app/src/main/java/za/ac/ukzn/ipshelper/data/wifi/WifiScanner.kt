package za.ac.ukzn.ipshelper.data.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log

class WifiScanner(private val context: Context) {
    // Use a safe cast to prevent a crash if the Wi-Fi service is not available
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    @SuppressLint("MissingPermission")
    fun scanBlocking(): List<ScanResult> {
        // If the wifi manager is null, return an empty list immediately.
        if (wifi == null) {
            return emptyList()
        }

        return try {
            @Suppress("DEPRECATION")
            wifi.startScan()
            @Suppress("DEPRECATION")
            wifi.scanResults.orEmpty()
        } catch (e: SecurityException) {
            Log.e("WifiScanner", "Permission denied for Wi-Fi scan", e)
            emptyList()
        }
    }
}
