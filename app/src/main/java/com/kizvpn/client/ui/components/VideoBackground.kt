package com.kizvpn.client.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kizvpn.client.R

@Composable
fun VideoBackground(
    modifier: Modifier = Modifier,
    videoResId: Int = R.raw.video_9999, // Имя файла video_9999.mp4
    loop: Boolean = true,
    alpha: Float = 1f,
    isPlaying: Boolean = true, // Управление воспроизведением
    scale: Float = 0.8f // Масштаб видео (0.8 = 80% от исходного размера)
) {
    val context = LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val videoUri = Uri.parse("android.resource://${context.packageName}/${videoResId}")
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = true // Всегда воспроизводим с самого начала
            prepare()
        }
    }
    
    // Управление воспроизведением - всегда воспроизводим
    LaunchedEffect(Unit) {
        exoPlayer.playWhenReady = true
    }
    
    // Также обновляем при изменении isPlaying (для совместимости)
    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = true // Всегда воспроизводим, независимо от статуса
    }
    
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Скрываем элементы управления
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Заполняет весь экран с обрезкой
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .scale(scale) // Уменьшаем масштаб видео
        )
    }
}

