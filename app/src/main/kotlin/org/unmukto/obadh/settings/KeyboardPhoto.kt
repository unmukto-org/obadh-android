package org.unmukto.obadh.settings

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A bounded decode and one private, cropped photo. No URI or photo decode on the input path. */
object KeyboardPhoto {
    data class Crop(val left: Float, val top: Float, val width: Float, val height: Float)
    private fun file(context: Context) = File(context.createDeviceProtectedStorageContext().filesDir, "custom_background_image")
    fun exists(context: Context) = file(context).isFile

    suspend fun preview(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(file(context).path, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull()
    }

    suspend fun load(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 64_000_000L) return@withContext null
            var sample = 1
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
            bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            } ?: return@withContext null
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
            bitmap
        } catch (_: Exception) { bitmap?.recycle(); null }
    }

    suspend fun render(bitmap: Bitmap, crop: Crop, brightness: Float): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            require(crop.left.isFinite() && crop.top.isFinite() && crop.width.isFinite() && crop.height.isFinite())
            val left = crop.left.coerceIn(0f, .999f)
            val top = crop.top.coerceIn(0f, .999f)
            val width = crop.width.coerceIn(.001f, 1f - left)
            val height = crop.height.coerceIn(.001f, 1f - top)
            val source = Rect((left * bitmap.width).toInt(), (top * bitmap.height).toInt(),
                ((left + width) * bitmap.width).toInt().coerceAtLeast((left * bitmap.width).toInt() + 1),
                ((top + height) * bitmap.height).toInt().coerceAtLeast((top * bitmap.height).toInt() + 1))
            val output = Bitmap.createBitmap(source.width().coerceAtMost(1200), source.height().coerceAtMost(1200), Bitmap.Config.ARGB_8888)
            val light = brightness.coerceIn(0f, 1f)
            Canvas(output).drawBitmap(bitmap, source, Rect(0, 0, output.width, output.height), Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    light, 0f, 0f, 0f, 0f, 0f, light, 0f, 0f, 0f,
                    0f, 0f, light, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            })
            output
        }.getOrNull()
    }

    /** Only Apply changes the stored image/theme. Cancelling any editor step leaves them intact. */
    suspend fun save(context: Context, bitmap: Bitmap, crop: Crop, brightness: Float, borders: Boolean = true): Boolean {
        val rendered = render(bitmap, crop, brightness) ?: return false
        return saveRendered(context, rendered, borders).also { rendered.recycle() }
    }

    suspend fun saveRendered(context: Context, bitmap: Bitmap, borders: Boolean, photoId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val atomic = AtomicFile(file(context))
        var output: java.io.FileOutputStream? = null
        try {
            output = atomic.startWrite()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output!!))
            atomic.finishWrite(output); output = null
            val p = context.getSharedPreferences("obadh_prefs", Context.MODE_PRIVATE)
            check(p.edit().putLong("keyboard_photo_revision", System.currentTimeMillis()).putString("keyboard_theme", "photo")
                .putInt("keyboard_theme_mode", 2).putBoolean("keyboard_key_borders", borders).putString("keyboard_photo_id", photoId).commit())
            NativePreferences.send(context)
            true
        } catch (_: Exception) { output?.let(atomic::failWrite); false }
    }

    suspend fun save(context: Context, uri: Uri): Boolean {
        val bitmap = load(context, uri) ?: return false
        return save(context, bitmap, Crop(0f, 0f, 1f, 1f), .4f).also { bitmap.recycle() }
    }

    suspend fun remove(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            AtomicFile(file(context)).delete()
            if (file(context).exists()) return@withContext false
            val preferences = context.getSharedPreferences("obadh_prefs", Context.MODE_PRIVATE)
            check(preferences.edit().apply {
                if (preferences.getString("keyboard_theme", "default") == "photo") { putString("keyboard_theme", "default"); putInt("keyboard_theme_mode", 0); remove("keyboard_photo_id") }
                putLong("keyboard_photo_revision", System.currentTimeMillis())
            }.commit())
            NativePreferences.send(context)
            true
        } catch (_: Exception) { false }
    }
}
