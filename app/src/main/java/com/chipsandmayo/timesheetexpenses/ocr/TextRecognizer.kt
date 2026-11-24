package com.chipsandmayo.timesheetexpenses.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

data class OcrResult(val lines: List<OcrLine>, val fullText: String)

object ReceiptOcr {
    suspend fun read(context: Context, file: File): OcrResult {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(image).await()
            val lines = result.textBlocks.flatMap { b -> b.lines }.map { l ->
                val box = l.boundingBox
                OcrLine(l.text, box?.top ?: 0, box?.bottom ?: 0)
            }
            return OcrResult(lines, result.text)
        } finally {
            recognizer.close()
        }
    }
}
