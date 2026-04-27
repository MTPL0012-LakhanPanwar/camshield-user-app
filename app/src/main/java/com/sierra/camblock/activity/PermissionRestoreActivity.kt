package com.sierra.camblock.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.sierra.camblock.CameraBlockerService
import com.sierra.camblock.R
import com.sierra.camblock.api.RetrofitClient
import com.sierra.camblock.api.models.CompleteForceExitRequest
import com.sierra.camblock.databinding.ActivityPermissionRestoreBinding
import com.sierra.camblock.manager.DeviceAdminManager
import com.sierra.camblock.utils.DeviceUtils
import com.sierra.camblock.utils.PrefsManager
import com.sierra.camblock.utils.applyDarkSystemBars
import kotlinx.coroutines.launch

class PermissionRestoreActivity : AppCompatActivity() {
    private lateinit var binding : ActivityPermissionRestoreBinding
    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var prefsManager: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPermissionRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyDarkSystemBars(R.color.parent_bg)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        deviceAdminManager = DeviceAdminManager(this)
        prefsManager = PrefsManager(this)

        // Check if coming from notification click
        val notificationData = intent.getSerializableExtra("notification_data") as? HashMap<String, String>
        val isFromNotification = notificationData != null

        if (isFromNotification) {
            // Coming from notification - perform unlock and restore
            val restoreToken = notificationData?.get("token")
            if (restoreToken != null) {
                performCompleteForceExit(restoreToken)
            }
            unlockAndRemoveAdmin()
        } else {
            // Normal flow - just deactivate device admin
            if (deviceAdminManager.isDeviceAdminActive()) {
                deviceAdminManager.removeDeviceAdmin()
            }
        }

        binding.iToolbar.toolbarTitle.text = "SECURITY CHECK"
        binding.iToolbar.btnBack.visibility = View.GONE
        binding.btnContinue.setOnClickListener {
            // Navigate to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun unlockAndRemoveAdmin() {
        if (DeviceUtils.isTargetedXiaomiVersion()){
            stopLockTask()
        }
        prefsManager.isLocked = false
        prefsManager.activeVisitorId = ""
        stopService(Intent(this, CameraBlockerService::class.java))

        if (deviceAdminManager.isDeviceAdminActive()) {
            deviceAdminManager.removeDeviceAdmin()
        }    }

    private fun performCompleteForceExit(restoreToken: String) {
        val deviceId = DeviceUtils.getDeviceId(this)
        val request = CompleteForceExitRequest(
            token = restoreToken,
            deviceId = deviceId
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.completeForceExit(request)
                if (response.isSuccessful && response.body()?.status == "success") {
                    // Force exit completed successfully
                    Log.e(javaClass.name, " : ${response.body()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}