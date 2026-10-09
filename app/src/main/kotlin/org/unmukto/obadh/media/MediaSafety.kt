// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

/** No decoder, editor or Android state: validate before allocating or granting content. */
object MediaSafety {
    const val MAX_MEDIA = 8 * 1024 * 1024
    const val MAX_THUMB = 384 * 1024
    const val MAX_JSON = 1024 * 1024
    fun slug(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,200}"))
    fun mediaUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        value.length <= 1600 && uri.scheme == "https" && uri.host == "static.klipy.com" &&
            uri.userInfo == null && uri.port in listOf(-1,443) && uri.fragment == null
    }.getOrDefault(false)
    fun accepts(types: List<String>, mime: String) = types.any {
        it.equals(mime,true) || it.equals("image/*",true) || it == "*/*"
    }
    fun canAnimate(types: List<String>) = accepts(types,"image/gif") || accepts(types,"image/webp")
    fun select(files: List<MediaFile>, types: List<String>): MediaFile? = files
        .filter { it.mime in listOf("image/gif","image/webp") && accepts(types,it.mime) && mediaUrl(it.url) && it.bytes in 1..MAX_MEDIA &&
            it.width in 1..1024 && it.height in 1..1024 }
        // Prefer compact WebP when explicitly advertised; GIF for wildcard editors. Aim for
        // the smallest variant at useful resolution, without silently sending a still.
        .sortedWith(compareBy<MediaFile> { if(it.mime==if(types.any { type -> type.equals("image/webp",true) })"image/webp" else "image/gif") 0 else 1 }
            .thenBy { if(maxOf(it.width,it.height)>=300) 0 else 1 }.thenBy { it.bytes })
        .firstOrNull()
    fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun read(input: InputStream, max: Int, active: () -> Boolean = { true }, progress: (Int)->Unit = {}): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(max,8192));val buffer=ByteArray(8192)
        while(true) {
            if(!active()) throw java.util.concurrent.CancellationException()
            val n=input.read(buffer);if(n<0)break
            if(output.size()+n>max) throw MediaException(MediaFailure.TOO_LARGE)
            output.write(buffer,0,n);progress(output.size())
        }
        return output.toByteArray()
    }
    private fun le(bytes: ByteArray, at: Int, n: Int): Int {
        if(at<0 || at+n>bytes.size)return -1
        var value=0;for(i in 0 until n)value=value or ((bytes[at+i].toInt() and 255) shl (i*8));return value
    }
    fun valid(bytes: ByteArray, file: MediaFile, thumb: Boolean = false): Boolean = runCatching {
        val max = if(thumb) MAX_THUMB else MAX_MEDIA
        if(bytes.size !in 1..max || !mediaUrl(file.url) || file.width !in 1..1024 || file.height !in 1..1024 ||
            (thumb && maxOf(file.width,file.height)>256)) return false
        when(file.mime) {
            "image/gif" -> gif(bytes,file,thumb)
            "image/webp" -> webp(bytes,file,thumb)
            else -> false
        }
    }.getOrDefault(false)
    private fun gif(b: ByteArray, file: MediaFile, thumb: Boolean): Boolean {
        if(b.size<14 || String(b,0,6,Charsets.US_ASCII) !in listOf("GIF87a","GIF89a"))return false
        val w=le(b,6,2);val h=le(b,8,2)
        if(w!=file.width || h!=file.height || (thumb && maxOf(w,h)>256))return false
        var p=13;val packed=b[10].toInt() and 255
        if(packed and 128!=0)p+=3*(1 shl ((packed and 7)+1))
        var frames=0
        fun blocks(): Boolean {
            while(p<b.size) { val n=b[p++].toInt() and 255;if(n==0)return true;p+=n;if(p>b.size)return false }
            return false
        }
        while(p<b.size) {
            when(b[p++].toInt() and 255) {
                0x3b -> return frames>0 && p==b.size
                0x21 -> { if(p>=b.size)return false;p++;if(!blocks())return false }
                0x2c -> {
                    if(p+9>b.size)return false
                    val x=le(b,p,2);val y=le(b,p+2,2);val fw=le(b,p+4,2);val fh=le(b,p+6,2)
                    if(fw<1 || fh<1 || x+fw>w || y+fh>h)return false
                    val flags=b[p+8].toInt() and 255;p+=9
                    if(flags and 128!=0)p+=3*(1 shl ((flags and 7)+1))
                    if(p>=b.size || (b[p++].toInt() and 255) !in 2..8 || !blocks())return false
                    frames++;if(frames>if(thumb) 120 else 300)return false
                }
                else -> return false
            }
        }
        return false
    }
    // Validate every frame's canvas and compressed-image header before native decoding.
    // This is a bounded container check, not a replacement for Android's image decoder.
    private fun webp(b: ByteArray,file: MediaFile,thumb: Boolean): Boolean {
        if(b.size<26 || String(b,0,4,Charsets.US_ASCII)!="RIFF" || String(b,8,4,Charsets.US_ASCII)!="WEBP" || le(b,4,4)!=b.size-8)return false
        fun image(tag: String,at: Int,n: Int,w: Int,h: Int): Boolean = when(tag) {
            "VP8 " -> n>=10 && b[at].toInt() and 1==0 && le(b,at+3,3)==0x2a019d &&
                (le(b,at+6,2) and 0x3fff)==w && (le(b,at+8,2) and 0x3fff)==h
            "VP8L" -> n>=5 && b[at].toInt() and 255==0x2f &&
                (le(b,at+1,4) and 0x3fff)+1==w && ((le(b,at+1,4) ushr 14) and 0x3fff)+1==h &&
                le(b,at+1,4) ushr 29==0
            else -> false
        }
        fun frame(from: Int,end: Int,w: Int,h: Int): Boolean {
            var p=from;var images=0
            while(p+8<=end) {
                val tag=String(b,p,4,Charsets.US_ASCII);val n=le(b,p+4,4)
                if(n<0 || n>end-p-8 || p+8+n+(n and 1)>end)return false
                if(tag=="VP8 " || tag=="VP8L") {
                    if(++images!=1 || !image(tag,p+8,n,w,h))return false
                }
                p+=8+n+(n and 1)
            }
            return p==end && images==1
        }
        var p=12;var extended=false;var animation=false;var animHeader=false;var frames=0;var still=false
        while(p+8<=b.size) {
            val tag=String(b,p,4,Charsets.US_ASCII);val n=le(b,p+4,4)
            if(n<0 || n>b.size-p-8 || p+8+n+(n and 1)>b.size)return false
            val at=p+8
            when(tag) {
                "VP8X" -> {
                    if(p!=12 || n!=10 || le(b,at+4,3)+1!=file.width || le(b,at+7,3)+1!=file.height)return false
                    extended=true;animation=b[at].toInt() and 2!=0
                }
                "ANIM" -> { if(!extended || !animation || animHeader || frames>0 || n!=6)return false;animHeader=true }
                "ANMF" -> {
                    if(!animHeader || n<24 || ++frames>if(thumb)120 else 300)return false
                    val x=le(b,at,3)*2;val y=le(b,at+3,3)*2;val w=le(b,at+6,3)+1;val h=le(b,at+9,3)+1
                    if(x+w>file.width || y+h>file.height || !frame(at+16,at+n,w,h))return false
                }
                "VP8 ","VP8L" -> {
                    if(animation || still || !image(tag,at,n,file.width,file.height))return false
                    still=true
                }
            }
            p+=8+n+(n and 1)
        }
        return p==b.size && if(animation)animHeader && frames>0 else still
    }
}
