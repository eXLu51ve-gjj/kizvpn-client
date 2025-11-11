package com.kizvpn.client.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kizvpn.client.R

@Composable
fun VideoButton(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    videoResId: Int = R.raw.kiz_vpn_final_sferka,
    loop: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    clickableSize: androidx.compose.ui.unit.Dp? = null // Если null, используется size
) {
    val context = LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = true
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
    }
    
    // Инициализируем и обновляем видео при изменении videoResId
    LaunchedEffect(videoResId) {
        val videoUri = Uri.parse("android.resource://${context.packageName}/${videoResId}")
        val mediaItem = MediaItem.fromUri(videoUri)
        
        // Проверяем, есть ли уже видео в плейлисте
        if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.stop() // Останавливаем текущее видео
            exoPlayer.clearMediaItems() // Очищаем плейлист
        }
        
        exoPlayer.setMediaItem(mediaItem) // Устанавливаем новое видео
        exoPlayer.prepare() // Подготавливаем новое видео
        exoPlayer.playWhenReady = true // Запускаем воспроизведение
    }
    
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Box(
        modifier = modifier.size(size), // Размер контейнера = размер видео
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        // Видео на весь размер контейнера
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Кликабельная область поверх видео (круглая, центрирована)
        Box(
            modifier = Modifier
                .size(clickableSize ?: size)
                .align(androidx.compose.ui.Alignment.Center)
                .clip(androidx.compose.foundation.shape.CircleShape) // Делаем круглой
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
        )
    }
}

