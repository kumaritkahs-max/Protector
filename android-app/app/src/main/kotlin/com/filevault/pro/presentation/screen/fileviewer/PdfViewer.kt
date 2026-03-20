package com.filevault.pro.presentation.screen.fileviewer

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun PdfViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pageCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pdfRenderer: PdfRenderer? by remember { mutableStateOf(null) }
    var fileDescriptor: ParcelFileDescriptor? by remember { mutableStateOf(null) }

    DisposableEffect(file) {
        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            pageCount = pdfRenderer!!.pageCount
            val page = pdfRenderer!!.openPage(currentPage)
            val bmp = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap = bmp
            page.close()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to open PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = {
                if (currentPage > 0) currentPage--
            }, enabled = currentPage > 0) {
                Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Previous Page")
            }
            Text("Page ${currentPage + 1} of $pageCount", style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = {
                if (currentPage < pageCount - 1) currentPage++
            }, enabled = currentPage < pageCount - 1) {
                Icon(androidx.compose.material.icons.Icons.Default.ArrowForward, contentDescription = "Next Page")
            }
        }
        Spacer(Modifier.height(8.dp))
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "PDF Page", modifier = Modifier.fillMaxWidth().weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val text = com.filevault.pro.util.PdfTextExtractor.extractTextFromPdf(file)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("PDF Text", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "PDF text copied to clipboard", Toast.LENGTH_SHORT).show()
        }) {
            Text("Copy Text")
        }
    }
}
