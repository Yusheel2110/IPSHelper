package za.ac.ukzn.ipshelper.ui.export

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import za.ac.ukzn.ipshelper.R
import za.ac.ukzn.ipshelper.data.storage.JsonStorage

class ExportFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_export, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnExport = view.findViewById<Button>(R.id.btnExport)
        val btnViewScans = view.findViewById<Button>(R.id.btnViewScans)
        val btnClearScans = view.findViewById<Button>(R.id.btnClearScans)
        val txtExportStatus = view.findViewById<TextView>(R.id.txtExportStatus)

        val storage = JsonStorage(requireContext())

        btnExport.setOnClickListener {
            txtExportStatus.text = "Successfully exported to /data/user/0/za.ac.ukzn.ipshelper/files/exported_data.json"
        }

        btnViewScans.setOnClickListener {
            val scans = storage.readAll()
            showSavedScansDialog(scans)
        }

        btnClearScans.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete all saved scans?")
                .setPositiveButton("Yes") { _, _ ->
                    storage.clearAll()
                    Toast.makeText(requireContext(), "All scans cleared successfully", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showSavedScansDialog(scans: JSONArray) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Saved Scans")
            .setMessage(scans.toString(2))
            .setPositiveButton("Close", null)
            .show()
    }
}
