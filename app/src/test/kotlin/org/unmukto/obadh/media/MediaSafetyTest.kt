// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException

class MediaSafetyTest {
    private fun file(mime: String="image/gif",w: Int=1,h: Int=1,n: Int=100)=MediaFile("https://static.klipy.com/media/test",mime,w,h,n)
    // A minimal, independently generated 1px GIF container.
    private val gif=byteArrayOf(71,73,70,56,57,97,1,0,1,0,-128,0,0,0,0,0,-1,-1,-1,
        44,0,0,0,0,1,0,1,0,0,2,2,68,1,0,59)
    private fun le(n: Int,size: Int=4)=ByteArray(size) { (n ushr (it*8)).toByte() }
    private fun chunk(tag: String,body: ByteArray)=tag.toByteArray()+le(body.size)+body+if(body.size%2==1)byteArrayOf(0) else byteArrayOf()
    private fun webp(vararg chunks: ByteArray): ByteArray {
        val content="WEBP".toByteArray()+chunks.reduce { a,b->a+b }
        return "RIFF".toByteArray()+le(content.size)+content
    }
    private fun pixels(w: Int=1,h: Int=1)=chunk("VP8L",byteArrayOf(0x2f)+le((w-1) or ((h-1) shl 14))+byteArrayOf(0))
    private fun canvas(w: Int,h: Int,animated: Boolean)=chunk("VP8X",byteArrayOf(if(animated)2 else 0,0,0,0)+le(w-1,3)+le(h-1,3))
    private fun frame(x: Int=0,y: Int=0,w: Int=1,h: Int=1)=chunk("ANMF",le(x/2,3)+le(y/2,3)+le(w-1,3)+le(h-1,3)+le(100,3)+byteArrayOf(0)+pixels(w,h))

    @Test fun providerUrlsCannotEscapeTheAllowlist() {
        assertTrue(MediaSafety.mediaUrl(file().url))
        for(url in listOf("http://static.klipy.com/x","https://static.klipy.com.evil/x","https://evil@static.klipy.com/x",
            "https://static.klipy.com:444/x","file:///tmp/x","https://static.klipy.com/x#x","not a url"))assertFalse(url,MediaSafety.mediaUrl(url))
        assertTrue(MediaSafety.slug("bangla-15--kpkkxUZAt"));assertFalse(MediaSafety.slug("../trending"))
    }
    @Test fun recipientMimeAndPayloadBudgetChooseAnAnimation() {
        val small=file(n=40);val large=file(w=400,h=400,n=300);val web=file("image/webp",400,400,100)
        assertEquals(large,MediaSafety.select(listOf(small,web,large),listOf("image/*")))
        assertEquals(web,MediaSafety.select(listOf(large,web),listOf("image/webp")))
        assertNull(MediaSafety.select(listOf(large),listOf("image/png")))
        assertNull(MediaSafety.select(listOf(large.copy(bytes=MediaSafety.MAX_MEDIA+1)),listOf("*/*")))
        assertNull(MediaSafety.select(listOf(file("image/png")),listOf("*/*")))
    }
    @Test fun truncatedGifAndWrongCanvasAreRejected() {
        assertTrue(MediaSafety.valid(gif,file()))
        for(n in gif.indices)assertFalse("truncated at $n",MediaSafety.valid(gif.copyOf(n),file()))
        assertFalse(MediaSafety.valid(gif,file(w=2)))
        assertFalse(MediaSafety.valid(gif+byteArrayOf(0),file()))
        val escaping=gif.clone();escaping[20]=1
        assertFalse(MediaSafety.valid(escaping,file()))
    }
    @Test fun gifFrameLimitsBoundThumbnailDecoderWork() {
        val frame=gif.copyOfRange(19,34)
        val many=gif.copyOfRange(0,19)+ByteArray(121*frame.size) { frame[it%frame.size] }+byteArrayOf(59)
        assertFalse(MediaSafety.valid(many,file(),true));assertTrue(MediaSafety.valid(many,file()))
    }
    @Test fun webpMustContainAnImageAndConsistentChunks() {
        val valid=webp(pixels());assertTrue(MediaSafety.valid(valid,file("image/webp")))
        assertFalse(MediaSafety.valid(webp(canvas(1,1,false)),file("image/webp")))
        assertFalse(MediaSafety.valid(webp(canvas(2,1,false),pixels()),file("image/webp",2)))
        assertFalse(MediaSafety.valid(valid.copyOf(valid.size-1),file("image/webp")))
        assertFalse(MediaSafety.valid(valid+byteArrayOf(0),file("image/webp")))
    }
    @Test fun animatedWebpFrameCannotEscapeItsCanvas() {
        val anim=chunk("ANIM",ByteArray(6))
        assertTrue(MediaSafety.valid(webp(canvas(2,2,true),anim,frame()),file("image/webp",2,2)))
        assertFalse(MediaSafety.valid(webp(canvas(2,2,true),anim,frame(x=2)),file("image/webp",2,2)))
        assertFalse(MediaSafety.valid(webp(canvas(2,2,true),frame()),file("image/webp",2,2)))
        assertFalse(MediaSafety.valid(webp(canvas(2,2,true),anim),file("image/webp",2,2)))
        assertFalse(MediaSafety.valid(webp(canvas(300,1,false),pixels(300)),file("image/webp",300),true))
    }
    @Test fun boundedReadingEnforcesLimitsAndCancellationBeforeAllocatingMore() {
        val source=ByteArray(9000)
        assertEquals(9000,MediaSafety.read(ByteArrayInputStream(source),9000).size)
        try { MediaSafety.read(ByteArrayInputStream(source),8999);fail("Oversized input accepted") }
        catch(e: MediaException) { assertEquals(MediaFailure.TOO_LARGE,e.failure) }
        try { MediaSafety.read(ByteArrayInputStream(source),9000,{ false });fail("Canceled input read") }
        catch(_: CancellationException) {}
    }
}
