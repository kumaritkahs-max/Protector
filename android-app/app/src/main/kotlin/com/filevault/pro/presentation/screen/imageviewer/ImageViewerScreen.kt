package com.filevault.pro.presentation.screen.imageviewer

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.filevault.pro.util.FileUtils
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

@Composable
fun ImageViewerScreen(
    path: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val decodedPath = remember(path) { try { URLDecoder.decode(path, "UTF-8") } catch (_: Exception) { path } }
    val file = remember(decodedPath) { File(decodedPath) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var rotation by remember { mutableFloatStateOf(0f) }

    val minScale = 1f
    val maxScale = 5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    rotationZ = rotation
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        scale = newScale
                        if (scale > 1f) {
                            offset = Offset(
                                x = offset.x + pan.x,
                                y = offset.y + pan.y
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        file.name,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                    IconButton(onClick = {
                        val fileUri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Image"))
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = Color.White)
                    }
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Icon(Icons.Default.Info, "Info", tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${(scale * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlButton(icon = Icons.Default.ZoomOut, label = "Zoom Out") {
                        scale = max(minScale, scale - 0.5f)
                        if (scale <= 1f) offset = Offset.Zero
                    }
                    ControlButton(icon = Icons.Default.ZoomIn, label = "Zoom In") {
                        scale = min(maxScale, scale + 0.5f)
                    }
                    ControlButton(icon = Icons.Default.RotateRight, label = "Rotate") {
                        rotation = (rotation + 90f) % 360f
                    }
                    ControlButton(icon = Icons.Default.FitScreen, label = "Fit") {
                        scale = 1f
                        offset = Offset.Zero
                        rotation = 0f
                    }
                    ControlButton(icon = Icons.Default.OpenInNew, label = "Open") {
                        val fileUri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                }
            }
        }

        if (showInfo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) { detectTapGestures { showInfo = false } },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("File Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        InfoRow("Name", file.name)
                        InfoRow("Size", FileUtils.formatSize(file.length()))
                        InfoRow("Path", file.parent ?: "")
                        InfoRow("Modified", SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                            .format(Date(file.lastModified())))
                        InfoRow("Extension", file.extension.uppercase().ifEmpty { "Unknown" })
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showInfo = false }, modifier = Modifier.align(Alignment.End)) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
