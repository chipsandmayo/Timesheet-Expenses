package com.chipsandmayo.timesheetexpenses.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

object Images {
    /** Upright, max [maxSide]px JPEG of a camera photo. */
    fun receiptJpeg(file: File, maxSide: Int = 2200, quality: Int = 85): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return file.readBytes()
        val rotation = when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        var out = bmp
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        return ByteArrayOutputStream().use { bos ->
            out.compress(Bitmap.CompressFormat.JPEG, quality, bos); bos.toByteArray()
        }
    }

    fun pngSize(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        return o.outWidth to o.outHeight
    }
}
