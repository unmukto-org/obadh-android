package org.unmukto.obadh.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import org.unmukto.obadh.settings.KeyboardPhoto
import org.unmukto.obadh.settings.PhotoThemes

/** At most a decoded source and one preview across rotation; gallery images are small, lazy previews. */
class ThemePhotoState : ViewModel() {
    var pending by mutableStateOf<Bitmap?>(null)
    var staged by mutableStateOf<Bitmap?>(null)
    var photos by mutableStateOf<List<PhotoThemes.Theme>>(emptyList())
    var current by mutableStateOf<PhotoThemes.Theme?>(null)
    var loading by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    var catalogRevision by mutableIntStateOf(0)
    var previewRevision by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    private var draftSource: Bitmap? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    fun catalog(context: Context) { scope.launch { photos=PhotoThemes.list(context.applicationContext) } }
    fun load(context: Context, uri: Uri) {
        if (loading || saving) return
        scope.launch {
            loading = true; current=null; staged=null; draftSource=null
            try {
                pending = KeyboardPhoto.load(context.applicationContext, uri)
                if (pending == null) error = "Couldn't read this image. Choose another photo."
            } finally { loading = false }
        }
    }
    fun show(context: Context, theme: PhotoThemes.Theme) {
        if (loading || saving) return
        scope.launch {
            loading=true
            try {
                draftSource=null; current=theme; staged=PhotoThemes.preview(context.applicationContext,theme)
                if(staged==null) error="Couldn't read this photo. Try another theme." else previewRevision++
            } finally { loading=false }
        }
    }
    fun edit(context: Context) {
        val theme=current ?: return
        if(loading || saving) return
        scope.launch {
            loading=true
            try { pending=draftSource ?: PhotoThemes.original(context.applicationContext,theme); if(pending==null) error="Couldn't read this photo." } finally { loading=false }
        }
    }
    fun stage(bitmap: Bitmap, crop: KeyboardPhoto.Crop, brightness: Float) {
        if (saving) return
        scope.launch {
            saving = true
            try {
                val preview=KeyboardPhoto.render(bitmap,crop,brightness)
                if(preview != null) {
                    draftSource=bitmap
                    current=PhotoThemes.Theme(current?.id ?: java.util.UUID.randomUUID().toString().replace("-",""),crop,brightness)
                    pending=null;staged=preview;previewRevision++
                } else error="Couldn't prepare this photo. Try again."

            } finally { saving = false }
        }
    }
    fun apply(context: Context, borders: Boolean) {
        val theme=current ?: return
        if (saving) return
        scope.launch {
            saving = true
            try {
                val source=draftSource
                val saved=if(source==null) theme else PhotoThemes.save(context.applicationContext,source,theme.crop,theme.brightness,theme.id)
                    ?: run { error="Couldn't save this photo. Try again.";return@launch }
                if (PhotoThemes.apply(context.applicationContext, saved, borders)) {
                    current=saved;draftSource=null;staged=null
                    photos=PhotoThemes.list(context.applicationContext);catalogRevision++;revision++
                }
                else error = "Couldn't apply this photo. Try again."
            } finally { saving = false }
        }
    }
    fun remove(context: Context) {
        val theme=current ?: return
        if(saving) return
        scope.launch {
            saving=true
            try {
                if(PhotoThemes.remove(context.applicationContext,theme)) { current=null;staged=null;draftSource=null;photos=PhotoThemes.list(context.applicationContext);catalogRevision++;revision++ }
                else error="Couldn't delete this photo. Try again."
            } finally { saving=false }
        }
    }
    fun cancelPreview() { pending=null;staged=null;draftSource=null;current=null }
    override fun onCleared() { scope.cancel(); cancelPreview() }
}
