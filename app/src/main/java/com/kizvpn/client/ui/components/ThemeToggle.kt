package com.kizvpn.client.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.kizvpn.client.ui.theme.NeonPrimary

/**
 * Кнопка переключения темы в стиле Apple (iOS toggle)
 */
@Composable
fun ThemeToggle(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDark) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "theme_toggle"
    )
    
    val backgroundColor = androidx.compose.ui.graphics.lerp(
        Color(0xFFE0E0E0), // Светлый цвет
        Color(0xFF6A00FF), // Темный цвет (неоновый фиолетовый)
        animatedProgress
    )
    
    val iconX = lerp(1.5.dp, 15.dp, animatedProgress)
    
    Box(
        modifier = modifier
            .width(36.dp)  // Уменьшено на 30% (52 * 0.7 ≈ 36)
            .height(22.dp)  // Уменьшено на 30% (32 * 0.7 ≈ 22)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onToggle() }
            .shadow(3.dp, shape = CircleShape, spotColor = if (isDark) NeonPrimary else Color.Gray)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = iconX, y = 0.dp)
                .size(20.dp)  // Уменьшено на 30% (28 * 0.7 ≈ 20)
                .clip(CircleShape)
                .background(Color.White)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = if (isDark) "Dark mode" else "Light mode",
                tint = if (isDark) NeonPrimary else Color(0xFFFFA726),
                modifier = Modifier.size(12.dp)  // Уменьшено на 30% (16 * 0.7 ≈ 12)
            )
        }
    }
}

