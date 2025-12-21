package com.kizvpn.client.ui.onboarding.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Современный красивый переключатель в стиле iOS для onboarding
 */
@Composable
fun ModernToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabledColor: Color = Color(0xFF9C27B0), // Фиолетовый
    disabledColor: Color = Color(0xFF424242), // Темно-серый
    thumbColor: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    // Анимация для позиции переключателя
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumb_offset"
    )
    
    // Анимация для цвета трека
    val trackColor by animateColorAsState(
        targetValue = if (checked) enabledColor else disabledColor,
        animationSpec = tween(300),
        label = "track_color"
    )
    
    Box(
        modifier = modifier
            .width(56.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null // Убираем ripple эффект
            ) {
                onCheckedChange(!checked)
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Переключатель (thumb) с символом
        Box(
            modifier = Modifier
                .size(28.dp)
                .offset(x = (24.dp * thumbOffset)) // Двигаем переключатель
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape
                )
                .background(
                    color = thumbColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Символ в центре переключателя
            Text(
                text = if (checked) "I" else "O",
                color = if (checked) enabledColor else disabledColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}