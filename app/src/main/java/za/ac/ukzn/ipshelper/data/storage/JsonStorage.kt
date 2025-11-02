package za.ac.ukzn.ipshelper.data.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonStorage(private val context: Context) {

    private val fileName = "exported_data.json"

    fun saveFingerprint(label: String, x: Float, y: Float, z: Int, wifiList: List<Map<String, Any>>) {
        val file = File(context.filesDir, fileName)
        val jsonObject = JSONObject().apply {
            put("label", label)
            put("x", x)
            put("y", y)
            put("z", z)
            put("wifi_data", JSONArray(wifiList))
        }
        val jsonArray = readAll()
        jsonArray.put(jsonObject)
        file.writeText(jsonArray.toString(4))
    }

    fun appendEvent(event: JSONObject) {
        val file = File(context.filesDir, fileName)
        val array = readAll()
        array.put(event)
        file.writeText(array.toString(4))
    }

    fun removeEvent(event: JSONObject) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            for (i in array.length() - 1 downTo 0) {
                val obj = array.getJSONObject(i)
                if (obj.optLong("timestamp") == event.optLong("timestamp")) {
                    array.remove(i)
                    break
                }
            }
            file.writeText(array.toString(4))
        } catch (_: Exception) { }
    }

    fun readAll(): JSONArray {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return JSONArray()
        return try {
            val text = file.readText()
            if (text.trim().startsWith("{")) JSONArray().apply { put(JSONObject(text)) }
            else JSONArray(text)
        } catch (_: Exception) { JSONArray() }
    }

    fun clearAll() {
        val file = File(context.filesDir, fileName)
        if (file.exists()) file.delete()
    }
}
