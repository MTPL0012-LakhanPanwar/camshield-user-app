package com.sierra.camblock.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sierra.camblock.R
import com.sierra.camblock.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {
    private lateinit var binding : ActivityHelpBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupClickListeners()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    
    private fun setupClickListeners() {
        binding.iToolbar.toolbarTitle.text = "Help & Guide"
        binding.iToolbar.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}