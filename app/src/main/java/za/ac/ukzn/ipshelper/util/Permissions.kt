package za.ac.ukzn.ipshelper.util

import android.Manifest
import android.os.Build

object Permissions {
    fun wifiPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        // ACCESS_FINE_LOCATION is required for Wi-Fi scan results on all modern versions.
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        // On Android 12 (API 31) and higher, NEARBY_WIFI_DEVICES is also required.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions.toTypedArray()
    }
}
