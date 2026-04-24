package com.camshield.admin.ui

import android.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdminSplashScreen() {
    // Darkened brand tones
    val brandBlue = Color(0xFF0F2D68) // dimmer blue
    val darkNavy = Color(0xFF0B101F)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. The Icon Container (Card equivalent)
            Surface(
                modifier = Modifier.size(150.dp),
                shape = RoundedCornerShape(30.dp),
                color = darkNavy, // Dark box behind the icon
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = com.sierra.camblock.R.drawable.admin),
                        contentDescription = "Security Icon",
                        modifier = Modifier.size(100.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 2. The Main Title
            Text(
                text = "SECURE ZONE",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. The Subtitle
            Text(
                text = "ADMIN ACCESS",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 4.sp // Mimics the spread look in your image
            )
        }
    }
}
@Preview(showBackground = true, device = Devices.PIXEL_7)
@Composable
fun AdminSplashPreview() {
    AdminSplashScreen()
}
