package com.kizvpn.client.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.kizvpn.client.ui.theme.BackgroundDark
import com.kizvpn.client.ui.theme.TextPrimary
import com.kizvpn.client.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScreen(
    onBack: () -> Unit,
    onConfigScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scannedConfig by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var hasCameraPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Необходимо разрешение на использование камеры", Toast.LENGTH_LONG).show()
        }
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TopAppBar(
            title = {
                Text(
                    "Сканировать QR-код",
                    color = TextPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Назад",
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundDark
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (hasCameraPermission) {
                // QR-сканер
                AndroidView(
                    factory = { ctx ->
                        val barcodeView = DecoratedBarcodeView(ctx)
                        val formats = listOf(BarcodeFormat.QR_CODE)
                        barcodeView.decoderFactory = DefaultDecoderFactory(formats)
                        
                        // Атомарный флаг для предотвращения повторной обработки
                        val processingRef = java.util.concurrent.atomic.AtomicBoolean(false)
                        
                        // Обработка результатов сканирования
                        barcodeView.decodeContinuous(object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult?) {
                                result?.let {
                                    val scannedText = it.text
                                    Log.d("QRCodeScreen", "QR-код отсканирован: ${scannedText?.take(50)}...")
                                    
                                    // Проверяем, что текст не пустой и обработка еще не началась
                                    if (scannedText != null && scannedText.isNotBlank()) {
                                        // Проверяем атомарно, не обрабатывается ли уже QR-код
                                        if (processingRef.compareAndSet(false, true)) {
                                            try {
                                                // Останавливаем сканирование
                                                barcodeView.pause()
                                                
                                                Log.d("QRCodeScreen", "Обрабатываем отсканированный QR-код")
                                                
                                                // Выполняем обработку в главном потоке через корутину
                                                scope.launch {
                                                    try {
                                                        // Показываем уведомление
                                                        Toast.makeText(
                                                            context,
                                                            "QR-код отсканирован!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        
                                                        // Сохраняем отсканированный конфиг
                                                        scannedConfig = scannedText
                                                        
                                                        // Вызываем callback для обработки конфига
                                                        Log.d("QRCodeScreen", "Вызываем onConfigScanned")
                                                        onConfigScanned(scannedText)
                                                        
                                                        // Даем время на обработку конфига перед закрытием
                                                        delay(800)
                                                        
                                                        // Закрываем экран
                                                        Log.d("QRCodeScreen", "Закрываем экран")
                                                        onBack()
                                                    } catch (e: Exception) {
                                                        Log.e("QRCodeScreen", "Ошибка при обработке QR-кода", e)
                                                        Toast.makeText(
                                                            context,
                                                            "Ошибка: ${e.message ?: "Неизвестная ошибка"}",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        
                                                        // Сбрасываем флаг и возобновляем сканирование
                                                        processingRef.set(false)
                                                        barcodeView.resume()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("QRCodeScreen", "Критическая ошибка при обработке QR-кода", e)
                                                Toast.makeText(
                                                    context,
                                                    "Критическая ошибка: ${e.message ?: "Неизвестная ошибка"}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                
                                                // Сбрасываем флаг и возобновляем сканирование
                                                processingRef.set(false)
                                                try {
                                                    barcodeView.resume()
                                                } catch (ex: Exception) {
                                                    Log.e("QRCodeScreen", "Не удалось возобновить сканирование", ex)
                                                }
                                            }
                                        } else {
                                            Log.d("QRCodeScreen", "QR-код уже обрабатывается, игнорируем повторное сканирование")
                                        }
                                    }
                                }
                            }
                            
                            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {
                                // Не требуется
                            }
                        })
                        
                        barcodeView.resume()
                        barcodeView
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // Обновление не требуется
                    },
                    onRelease = { view ->
                        try {
                            view.pause()
                        } catch (e: Exception) {
                            Log.e("QRCodeScreen", "Ошибка при освобождении камеры", e)
                        }
                    }
                )
            } else {
                // Сообщение, если нет разрешения
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Необходимо разрешение на использование камеры",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                        ) {
                            Text("Предоставить разрешение")
                        }
                    }
                }
            }
        }
        
        // Подсказка внизу
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Наведите камеру на QR-код",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

