package za.ac.ukzn.ipshelper.ui.main

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import za.ac.ukzn.ipshelper.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("DEBUG_MAIN", "onCreate() started")
        setContentView(R.layout.activity_main)
        Log.d("DEBUG_MAIN", "setContentView done")
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        Log.d("DEBUG_MAIN", "onPostCreate() called")

        val navController = findNavController(R.id.nav_host_fragment)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        bottomNav.setupWithNavController(navController)
        Log.d("DEBUG_MAIN", "BottomNav linked to NavController successfully")
    }
}
