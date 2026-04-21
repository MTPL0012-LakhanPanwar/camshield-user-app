package com.sierra.camblock.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sierra.camblock.R
import com.sierra.camblock.databinding.ActivityPermissionBinding
import com.sierra.camblock.utils.PermissionUtils
import com.sierra.camblock.utils.PrefsManager
import com.sierra.camblock.utils.applyDarkSystemBars

/**
 * Collects the two runtime permissions required for the blocker to work:
 *   1. System Alert Window (overlay)                 — unchanged.
 *   2. Accessibility Service (replaces Usage Stats)  — new.
 *
 * The XML still labels the first switch "Usage Stat" for historical reasons,
 * but we now bind it to the Accessibility permission so older screenshots /
 * layouts keep working without a full redesign.
 */
class PermissionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionBinding
    private lateinit var prefsManager: PrefsManager

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateAllSwitches()
        updateContinueButton()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyDarkSystemBars(R.color.parent_bg)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefsManager = PrefsManager(this)

        // Relabel the first permission card to match the new behaviour.
        binding.tvUsageStat.text = getString(R.string.permission_accessibility_title)

        setupSwitches()
        updateAllSwitches()
        updateContinueButton()

        binding.btnContinue.setOnClickListener {
            if (allPermissionsGranted()) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                val pendingPermissions = getPendingPermissionsList()
                Toast.makeText(
                    this,
                    "Please grant $pendingPermissions permission(s)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupSwitches() {
        binding.swUsageStat.setOnClickListener {
            if (hasAccessibilityPermission()) {
                binding.swUsageStat.isChecked = true
                updateContinueButton()
            } else {
                binding.swUsageStat.isChecked = false
                showAccessibilityRationale()
            }
        }

        binding.swOverlay.setOnClickListener {
            if (hasOverlayPermission()) {
                binding.swOverlay.isChecked = true
                updateContinueButton()
            } else {
                binding.swOverlay.isChecked = false
                requestOverlayPermission()
            }
        }
    }

    private fun updateAllSwitches() {
        binding.swUsageStat.isChecked = hasAccessibilityPermission()
        binding.swOverlay.isChecked = hasOverlayPermission()
    }

    private fun updateContinueButton() {
        val allGranted = allPermissionsGranted()
        binding.btnContinue.apply {
            isEnabled = allGranted
            backgroundTintList = ContextCompat.getColorStateList(
                this@PermissionActivity,
                if (allGranted) R.color.btn_blue else R.color.btn_disabled
            )
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return hasAccessibilityPermission() && hasOverlayPermission()
    }

    private fun getPendingPermissionsList(): String {
        val pending = mutableListOf<String>()
        if (!hasAccessibilityPermission()) pending.add("Accessibility")
        if (!hasOverlayPermission()) pending.add("Overlay")
        return pending.joinToString(", ")
    }

    private fun hasAccessibilityPermission(): Boolean =
        PermissionUtils.isAccessibilityServiceEnabled(this)

    private fun hasOverlayPermission(): Boolean =
        PermissionUtils.hasOverlayPermission(this)

    /**
     * Explain to the user *why* the Accessibility permission is needed
     * before we throw them into the system Settings screen. Google's Play
     * policy for non-assistive Accessibility use mandates this disclosure.
     */
    private fun showAccessibilityRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_accessibility_title)
            .setMessage(R.string.permission_accessibility_rationale)
            .setPositiveButton("Open Settings") { _, _ ->
                prefsManager.isAccessibilityPermit = true
                launchAccessibilitySettings()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun launchAccessibilitySettings() {
        val intent = PermissionUtils.buildAccessibilitySettingsIntent(this)
        try {
            settingsLauncher.launch(intent)
        } catch (e: Exception) {
            // Fallback to the generic Accessibility settings page if the
            // direct-to-service deep link is unsupported (some OEM skins).
            try {
                settingsLauncher.launch(
                    Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    "Could not open Accessibility Settings",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = PermissionUtils.buildOverlaySettingsIntent(this)
        settingsLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        updateAllSwitches()
        updateContinueButton()
    }
}