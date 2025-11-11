package com.kizvpn.client.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.*
import androidx.compose.ui.tooling.preview.Preview
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.*

/**
 * Кнопка закрытия модального окна с эффектами глубины
 */
@Composable
fun CloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .size(width = 100.dp, height = 40.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false
            )
            .graphicsLayer {
                translationY = -2.dp.toPx() // Легкий подъем
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, Color(0x20FFFFFF))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Закрыть",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Модальное окно с графиком сетевой статистики
 */
@Composable
fun NetworkStatsModal(
    showStats: Boolean,
    onDismiss: () -> Unit,
    downloadData: List<Point>,
    uploadData: List<Point>,
    currentDownloadSpeed: Float,
    currentUploadSpeed: Float,
    totalDownloaded: String,
    totalUploaded: String,
    sessionTime: String
) {
    if (showStats) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10000f) // Поверх всех кнопок
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp), // Изначальный padding
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F24)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Заголовок без логотипа
                        Text(
                            text = "Статистика сети",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Время сессии
                        Text(
                            text = "Сессия: $sessionTime",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0x80FFFFFF),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Карточка с графиком
                        NetworkChartCard(
                            downloadData = downloadData,
                            uploadData = uploadData,
                            currentDownloadSpeed = currentDownloadSpeed,
                            currentUploadSpeed = currentUploadSpeed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp) // Изначальная высота графика
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp)) // Больший отступ для эффекта глубины
                        
                        // Статистика - "дальние" элементы
                        NetworkStatsRow(
                            downloaded = totalDownloaded,
                            uploaded = totalUploaded
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp)) // Фиксированный отступ
                        
                        // Логотип KIZ VPN (увеличенный в 2 раза, надпись под логотипом)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = com.kizvpn.client.R.drawable.kiz_vpntop),
                                contentDescription = "KIZ VPN",
                                modifier = Modifier.size(96.dp) // Увеличен в 2 раза (48.dp → 96.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "KIZ VPN",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(80.dp)) // Отступ для кнопки закрытия
                    }
                    
                    // Кнопка закрытия жестко закреплена к низу модального окна
                    CloseButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Карточка с графиком
 */
@Composable
fun NetworkChartCard(
    downloadData: List<Point>,
    uploadData: List<Point>,
    currentDownloadSpeed: Float,
    currentUploadSpeed: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .zIndex(3f) // Высокий уровень для "близкого" элемента
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .graphicsLayer {
                translationY = -4.dp.toPx() // Легкий подъем
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1012)), // Фиолетовый фон
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        border = BorderStroke(1.dp, Color(0x15FFFFFF)) // Тонкая граница для глубины
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp) // Изначальный padding
        ) {
            // Легенда и текущая скорость
            ChartLegend(currentDownloadSpeed, currentUploadSpeed)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // График
            if (downloadData.isNotEmpty() && uploadData.isNotEmpty()) {
                NetworkChart(
                    downloadData = downloadData,
                    uploadData = uploadData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет данных о сети",
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Легенда графика
 */
@Composable
fun ChartLegend(
    currentDownloadSpeed: Float,
    currentUploadSpeed: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Легенда скачивания
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color = Color(0xFF4FC3F7), shape = CircleShape)
            )
            Text(
                text = "Скачивание",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x80FFFFFF),
                modifier = Modifier.padding(start = 8.dp, end = 16.dp)
            )
        }
        
        // Легенда отдачи
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color = Color(0xFFFF9800), shape = CircleShape)
            )
            Text(
                text = "Отдача",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x80FFFFFF),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Текущая скорость
        Text(
            text = "↓ ${currentDownloadSpeed.toInt()} Kbps ↑ ${currentUploadSpeed.toInt()} Kbps",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4CAF50),
            fontSize = 11.sp
        )
    }
}

/**
 * График сети с YCharts
 */
@Composable
fun NetworkChart(
    downloadData: List<Point>,
    uploadData: List<Point>,
    modifier: Modifier = Modifier
) {
    val xAxisData = AxisData.Builder()
        .axisStepSize(30.dp) // Изначальный шаг
        .backgroundColor(Color.Transparent)
        .steps(downloadData.size - 1) // Изначальное количество делений
        .labelData { i -> "${i}с" } // Изначальные метки
        .labelAndAxisLinePadding(15.dp)
        .axisLineColor(Color(0x1AFFFFFF))
        .axisLabelColor(Color(0x80FFFFFF))
        .build()
    
    val yAxisData = AxisData.Builder()
        .steps(5)
        .backgroundColor(Color.Transparent)
        .labelAndAxisLinePadding(20.dp)
        .labelData { i -> "${(i * 200)}Kb" }
        .axisLineColor(Color.Transparent)
        .axisLabelColor(Color(0x80FFFFFF))
        .build()
    
    val lineChartData = LineChartData(
        linePlotData = LinePlotData(
            lines = listOf(
                // Линия скачивания
                Line(
                    dataPoints = downloadData,
                    LineStyle(
                        color = Color(0xFF4FC3F7),
                        lineType = LineType.SmoothCurve(isDotted = false),
                        width = 2f // Тоньше для более изящного вида
                    ),
                    IntersectionPoint(
                        color = Color(0xFF4FC3F7),
                        radius = 0.75.dp // Уменьшены ещё в 2 раза
                    ),
                    SelectionHighlightPoint(
                        color = Color(0xFF4FC3F7)
                    ),
                    ShadowUnderLine(
                        alpha = 0.3f,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x504FC3F7),
                                Color(0x004FC3F7)
                            )
                        )
                    ),
                    SelectionHighlightPopUp()
                ),
                // Линия отдачи
                Line(
                    dataPoints = uploadData,
                    LineStyle(
                        color = Color(0xFFFF9800),
                        lineType = LineType.SmoothCurve(isDotted = false),
                        width = 2f // Тоньше для более изящного вида
                    ),
                    IntersectionPoint(
                        color = Color(0xFFFF9800),
                        radius = 0.75.dp // Уменьшены ещё в 2 раза
                    ),
                    SelectionHighlightPoint(
                        color = Color(0xFFFF9800)
                    ),
                    ShadowUnderLine(
                        alpha = 0.3f,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x50FF9800),
                                Color(0x00FF9800)
                            )
                        )
                    ),
                    SelectionHighlightPopUp()
                )
            )
        ),
        xAxisData = xAxisData,
        yAxisData = yAxisData,
        gridLines = GridLines(color = Color(0x1AFFFFFF)),
        backgroundColor = Color.Transparent
    )
    
    LineChart(
        modifier = modifier,
        lineChartData = lineChartData
    )
}

/**
 * Строка со статистикой (скачано/отдано)
 */
@Composable
fun NetworkStatsRow(
    downloaded: String,
    uploaded: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(0f), // Самый низкий уровень
        horizontalArrangement = Arrangement.spacedBy(12.dp) // Больше пространства для эффекта глубины
    ) {
        // Скачано
        StatCard(
            value = downloaded,
            label = "Скачано",
            color = Color(0xFF4FC3F7),
            modifier = Modifier.weight(1f)
        )
        
        // Отдано
        StatCard(
            value = uploaded,
            label = "Отдано",
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Карточка статистики
 */
@Composable
fun StatCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .zIndex(2f) // Средний уровень - дальше чем график
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                clip = false
            )
            .graphicsLayer {
                alpha = 0.98f // Легкая прозрачность
                translationY = 2.dp.toPx() // Легкое опускание
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1012)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(0.5.dp, Color(0x10FFFFFF)) // Очень тонкая граница
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x80FFFFFF),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Preview для NetworkStatsModal
 */
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun NetworkStatsModalPreview() {
    // Создаём тестовые данные для графика
    val testDownloadData = List(15) { i ->
        Point(i.toFloat(), (300 + Math.random() * 400).toFloat())
    }
    val testUploadData = List(15) { i ->
        Point(i.toFloat(), (200 + Math.random() * 300).toFloat())
    }
    
    NetworkStatsModal(
        showStats = true,
        onDismiss = {},
        downloadData = testDownloadData,
        uploadData = testUploadData,
        currentDownloadSpeed = 450f,
        currentUploadSpeed = 320f,
        totalDownloaded = "42,6 MB",
        totalUploaded = "16,7 MB",
        sessionTime = "2м 30с"
    )
}

