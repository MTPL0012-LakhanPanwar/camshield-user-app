package com.sierra.camblock.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sierra.camblock.R
import com.sierra.camblock.databinding.ActivityPermissionRestoreBinding
import com.sierra.camblock.manager.DeviceAdminManager

class PermissionRestoreActivity : AppCompatActivity() {
    private lateinit var binding : ActivityPermissionRestoreBinding
    private lateinit var deviceAdminManager: DeviceAdminManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPermissionRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        deviceAdminManager = DeviceAdminManager(this)
        // Deactivate device admin
        if (deviceAdminManager.isDeviceAdminActive()) {
            deviceAdminManager.removeDeviceAdmin()
        }
        binding.iToolbar.toolbarTitle.text = "SECURITY CHECK"
        binding.iToolbar.btnBack.visibility = View.GONE
        binding.btnContinue.setOnClickListener {
            // Navigate to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}