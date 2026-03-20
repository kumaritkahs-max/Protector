package com.filevault.pro.presentation.screen.videoplayer

import android.content.ComponentName
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.filevault.pro.service.MediaPlaybackService
import com.filevault.pro.util.FileUtils
import com.filevault.pro.util.MediaQueue
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import java.io.File
import java.net.URLDecoder

@Composable
fun VideoPlayerScreen(
    path: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val decodedPath = remember(path) { try { URLDecoder.decode(path, "UTF-8") } catch (_: Exception) { path } }

    var currentPlayPath by remember { mutableStateOf(decodedPath) }
    val fileName = remember(currentPlayPath) { File(currentPlayPath).name }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isFitToScreen by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(1f) }

    val hasPrev = MediaQueue.hasPrev()
    val hasNext = MediaQueue.hasNext()
    val queueSize = MediaQueue.filePaths.size
    val queuePos = MediaQueue.currentIndex + 1

    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MediaPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get().apply {
                val uri = if (currentPlayPath.startsWith("content://")) Uri.parse(currentPlayPath)
                          else Uri.fromFile(File(currentPlayPath))
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        }, MoreExecutors.directExecutor())
    }

    LaunchedEffect(controller) {
        val ctrl = controller ?: return@LaunchedEffect
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> duration = ctrl.duration.coerceAtLeast(0L)
                    Player.STATE_ENDED -> {
                        val next = MediaQueue.goNext()
                        if (next != null) currentPlayPath = next
                    }
                }
            }
        }
        ctrl.addListener(listener)
        playerViewRef.value?.player = ctrl
    }

    LaunchedEffect(currentPlayPath) {
        val ctrl = controller ?: return@LaunchedEffect
        val uri = if (currentPlayPath.startsWith("content://")) Uri.parse(currentPlayPath)
                  else Uri.fromFile(File(currentPlayPath))
        ctrl.setMediaItem(MediaItem.fromUri(uri))
        ctrl.prepare()
        ctrl.playWhenReady = true
        currentPosition = 0L
        duration = 0L
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            delay(500)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls && !isLocked) {
            delay(4000)
            showControls = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = if (isFitToScreen)
                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    controller?.let { player = it }
                    playerViewRef.value = this
                }
            },
            update = { view ->
                view.resizeMode = if (isFitToScreen)
                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                else
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(0.5f))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            fileName,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        if (queueSize > 1) {
                            Text(
                                "$queuePos / $queueSize",
                                color = Color.White.copy(0.5f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    IconButton(onClick = { showSpeedMenu = true }) {
                        Icon(Icons.Default.Speed, "Speed", tint = Color.White)
                    }
                    IconButton(onClick = { isFitToScreen = !isFitToScreen }) {
                        Icon(
                            if (isFitToScreen) Icons.Default.FitScreen else Icons.Default.Fullscreen,
                            "Aspect Ratio", tint = Color.White
                        )
                    }
                    IconButton(onClick = { isLocked = true; showControls = false }) {
                        Icon(Icons.Default.Lock, "Lock Controls", tint = Color.White)
                    }
                }
            }
        }

        if (isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                IconButton(
                    onClick = { isLocked = false; showControls = true },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.6f))
                ) {
                    Icon(Icons.Default.LockOpen, "Unlock", tint = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(0.5f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(FileUtils.formatDuration(currentPosition), color = Color.White.copy(0.7f), fontSize = 12.sp)
                    Text(FileUtils.formatDuration(duration), color = Color.White.copy(0.7f), fontSize = 12.sp)
                }
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { fraction ->
                        val seekTo = (fraction * duration).toLong()
                        controller?.seekTo(seekTo)
                        currentPosition = seekTo
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFBB86FC),
                        inactiveTrackColor = Color.White.copy(0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoControlBtn(Icons.Default.Replay10, "-10s") {
                        val newPos = (currentPosition - 10000L).coerceAtLeast(0L)
                        controller?.seekTo(newPos)
                        currentPosition = newPos
                    }
                    VideoControlBtn(
                        icon = Icons.Default.SkipPrevious,
                        label = "Prev",
                        tint = if (hasPrev) Color.White else Color.White.copy(0.3f)
                    ) {
                        val prev = MediaQueue.goPrev()
                        if (prev != null) currentPlayPath = prev
                        else { controller?.seekTo(0); currentPosition = 0L }
                    }
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.2f))
                            .clickable {
                                if (controller?.isPlaying == true) controller?.pause()
                                else controller?.play()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    VideoControlBtn(
                        icon = Icons.Default.SkipNext,
                        label = "Next",
                        tint = if (hasNext) Color.White else Color.White.copy(0.3f)
                    ) {
                        val next = MediaQueue.goNext()
                        if (next != null) currentPlayPath = next
                        else { val dur = duration; if (dur > 0) controller?.seekTo(dur) }
                    }
                    VideoControlBtn(Icons.Default.Forward10, "+10s") {
                        val newPos = (currentPosition + 10000L).coerceAtMost(duration)
                        controller?.seekTo(newPos)
                        currentPosition = newPos
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VolumeUp, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White.copy(0.8f),
                            activeTrackColor = Color.White.copy(0.6f),
                            inactiveTrackColor = Color.White.copy(0.15f)
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${playbackSpeed}x",
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { showSpeedMenu = true }
                    )
                }
            }
        }

        if (showSpeedMenu) {
            AlertDialog(
                onDismissRequest = { showSpeedMenu = false },
                title = { Text("Playback Speed") },
                text = {
                    Column {
                        listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f).forEach { speed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playbackSpeed = speed
                                        controller?.setPlaybackSpeed(speed)
                                        showSpeedMenu = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = playbackSpeed == speed, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text("${speed}x${if (speed == 1f) "  (Normal)" else ""}")
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSpeedMenu = false }) { Text("Done") } }
            )
        }
    }
}

@Composable
private fun VideoControlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp))
    }
}
