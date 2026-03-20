package com.filevault.pro.util

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

object PdfTextExtractor {
    fun extractTextFromPdf(file: File): String {
        return try {
            val pdfDoc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(pdfDoc)
            pdfDoc.close()
            text
        } catch (e: Exception) {
            "Failed to extract text: ${e.message}"
        }
    }
}
