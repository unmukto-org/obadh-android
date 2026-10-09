package org.unmukto.obadh.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Only photos explicitly saved by the user: one bounded original each, no download/cache library. */
object PhotoThemes {
    data class Theme(val id: String, val crop: KeyboardPhoto.Crop, val brightness: Float)
    private val mutex = Mutex()
    private fun directory(context: Context) = File(context.createDeviceProtectedStorageContext().filesDir, "keyboard_photos").apply { mkdirs() }
    private fun metadata(context: Context) = AtomicFile(File(directory(context), "themes.json"))
    private fun source(context: Context, id: String): File { require(id.matches(Regex("[a-f0-9]{32}"))); return File(directory(context), "$id.jpg") }
    private fun read(context: Context): List<Theme> = runCatching {
        val array = JSONArray(metadata(context).readFully().toString(Charsets.UTF_8))
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i); val id = o.getString("id")
            if (!id.matches(Regex("[a-f0-9]{32}")) || !source(context, id).isFile) return@mapNotNull null
            Theme(id, KeyboardPhoto.Crop(o.getDouble("left").toFloat(), o.getDouble("top").toFloat(), o.getDouble("width").toFloat(), o.getDouble("height").toFloat()), o.getDouble("brightness").toFloat().coerceIn(0f,1f))
        }
    }.getOrDefault(emptyList())
    private fun write(context: Context, list: List<Theme>) {
        val a = JSONArray()
        list.forEach { t -> a.put(JSONObject().put("id", t.id).put("left", t.crop.left).put("top", t.crop.top)
            .put("width", t.crop.width).put("height", t.crop.height).put("brightness", t.brightness)) }
        val atomic=metadata(context); val stream=atomic.startWrite()
        try { stream.write(a.toString().toByteArray()); atomic.finishWrite(stream) } catch(e: Exception) { atomic.failWrite(stream);throw e }
    }
    suspend fun list(context: Context): List<Theme> = withContext(Dispatchers.IO) { mutex.withLock { read(context) } }
    suspend fun original(context: Context, theme: Theme, small: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(source(context,theme.id).path, BitmapFactory.Options().apply { if (small) inSampleSize=4 }) }.getOrNull()
    }
    suspend fun preview(context: Context, theme: Theme, small: Boolean = false): Bitmap? {
        val original=original(context,theme,small) ?: return null
        return KeyboardPhoto.render(original,theme.crop,theme.brightness).also { original.recycle() }
    }
    suspend fun save(context: Context, bitmap: Bitmap, crop: KeyboardPhoto.Crop, brightness: Float, editing: String? = null): Theme? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val theme=Theme(editing ?: UUID.randomUUID().toString().replace("-",""),crop,brightness)
                if (!source(context,theme.id).isFile) {
                    val atomic=AtomicFile(source(context,theme.id)); val stream=atomic.startWrite()
                    try { check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,stream));atomic.finishWrite(stream) } catch(e: Exception) { atomic.failWrite(stream);throw e }
                }
                val list=read(context).toMutableList(); val index=list.indexOfFirst { it.id==theme.id }
                if(index<0) list.add(theme) else list[index]=theme
                write(context,list)
                theme
            }.getOrNull()
        }
    }
    suspend fun apply(context: Context, theme: Theme, borders: Boolean): Boolean {
        val preview=preview(context,theme) ?: return false
        return KeyboardPhoto.saveRendered(context,preview,borders,theme.id).also { preview.recycle() }
    }
    suspend fun remove(context: Context, theme: Theme): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val prefs=context.getSharedPreferences("obadh_prefs",Context.MODE_PRIVATE)
                if(prefs.getString("keyboard_photo_id",null)==theme.id) check(KeyboardPhoto.remove(context))
                write(context,read(context).filterNot { it.id==theme.id })
                AtomicFile(source(context,theme.id)).delete()
                true
            }.getOrDefault(false)
        }
    }
}
