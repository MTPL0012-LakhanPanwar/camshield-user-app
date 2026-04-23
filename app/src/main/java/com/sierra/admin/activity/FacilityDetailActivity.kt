package com.sierra.admin.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.camshield.admin.ui.FacilityDetailScreen
import com.camshield.admin.ui.theme.CameraLockFacilityTheme
import com.camshield.admin.viewmodel.AuthViewModel
import com.camshield.admin.viewmodel.FacilityViewModel
import kotlin.jvm.java
import kotlin.run

class FacilityDetailActivity : ComponentActivity() {

    private val facilityViewModel: FacilityViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    companion object {
        const val EXTRA_FACILITY_ID = "facilityId"
        const val EXTRA_EDIT_FACILITY_ID = "editFacilityId"
        const val RESULT_EDIT = 1001
        const val RESULT_DELETED = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val facilityId = intent.getStringExtra(EXTRA_FACILITY_ID) ?: run {
            finish()
            return
        }

        setContent {
            CameraLockFacilityTheme {
                FacilityDetailScreen(
                    facilityId = facilityId,
                    viewModel = facilityViewModel,
                    onNavigateBack = { finish() },
                    onEditFacility = { editId ->
                        val resultIntent = Intent()
                        resultIntent.putExtra(EXTRA_EDIT_FACILITY_ID, editId)
                        setResult(RESULT_EDIT, resultIntent)
                        finish()
                    },
                    onDeleteSuccess = {
                        setResult(RESULT_DELETED)
                        finish()
                    },
                    onUnauthorized = {
                        authViewModel.logout()
                        val intent = Intent(this@FacilityDetailActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}


