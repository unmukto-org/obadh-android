package org.unmukto.obadh.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One private, resized photo. The IME never reads the source URI or decodes on a key press. */
object KeyboardPhoto {
    private fun file(context: Context) = File(context.createDeviceProtectedStorageContext().filesDir, "custom_background_image")
    fun exists(context: Context) = file(context).isFile

    suspend fun preview(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(file(context).path, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull()
    }

    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        val atomic = AtomicFile(file(context))
        var output: java.io.FileOutputStream? = null
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: return@withContext false
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 64_000_000L) return@withContext false
            var sample = 1
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
            bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            } ?: return@withContext false
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            }.getOrNull()
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            if (!matrix.isIdentity) {
                val oriented = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
                if (oriented !== bitmap) bitmap!!.recycle()
                bitmap = oriented
            }
            // Bound brightness for readable white labels even when key borders are disabled.
            val dimmed = Bitmap.createBitmap(bitmap!!.width, bitmap!!.height, Bitmap.Config.ARGB_8888)
            Canvas(dimmed).drawBitmap(bitmap!!, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    .35f, 0f, 0f, 0f, 0f, 0f, .35f, 0f, 0f, 0f,
                    0f, 0f, .35f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            })
            bitmap!!.recycle()
            bitmap = dimmed
            output = atomic.startWrite()
            check(bitmap!!.compress(Bitmap.CompressFormat.JPEG, 85, output!!))
            atomic.finishWrite(output)
            output = null
            val p = context.getSharedPreferences("obadh_prefs", Context.MODE_PRIVATE)
            p.edit().putLong("keyboard_photo_revision", System.currentTimeMillis()).putString("keyboard_theme", "photo").apply()
            NativePreferences.send(context)
            true
        } catch (_: Exception) {
            output?.let(atomic::failWrite)
            false
        } finally { bitmap?.recycle() }
    }

    suspend fun remove(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            AtomicFile(file(context)).delete()
            if (file(context).exists()) return@withContext false
            val preferences = context.getSharedPreferences("obadh_prefs", Context.MODE_PRIVATE)
            preferences.edit().apply {
                if (preferences.getString("keyboard_theme", "default") == "photo") putString("keyboard_theme", "default")
                putLong("keyboard_photo_revision", System.currentTimeMillis())
            }.apply()
            NativePreferences.send(context)
            true
        } catch (_: Exception) { false }
    }
}
