package com.chipsandmayo.timesheetexpenses.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun SignatureDialog(onDismiss: () -> Unit, title: String = "Sign below", onSave: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("Tip: turn your phone sideways for more room.", style = MaterialTheme.typography.bodySmall)
                Canvas(
                    Modifier.fillMaxWidth().height(200.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current.clear(); current.add(it) },
                                onDrag = { change, _ -> current.add(change.position) },
                                onDragEnd = { strokes.add(current.toList()); current.clear() },
                            )
                        },
                ) {
                    (strokes + listOf(current.toList())).forEach { pts ->
                        if (pts.size > 1) {
                            val p = androidx.compose.ui.graphics.Path()
                            p.moveTo(pts[0].x, pts[0].y)
                            pts.drop(1).forEach { p.lineTo(it.x, it.y) }
                            drawPath(p, Color(0xFF0B1F5C), style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { strokes.clear(); current.clear() }) { Text("Clear") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(enabled = strokes.isNotEmpty(), onClick = { renderSignature(strokes)?.let(onSave) }) { Text("Save") }
                }
            }
        }
    }
}

/** Renders strokes onto a transparent bitmap cropped to the ink. */
private fun renderSignature(strokes: List<List<Offset>>): Bitmap? {
    val pts = strokes.flatten()
    if (pts.isEmpty()) return null
    val pad = 12f
    val minX = pts.minOf { it.x } - pad; val minY = pts.minOf { it.y } - pad
    val maxX = pts.maxOf { it.x } + pad; val maxY = pts.maxOf { it.y } + pad
    val w = (maxX - minX).toInt().coerceAtLeast(10); val h = (maxY - minY).toInt().coerceAtLeast(10)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint().apply {
        color = android.graphics.Color.rgb(11, 31, 92); style = Paint.Style.STROKE; strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    strokes.filter { it.size > 1 }.forEach { s ->
        val path = Path()
        path.moveTo(s[0].x - minX, s[0].y - minY)
        s.drop(1).forEach { path.lineTo(it.x - minX, it.y - minY) }
        canvas.drawPath(path, paint)
    }
    return bmp
}
