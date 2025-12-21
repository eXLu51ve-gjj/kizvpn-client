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
fun WelcomeLanguageStep(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Иконка языка (белая)
        Image(
            painter = painterResource(id = R.drawable.ic_settings_language),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
        
        // Заголовок
        Text(
            text = LocalizationHelper.getString(context, R.string.welcome_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = LocalizationHelper.getString(context, R.string.choose_language),
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Кнопки выбора языка
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Русский язык
            LanguageButton(
                text = "Русский",
                isSelected = selectedLanguage == "ru",
                onClick = { 
                    onLanguageSelected("ru")
                }
            )
            
            // English
            LanguageButton(
                text = "English",
                isSelected = selectedLanguage == "en",
                onClick = { 
                    onLanguageSelected("en")
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Кнопка "Далее" - показываем только если язык выбран
        if (selectedLanguage.isNotEmpty()) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = LocalizationHelper.getString(context, R.string.continue_text),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun LanguageButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) Color(0xFFFF6B35) else Color.Transparent,
            contentColor = Color.White
        ),
        border = BorderStroke(
            width = 2.dp,
            color = if (isSelected) Color(0xFFFF6B35) else Color.White.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}