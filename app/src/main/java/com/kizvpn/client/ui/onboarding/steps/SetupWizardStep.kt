package com.kizvpn.client.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizvpn.client.R
import com.kizvpn.client.util.LocalizationHelper

@Composable
fun SetupWizardStep(
    onStart: () -> Unit,
    onSkipAll: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Иконка настроек (белая)
        Image(
            painter = painterResource(id = R.drawable.ic_menu_settings),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
        
        // Заголовок
        Text(
            text = LocalizationHelper.getString(context, R.string.setup_wizard_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = LocalizationHelper.getString(context, R.string.setup_wizard_description),
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Кнопки выбора
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Кнопка "Начать"
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B35),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = LocalizationHelper.getString(context, R.string.start_setup),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Кнопка "Пропустить все"
            OutlinedButton(
                onClick = onSkipAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = LocalizationHelper.getString(context, R.string.skip_all),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}