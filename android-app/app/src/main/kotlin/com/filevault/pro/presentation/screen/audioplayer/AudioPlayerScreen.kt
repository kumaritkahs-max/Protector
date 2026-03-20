package com.filevault.pro.presentation.screen.audioplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.filevault.pro.service.MediaPlaybackService
import com.filevault.pro.util.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.net.URLDecoder
import kotlinx.coroutines.delay

@Composable
fun AudioPlayerScreen(
    path: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val decodedPath = remember(path) { try { URLDecoder.decode(path, "UTF-8") } catch (_: Exception) { path } }
    val file = remember(decodedPath) { File(decodedPath) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isRepeat by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var showEqualizer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MediaPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get().apply {
                val uri = if (decodedPath.startsWith("content://")) {
                    Uri.parse(decodedPath)
                } else {
                    Uri.fromFile(file)
                }
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
                if (state == Player.STATE_READY) {
                    duration = ctrl.duration.coerceAtLeast(0L)
                }
            }
        }
        ctrl.addListener(listener)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller?.release()
        }
    }

    val bgColors = listOf(
        Color(0xFF1A0A2E),
        Color(0xFF16213E),
        Color(0xFF0F3460)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    "Now Playing",
                    color = Color.White.copy(0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { showSpeedMenu = true }) {
                    Icon(Icons.Default.Speed, "Speed", tint = Color.White.copy(0.8f))
                }
            }

            Spacer(Modifier.weight(0.5f))

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF6B4EFF), Color(0xFF9C27B0), Color(0xFF1A0A2E))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = Color.White.copy(0.9f),
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    file.nameWithoutExtension,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    file.extension.uppercase() + " • " + FileUtils.formatSize(file.length()),
                    color = Color.White.copy(0.5f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { fraction ->
                        val seekTo = (fraction * duration).toLong()
                        controller?.seekTo(seekTo)
                        currentPosition = seekTo
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF7C4DFF),
                        inactiveTrackColor = Color.White.copy(0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(FileUtils.formatDuration(currentPosition), color = Color.White.copy(0.5f), fontSize = 12.sp)
                    Text(FileUtils.formatDuration(duration), color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    isShuffle = !isShuffle
                    controller?.shuffleModeEnabled = isShuffle
                }) {
                    Icon(
                        Icons.Default.Shuffle,
                        "Shuffle",
                        tint = if (isShuffle) Color(0xFF7C4DFF) else Color.White.copy(0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = {
                    controller?.seekTo(0)
                    currentPosition = 0
                }) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7C4DFF))
                        .clickable {
                            if (controller?.isPlaying == true) controller?.pause()
                            else controller?.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = {
                    val dur = duration
                    if (dur > 0) controller?.seekTo(dur)
                }) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                IconButton(onClick = {
                    isRepeat = !isRepeat
                    controller?.repeatMode = if (isRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                }) {
                    Icon(
                        if (isRepeat) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        tint = if (isRepeat) Color(0xFF7C4DFF) else Color.White.copy(0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallControl(Icons.Default.Replay10, "-10s") {
                    val newPos = (currentPosition - 10000L).coerceAtLeast(0L)
                    controller?.seekTo(newPos)
                    currentPosition = newPos
                }
                SmallControl(Icons.Default.Forward10, "+10s") {
                    val newPos = (currentPosition + 10000L).coerceAtMost(duration)
                    controller?.seekTo(newPos)
                    currentPosition = newPos
                }
                SmallControl(Icons.Default.Snooze, "Sleep") { showSleepTimer = true }
                SmallControl(Icons.Default.Share, "Share") {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Audio"))
                }
            }

            Spacer(Modifier.weight(0.5f))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(0.08f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayCircleOutline, null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Playing in background supported — screen can be off",
                        color = Color.White.copy(0.6f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (showSpeedMenu) {
            AlertDialog(
                onDismissRequest = { showSpeedMenu = false },
                title = { Text("Playback Speed") },
                text = {
                    Column {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
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

        if (showSleepTimer) {
            AlertDialog(
                onDismissRequest = { showSleepTimer = false },
                title = { Text("Sleep Timer") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (sleepTimerMinutes == 0) "Off" else "$sleepTimerMinutes min",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = sleepTimerMinutes.toFloat(),
                            onValueChange = { sleepTimerMinutes = it.toInt() },
                            valueRange = 0f..120f,
                            steps = 23
                        )
                        Text("Drag to set timer (0 = off)", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimer = false }) { Text("Set") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        sleepTimerMinutes = 0
                        showSleepTimer = false
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun SmallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(0.5f), fontSize = 10.sp)
    }
}
