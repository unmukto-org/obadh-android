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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    fun catalog(context: Context) { scope.launch { photos=PhotoThemes.list(context.applicationContext) } }
    fun load(context: Context, uri: Uri) {
        if (loading || saving) return
        scope.launch {
            loading = true; current=null; staged=null
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
                current=theme; staged=PhotoThemes.preview(context.applicationContext,theme)
                if(staged==null) error="Couldn't read this photo. Try another theme." else previewRevision++
            } finally { loading=false }
        }
    }
    fun edit(context: Context) {
        val theme=current ?: return
        if(loading || saving) return
        scope.launch {
            loading=true
            try { pending=PhotoThemes.original(context.applicationContext,theme); if(pending==null) error="Couldn't read this photo." } finally { loading=false }
        }
    }
    fun stage(context: Context, bitmap: Bitmap, crop: KeyboardPhoto.Crop, brightness: Float) {
        if (saving) return
        scope.launch {
            saving = true
            try {
                val saved=PhotoThemes.save(context.applicationContext,bitmap,crop,brightness,current?.id)
                if(saved != null) {
                    current=saved; pending=null; staged=PhotoThemes.preview(context.applicationContext,saved)
                    photos=PhotoThemes.list(context.applicationContext); catalogRevision++; previewRevision++
                } else error="Couldn't save this photo. Try again."
            } finally { saving = false }
        }
    }
    fun apply(context: Context, borders: Boolean) {
        val theme=current ?: return
        if (saving) return
        scope.launch {
            saving = true
            try {
                if (PhotoThemes.apply(context.applicationContext, theme, borders)) { staged=null; revision++ }
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
                if(PhotoThemes.remove(context.applicationContext,theme)) { current=null;staged=null;photos=PhotoThemes.list(context.applicationContext);catalogRevision++;revision++ }
                else error="Couldn't delete this photo. Try again."
            } finally { saving=false }
        }
    }
    override fun onCleared() { scope.cancel(); pending = null; staged = null }
}
