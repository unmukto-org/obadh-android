// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import android.content.ClipDescription
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.*
import org.unmukto.obadh.keyboard.NativeObadhFeatures
import org.unmukto.obadh.keyboard.ObadhInputMethodService
import java.util.UUID

/** One private search session; no provider work runs on the IME typing/model executors. */
object MediaController {
    const val SEARCH_OPTIONS="obadh.media.search"
    private data class Target(val pkg: String?,val id: Int,val name: String?,val type: Int) {
        companion object { fun of(info: EditorInfo)=Target(info.packageName,info.fieldId,info.fieldName,info.inputType) }
    }
    class SearchMemory {
        var initialized=false;var query="";var renderedQuery="";var recent=false;var items: List<MediaItem> = emptyList()
        var page=0;var hasNext=false;var scroll=0
    }
    data class Session(val token: String,val kind: MediaKind,val types: List<String>,val expires: Long,val memory: SearchMemory=SearchMemory())
    private data class Owned(val session: Session,val target: Target,var leaving: Boolean=false)
    private data class Choice(val owner: Owned,val item: MediaItem,val file: MediaFile,val bytes: ByteArray,val query: String,val expires: Long)
    private val main=Handler(Looper.getMainLooper())
    private val analytics=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var owner: Owned?=null
    private var returnOwner: Owned?=null
    private var pending: Choice?=null
    fun stopBackground() { analytics.coroutineContext.cancelChildren() }
    fun allowed(ime: ObadhInputMethodService?=NativeObadhFeatures.activeIme): Boolean {
        val info=ime?.currentInputEditorInfo ?: return false
        val settings=Settings.getValues()
        return KlipyClient.configured && info.privateImeOptions!=SEARCH_OPTIONS &&
            !settings.mInputAttributes.mIsPasswordField && !settings.mIncognitoModeEnabled
    }
    fun open(kind: MediaKind) {
        val ime=NativeObadhFeatures.activeIme ?: return
        if(!allowed(ime)) { message(ime,"Online media is unavailable in private or password fields.");return }
        val info=ime.currentInputEditorInfo ?: return
        val session=Session(UUID.randomUUID().toString(),kind,EditorInfoCompat.getContentMimeTypes(info).toList(),SystemClock.elapsedRealtime()+10*60*1000)
        val next=Owned(session,Target.of(info));owner=next;returnOwner=next;pending=null
        ime.prepareObadhContentInput()
        ime.startActivity(Intent(ime,MediaSearchActivity::class.java).putExtra("token",session.token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun session(token: String): Session?=owner?.session?.takeIf { it.token==token && it.expires>SystemClock.elapsedRealtime() }
    fun canRequest(token: String): Boolean {
        val ime=NativeObadhFeatures.activeIme ?: return false
        return session(token)!=null && MediaPreferences.enabled &&
            !ime.prefs().getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE,false)
    }
    fun choose(token: String,item: MediaItem,file: MediaFile,bytes: ByteArray,query: String): Boolean {
        val current=owner?.takeIf { session(token)!=null && canRequest(token) } ?: return false
        if(!MediaSafety.accepts(current.session.types,file.mime) || bytes.size !in 1..MediaSafety.MAX_MEDIA)return false
        current.leaving=true;pending=Choice(current,item,file,bytes,query,SystemClock.elapsedRealtime()+5000);owner=null
        main.postDelayed({ if(pending?.owner===current)pending=null },5000)
        return true
    }
    fun cancel(token: String) { if(owner?.session?.token==token) { owner?.leaving=true;owner=null } }
    fun startInput(ime: ObadhInputMethodService,info: EditorInfo?) {
        if(info==null || info.privateImeOptions==SEARCH_OPTIONS)return
        val original=returnOwner?.takeIf { it.leaving } ?: return
        returnOwner=null;owner=null
        if(Target.of(info)!=original.target || SystemClock.elapsedRealtime()>=original.session.expires) { pending=null;return }
        main.postDelayed({
            val current=ime.currentInputEditorInfo
            if(current!=null && Target.of(current)==original.target) {
                if(android.os.Build.VERSION.SDK_INT>=28)ime.requestShowSelf(0)
                else ime.window.window?.attributes?.token?.let { ime.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInputFromInputMethod(it,0) }
            }
        },100)
    }
    fun returned(ime: ObadhInputMethodService) {
        if(!ime.isInputViewShown)return
        val choice=pending ?: return
        val info=ime.currentInputEditorInfo ?: return
        if(info.privateImeOptions==SEARCH_OPTIONS)return
        pending=null
        if(SystemClock.elapsedRealtime()>=choice.expires || Target.of(info)!=choice.owner.target || !allowed(ime) || !MediaPreferences.enabled) {
            message(ime,"The input field changed. Select the item again.");return
        }
        val connection=ime.currentInputConnection ?: return
        val types=EditorInfoCompat.getContentMimeTypes(info).toList()
        if(!MediaSafety.accepts(types,choice.file.mime)) { message(ime,"This app doesn't accept this animation.");return }
        val uri=runCatching { MediaDelivery.put("${ime.packageName}.media",choice.bytes,choice.file.mime) }.getOrElse {
            message(ime,MediaFailure.BUSY.message);return
        }
        ime.prepareObadhContentInput()
        val accepted=runCatching { InputConnectionCompat.commitContent(connection,info,
            InputContentInfoCompat(uri,ClipDescription(choice.item.title,arrayOf(choice.file.mime)),null),
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null) }.getOrDefault(false)
        if(accepted) {
            MediaPreferences.remember(choice.owner.session.kind,choice.item)
            val kind=choice.owner.session.kind;val item=choice.item;val query=choice.query
            analytics.launch { if(MediaPreferences.enabled)runCatching { MediaThumbnails.warm(ime,item.preview) } }
            analytics.launch { if(MediaPreferences.enabled)runCatching { KlipyClient.shared(kind,item.slug,query,MediaPreferences.customer) } }
        } else { MediaDelivery.discard(uri);message(ime,"This app couldn't accept the animation. Try another input field.") }
        main.postDelayed({ MediaDelivery.expire() },91_000)
    }
    private fun message(ime: ObadhInputMethodService,text: String)=Toast.makeText(ime,text,Toast.LENGTH_SHORT).show()
}
