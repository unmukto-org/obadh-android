// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.stickers

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.latin.settings.Settings
import org.unmukto.obadh.keyboard.NativeObadhFeatures
import org.unmukto.obadh.keyboard.ObadhInputMethodService
import java.io.File
import java.lang.ref.WeakReference

/** IME-process session ownership: late IO/search results can never insert into a new editor. */
object StickerController {
    private var generation=0L
    private var panel=WeakReference<PopupWindow>(null)
    private var sending=false
    private data class Target(val pkg: String?,val id: Int,val name: String?,val inputType: Int) {
        companion object { fun of(info: EditorInfo)=Target(info.packageName,info.fieldId,info.fieldName,info.inputType) }
    }
    private data class SearchSession(val token: String,val target: Target,val expires: Long,val incognito: Boolean)
    private data class Pending(val session: SearchSession,val pack: StickerPack,val item: Sticker,val expires: Long)
    private var search: SearchSession?=null
    private var pending: Pending?=null
    private var returnTarget: SearchSession?=null
    private fun debug(message: String) { if(org.unmukto.obadh.BuildConfig.DEBUG) android.util.Log.d("ObadhStickers",message) }
    fun invalidate() { debug("invalidate $generation"); generation++;panel.get()?.dismiss();panel.clear();sending=false }
    fun supported(ime: ObadhInputMethodService?=NativeObadhFeatures.activeIme): Boolean {
        val info=ime?.currentInputEditorInfo ?: return false
        if(Settings.getValues().mInputAttributes.mIsPasswordField) return false
        return StickerSafety.acceptsPng(EditorInfoCompat.getContentMimeTypes(info).toList())
    }
    fun show(anchor: View,onDismiss: () -> Unit): PopupWindow? {
        val ime=NativeObadhFeatures.activeIme ?: return null
        if(ime.currentInputEditorInfo?.privateImeOptions=="obadh.sticker.search") return null
        val popup=StickerPanel.create(ime,anchor,onDismiss) ?: return null
        panel=WeakReference(popup);return popup
    }
    fun openManager(context: Context,pack: String?=null) {
        context.startActivity(Intent(context,StickerPacksActivity::class.java).putExtra("pack",pack).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun startSearch(ime: ObadhInputMethodService) {
        val info=ime.currentInputEditorInfo ?: return
        if(!supported(ime)) return
        search=SearchSession(java.util.UUID.randomUUID().toString(),Target.of(info),SystemClock.elapsedRealtime()+10*60*1000,Settings.getValues().mIncognitoModeEnabled)
        returnTarget=search
        ime.prepareObadhContentInput()
        panel.get()?.dismiss()
        ime.startActivity(Intent(ime,StickerSearchActivity::class.java).putExtra("token",search!!.token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
    }
    internal fun incognitoSearch(token: String)=search?.takeIf { it.token==token }?.incognito==true
    internal fun validSearch(token: String)=search?.let { it.token==token && SystemClock.elapsedRealtime()<it.expires }==true
    internal fun chooseSearch(token: String,pack: StickerPack,item: Sticker) {
        val session=search?.takeIf { validSearch(token) } ?: return
        debug("search choice ${session.target}")
        pending=Pending(session,pack,item,SystemClock.elapsedRealtime()+3000);search=null
    }
    internal fun cancelSearch(token: String) { if(search?.token==token) search=null }
    fun restoreInput(ime: ObadhInputMethodService,info: EditorInfo?) {
        val target=returnTarget ?: return
        if(info==null || info.privateImeOptions=="obadh.sticker.search") return
        returnTarget=null
        if(Target.of(info)!=target.target || SystemClock.elapsedRealtime()>=target.expires) { pending=null;return }
        StickerIO.main.postDelayed({
            val current=ime.currentInputEditorInfo
            if(current!=null && Target.of(current)==target.target) {
                if(android.os.Build.VERSION.SDK_INT>=28) ime.requestShowSelf(0)
                else ime.window.window?.attributes?.token?.let { ime.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInputFromInputMethod(it,0) }
            }
        },100)
    }
    fun returned(ime: ObadhInputMethodService) {
        debug("returned shown=${ime.isInputViewShown} pending=${pending!=null} info=${ime.currentInputEditorInfo?.let(Target::of)} private=${ime.currentInputEditorInfo?.privateImeOptions}")
        if(!ime.isInputViewShown)return
        val choice=pending ?: return
        val info=ime.currentInputEditorInfo ?: return
        if(info.privateImeOptions=="obadh.sticker.search") return
        pending=null
        if(SystemClock.elapsedRealtime()<choice.expires && Target.of(info)==choice.session.target && supported(ime)) {
            StickerIO.main.post { if(ime.currentInputEditorInfo===info && ime.isInputViewShown) send(ime,choice.pack,choice.item,choice.session.incognito) }
        } else message(ime,"The input field changed. Choose the sticker again.")
    }
    fun send(ime: ObadhInputMethodService,pack: StickerPack,item: Sticker,incognito: Boolean=Settings.getValues().mIncognitoModeEnabled) {
        debug("send requested supported=${supported(ime)} generation=$generation")
        if(sending) return
        if(!supported(ime)) { message(ime,"This app doesn't accept keyboard stickers.");return }
        val info=ime.currentInputEditorInfo;val connection=ime.currentInputConnection ?: return;val epoch=generation
        sending=true
        StickerIO.executor.execute {
            val prepared=runCatching {
                val data=pack.open(ime,"${item.id}.png").use { StickerSafety.readLimited(it,StickerSafety.MAX_FILE) }
                check(StickerSafety.validPng(data) && StickerSafety.digest(data)==item.sha256)
                val root=File(ime.filesDir,"sticker-share").apply { mkdirs() }
                // Retain grants long enough for asynchronous receivers; bound growth without deleting fresh images.
                root.listFiles()?.filter { it.lastModified()<System.currentTimeMillis()-72*60*60*1000 }?.forEach { it.delete() }
                val output=File(root,"${item.sha256}.png")
                if(!output.exists()) {
                    check((root.listFiles()?.size ?: 0)<256) { "Sticker storage is busy. Try again later." }
                    val stage=File(root,".pending");stage.writeBytes(data);check(stage.renameTo(output))
                }
                output.setLastModified(System.currentTimeMillis())
                FileProvider.getUriForFile(ime,"${ime.packageName}.stickers",output)
            }
            StickerIO.main.post {
                debug("send prepared epoch=$epoch current=$generation sameInfo=${ime.currentInputEditorInfo===info} shown=${ime.isInputViewShown}")
                if(epoch!=generation || ime.currentInputEditorInfo!==info || !ime.isInputViewShown) return@post
                sending=false
                prepared.onSuccess { uri ->
                    if(!supported(ime)) return@onSuccess
                    ime.prepareObadhContentInput()
                    val accepted=runCatching { InputConnectionCompat.commitContent(connection,info!!,
                        InputContentInfoCompat(uri,ClipDescription(item.name,arrayOf("image/png")),null),
                        InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null) }.getOrDefault(false)
                    if(accepted) { if(!incognito) StickerHistory.recent(ime,pack,item); panel.get()?.dismiss() }
                    else message(ime,"This app couldn't accept the sticker. Try another input field.")
                }.onFailure { message(ime,"Couldn't open this sticker. Check storage or download the pack again.") }
            }
        }
    }
    private fun message(context: Context,text: String) = Toast.makeText(context,text,Toast.LENGTH_SHORT).show()
}

internal object StickerHistory {
    @Volatile private var store: android.content.SharedPreferences?=null
    @Volatile private var history: Map<String,List<String>> = emptyMap()
    fun load(context: Context) {
        if(store!=null)return
        val prefs=context.getSharedPreferences("sticker-history",Context.MODE_PRIVATE)
        history=listOf("recent","favorites").associateWith { prefs.getString(it,"").orEmpty().split('|').filter(String::isNotEmpty) }
        store=prefs
    }
    private fun save(field: String,ids: List<String>) { history=history+(field to ids);store?.edit()?.putString(field,ids.joinToString("|"))?.apply() }
    fun key(pack: StickerPack,item: Sticker)="${pack.id}/${item.id}"
    fun ids(context: Context,field: String)=history[field].orEmpty()
    fun recent(context: Context,pack: StickerPack,item: Sticker) { val id=key(pack,item);save("recent",(listOf(id)+ids(context,"recent").filter { it!=id }).take(32)) }
    fun favorite(context: Context,pack: StickerPack,item: Sticker): Boolean {
        val id=key(pack,item);val old=ids(context,"favorites");val add=id !in old
        save("favorites",(if(add) listOf(id)+old else old.filter { it!=id }).take(64));return add
    }
}
