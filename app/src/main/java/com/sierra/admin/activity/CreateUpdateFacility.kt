package com.sierra.admin.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.camshield.admin.ui.CreateUpdateScreen
import com.camshield.admin.ui.theme.CameraLockFacilityTheme
import com.camshield.admin.viewmodel.FacilityViewModel

class CreateUpdateFacility : ComponentActivity() {

    private val facilityViewModel: FacilityViewModel by viewModels()

    companion object {
        const val EXTRA_FACILITY_ID = "facilityId"
        const val RESULT_SAVED = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val facilityId = intent.getStringExtra(EXTRA_FACILITY_ID)

        setContent {
            CameraLockFacilityTheme {
                CreateUpdateScreen(
                    facilityId = facilityId,
                    viewModel = facilityViewModel,
                    onNavigateBack = { finish() },
                    onSaveSuccess = {
                        setResult(RESULT_SAVED)
                        finish()
                    }
                )
            }
        }
    }
}
