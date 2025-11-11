package com.kizvpn.client.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * График трафика с волнообразной линией и анимацией набегающей линии
 */
@Composable
fun TrafficGraph(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF00E5FF), // Неоновый синий
    fillColor: Color = Color(0x3300E5FF), // Полупрозрачная заливка
    animatedProgress: Float = 1f
) {
    // Если данных нет, создаем минимальные данные для отображения
    val displayData = if (data.isEmpty() || data.all { it == 0f }) {
        List(60) { index -> (Math.sin(index / 10.0) * 50 + 100).toFloat() }
    } else {
        data
    }
    
    // Фиксированное максимальное значение: 1000 KB/s для отображения на графике
    // Данные приходят в KB/s, поэтому максимальное значение 1000 KB/s (~1 MB/s) - это нормально
    // Но для лучшей визуализации используем динамическое масштабирование
    val maxValue = 1000f // Фиксированный максимум 1000 KB/s для меток
    val minValue = 0f
    val range = maxValue - minValue
    
    // Для лучшей визуализации: используем динамический масштаб на основе реальных данных
    val dataMax = displayData.maxOrNull() ?: 0f
    val dataMin = displayData.minOrNull() ?: 0f
    
    // Вычисляем эффективный максимум для масштабирования
    val effectiveMaxValue = when {
        // Если все данные очень маленькие (менее 50 KB/s), используем максимум 100 KB/s
        dataMax < 50f -> maxOf(dataMax * 2f, 50f)
        // Если данные маленькие (менее 200 KB/s), используем максимум 250 KB/s
        dataMax < 200f -> maxOf(dataMax * 1.5f, 100f)
        // Если данные средние (менее 500 KB/s), используем максимум 600 KB/s
        dataMax < 500f -> maxOf(dataMax * 1.3f, 250f)
        // Если данные большие, но меньше 800 KB/s, используем максимум 1000 KB/s
        dataMax < 800f -> maxOf(dataMax * 1.2f, 600f)
        // Если данные очень большие, используем фиксированный максимум 1000 KB/s
        else -> maxValue
    }
    val effectiveRange = effectiveMaxValue - minValue
    
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    
    // Подготавливаем метки заранее (вне Canvas)
    // Метки: 1000, 700, 500, 100, 50, 0 (сверху вниз)
    val gridSteps = 5
    // Создаём метки в нужном порядке
    val labelValues = listOf(1000, 700, 500, 100, 50, 0)
    val labelTexts = labelValues.map { if (it == 1000) "1000" else "$it" }
    val labelTextStyles = labelTexts.map { text ->
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                color = labelColor,
                fontSize = 9.sp // Уменьшен размер шрифта для предотвращения наложения
            )
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height
            val padding = 2.dp.toPx() // Минимальные отступы для максимальной ширины
            val leftLabelPadding = 20.dp.toPx() // Минимальное место слева для меток
            val graphStartX = padding + leftLabelPadding // Начало графика с учетом места для меток
            val rightPadding = padding + leftLabelPadding // Симметричный отступ справа (равный левому)
            
            // Вычисляем точки для волнообразной линии
            val pointCount = displayData.size
            val graphWidth = width - graphStartX - rightPadding // Ширина области графика (симметричная)
            val stepX = graphWidth / (pointCount - 1)
            
            val points = mutableListOf<Offset>()
            for (i in displayData.indices) {
                val x = graphStartX + i * stepX
                // Нормализуем значение используя effectiveMaxValue для лучшей визуализации
                // В Canvas Y=0 вверху, Y увеличивается вниз
                // Поэтому: большое значение → y вверху (padding), значение 0 → y внизу (height-padding)
                val normalizedValue = if (effectiveRange > 0) {
                    ((displayData[i] - minValue) / effectiveRange).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val y = padding + (height - padding * 2) * (1f - normalizedValue)
                points.add(Offset(x, y))
            }
            
            // Создаем путь для волнообразной линии с плавным сглаживанием
            val path = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points[0].x, points[0].y)
                    
                    // Используем улучшенные кубические кривые Безье для плавной волны
                    for (i in 0 until points.size - 1) {
                        val current = points[i]
                        val next = points[i + 1]
                        
                        // Вычисляем предыдущую и следующую точки для более плавного сглаживания
                        val prev = if (i > 0) points[i - 1] else current
                        val nextNext = if (i < points.size - 2) points[i + 2] else next
                        
                        // Улучшенные контрольные точки с учетом соседних точек (более плавное сглаживание)
                        val dx = next.x - current.x
                        val dy = next.y - current.y
                        
                        // Первая контрольная точка - более плавные переходы (0.3 для плавности)
                        val controlX1 = current.x + dx * 0.3f + (current.x - prev.x) * 0.1f
                        val controlY1 = current.y + dy * 0.3f + (current.y - prev.y) * 0.1f
                        
                        // Вторая контрольная точка - более плавные переходы
                        val controlX2 = next.x - dx * 0.3f - (nextNext.x - next.x) * 0.1f
                        val controlY2 = next.y - dy * 0.3f - (nextNext.y - next.y) * 0.1f
                        
                        cubicTo(
                            controlX1, controlY1,
                            controlX2, controlY2,
                            next.x, next.y
                        )
                    }
                }
            }
            
            // Отрисовываем заливку под линией (градиент)
            val fillPath = Path().apply {
                // Копируем путь
                addPath(path)
                lineTo(points.last().x, height - padding)
                lineTo(points.first().x, height - padding)
                close()
            }
            
            // Применяем анимацию прогресса (набегающая линия) с плавным сглаживанием
            val animatedPath = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points[0].x, points[0].y)
                    
                    val stopIndex = (points.size * animatedProgress).toInt().coerceAtMost(points.size - 1)
                    
                    for (i in 0 until stopIndex) {
                        val current = points[i]
                        val next = points[i + 1]
                        
                        // Вычисляем предыдущую и следующую точки для более плавного сглаживания
                        val prev = if (i > 0) points[i - 1] else current
                        val nextNext = if (i < points.size - 2) points[i + 2] else next
                        
                        // Улучшенные контрольные точки с учетом соседних точек (более плавное сглаживание)
                        val dx = next.x - current.x
                        val dy = next.y - current.y
                        
                        // Первая контрольная точка - более плавные переходы (0.3 для плавности)
                        val controlX1 = current.x + dx * 0.3f + (current.x - prev.x) * 0.1f
                        val controlY1 = current.y + dy * 0.3f + (current.y - prev.y) * 0.1f
                        
                        // Вторая контрольная точка - более плавные переходы
                        val controlX2 = next.x - dx * 0.3f - (nextNext.x - next.x) * 0.1f
                        val controlY2 = next.y - dy * 0.3f - (nextNext.y - next.y) * 0.1f
                        
                        cubicTo(
                            controlX1, controlY1,
                            controlX2, controlY2,
                            next.x, next.y
                        )
                    }
                    
                    // Плавная интерполяция последней точки с использованием кривой Безье
                    if (stopIndex < points.size - 1) {
                        val current = points[stopIndex]
                        val next = points[stopIndex + 1]
                        val progress = (animatedProgress * points.size - stopIndex).coerceIn(0f, 1f)
                        
                               // Используем плавную интерполяцию с учетом кривой
                               val prev = if (stopIndex > 0) points[stopIndex - 1] else current
                               val dx = next.x - current.x
                               val dy = next.y - current.y
                               
                               val controlX1 = current.x + dx * 0.3f + (current.x - prev.x) * 0.1f
                               val controlY1 = current.y + dy * 0.3f + (current.y - prev.y) * 0.1f
                        
                        // Кубическая интерполяция для плавности
                        val t = progress
                        val t2 = t * t
                        val t3 = t2 * t
                        val mt = 1 - t
                        val mt2 = mt * mt
                        val mt3 = mt2 * mt
                        
                        val x = mt3 * current.x + 3 * mt2 * t * controlX1 + 3 * mt * t2 * next.x + t3 * next.x
                        val y = mt3 * current.y + 3 * mt2 * t * controlY1 + 3 * mt * t2 * next.y + t3 * next.y
                        
                        lineTo(x, y)
                    }
                }
            }
            
            // Отрисовываем сетку (горизонтальные линии) и метки слева
            val gridLineWidth = 1.dp.toPx()
            
            // Метки: 1000, 700, 500, 100, 50, 0 (сверху вниз)
            // Нормализуем значения для отображения (0..1000)
            val labelValuesList = listOf(1000, 700, 500, 100, 50, 0)
            val normalizedValues = labelValuesList.map { it.toFloat() / maxValue }
            
            for (i in 0..gridSteps) {
                // Вычисляем Y позицию на основе нормализованного значения
                // 0 → внизу (height - padding), 1 → вверху (padding)
                val normalizedValue = normalizedValues[i]
                val y = padding + (height - padding * 2) * (1f - normalizedValue)
                
                // Рисуем горизонтальную линию сетки (от начала графика до правого края)
                drawLine(
                    color = gridLineColor,
                    start = Offset(graphStartX, y),
                    end = Offset(width - rightPadding, y),
                    strokeWidth = gridLineWidth
                )
                
                // Добавляем метку слева с проверкой на перекрытие
                val textLayoutResult = labelTextStyles[i]
                val textHeight = textLayoutResult.size.height
                val minSpacing = textHeight * 1.5f // Увеличено до 150% высоты текста для лучшего разделения
                
                // Вычисляем позицию Y для метки с учетом соседних меток
                var labelY = y - textHeight / 2
                
                // Проверяем и корректируем позицию, чтобы избежать наложения
                // Для меток 100 и 50 нужен дополнительный отступ
                if (i > 0) {
                    val prevNormalizedValue = normalizedValues[i - 1]
                    val prevY = padding + (height - padding * 2) * (1f - prevNormalizedValue)
                    val minY = prevY - textHeight / 2 + minSpacing
                    // Дополнительный отступ для меток 100 и 50 (они очень близко)
                    val extraSpacing = if ((labelValuesList[i] == 100 && labelValuesList[i - 1] == 500) || 
                                           (labelValuesList[i] == 50 && labelValuesList[i - 1] == 100)) {
                        textHeight * 0.5f // Дополнительные 50% высоты текста
                    } else {
                        0f
                    }
                    if (labelY < minY + extraSpacing) {
                        labelY = minY + extraSpacing
                    }
                }
                
                // Также проверяем следующую метку
                if (i < gridSteps) {
                    val nextNormalizedValue = normalizedValues[i + 1]
                    val nextY = padding + (height - padding * 2) * (1f - nextNormalizedValue)
                    val maxY = nextY - textHeight / 2 - minSpacing
                    // Дополнительный отступ для меток 100 и 50
                    val extraSpacing = if ((labelValuesList[i] == 500 && labelValuesList[i + 1] == 100) || 
                                           (labelValuesList[i] == 100 && labelValuesList[i + 1] == 50)) {
                        textHeight * 0.5f // Дополнительные 50% высоты текста
                    } else {
                        0f
                    }
                    if (labelY > maxY - extraSpacing) {
                        labelY = maxY - extraSpacing
                    }
                }
                
                // Ограничиваем позицию метки в пределах графика
                labelY = labelY.coerceIn(padding + textHeight, height - padding - textHeight)
                
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        graphStartX - textLayoutResult.size.width - 4.dp.toPx(), // Минимальный отступ слева от линии графика
                        labelY
                    ),
                    color = labelColor
                )
            }
            
            // Отрисовываем заливку с градиентом
            val gradient = Brush.verticalGradient(
                colors = listOf(
                    fillColor.copy(alpha = 0.3f),
                    fillColor.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                startY = padding,
                endY = height - padding
            )
            
            drawPath(
                path = fillPath,
                brush = gradient,
                blendMode = BlendMode.Screen
            )
            
            // Отрисовываем волнообразную линию с анимацией
            drawPath(
                path = animatedPath,
                color = lineColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            
            // Добавляем свечение (glow effect) для неонового эффекта
            drawPath(
                path = animatedPath,
                color = lineColor.copy(alpha = 0.3f),
                style = Stroke(
                    width = 8.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                blendMode = BlendMode.Screen
            )
        }
    }
}

