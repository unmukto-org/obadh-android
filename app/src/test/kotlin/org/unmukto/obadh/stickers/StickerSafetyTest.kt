package org.unmukto.obadh.stickers

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.util.zip.*

class StickerSafetyTest {
    private val png get()=File("src/main/assets/stickers/blobfox/blobfox.png").readBytes()
    private fun zip(vararg files: Pair<String,ByteArray>): ByteArray {
        val result=ByteArrayOutputStream();ZipOutputStream(result).use { z -> files.forEach { (name,data) -> z.putNextEntry(ZipEntry(name));z.write(data);z.closeEntry() } };return result.toByteArray()
    }
    private fun rejected(body: ()->Unit) { try { body();fail("Expected rejection") } catch(e: IllegalArgumentException) {} catch(e: IllegalStateException) {} }
    @Test fun mimeNegotiation() {
        assertTrue(StickerSafety.acceptsPng(listOf("image/*")));assertTrue(StickerSafety.acceptsPng(listOf("image/png")));assertTrue(StickerSafety.acceptsPng(listOf("*/*")))
        assertFalse(StickerSafety.acceptsPng(listOf("image/gif","image/webp")));assertFalse(StickerSafety.acceptsPng(emptyList()))
    }
    @Test fun identifiersCannotEscapeDirectories() { for(id in listOf("../x","a/b","","a.png","/tmp","a\\b","a".repeat(91)))assertFalse(StickerSafety.validId(id));assertTrue(StickerSafety.validId("openmoji-1f600")) }
    @Test fun pngBoundsAndTruncation() { assertTrue(StickerSafety.validPng(png));assertFalse(StickerSafety.validPng(png.copyOf(30)));val huge=png.copyOf();java.nio.ByteBuffer.wrap(huge,16,4).putInt(100000);assertFalse(StickerSafety.validPng(huge)) }
    @Test fun rejectsAnimation() { val animation=png.copyOf();"acTL".toByteArray().copyInto(animation,12);assertFalse(StickerSafety.validPng(animation)) }
    @Test fun boundedReads() { rejected { StickerSafety.readLimited(ByteArrayInputStream(ByteArray(100)),99) };assertEquals(100,StickerSafety.readLimited(ByteArrayInputStream(ByteArray(100)),100).size) }
    private fun extract(data: ByteArray,expected: Map<String,String>,cancelled: ()->Boolean={false}) {
        val dir=Files.createTempDirectory("sticker-test").toFile()
        try { StickerSafety.extract(data.inputStream(),dir,expected,cancelled) } finally { dir.deleteRecursively() }
    }
    @Test fun validInstall() { extract(zip("image.png" to png),mapOf("image.png" to StickerSafety.digest(png))) }
    @Test fun checksumMismatch() { rejected { extract(zip("image.png" to png),mapOf("image.png" to "wrong")) } }
    @Test fun unexpectedContent() { rejected { extract(zip("image.png" to png,"extra" to byteArrayOf(1)),mapOf("image.png" to StickerSafety.digest(png))) } }
    @Test fun missingEntries() { rejected { extract(zip("image.png" to png),mapOf("image.png" to StickerSafety.digest(png),"missing.png" to "")) } }
    @Test fun traversalEvenIfExpected() { rejected { extract(zip("../escape.png" to png),mapOf("../escape.png" to "")) } }
    @Test fun bombBound() { rejected { extract(zip("NOTICE.txt" to ByteArray(StickerSafety.MAX_FILE+1)),mapOf("NOTICE.txt" to "")) } }
    @Test fun cancellationNeverInstalls() { rejected { extract(zip("image.png" to png),mapOf("image.png" to "")) { true } } }
    private fun pack(id: String,items: List<Sticker>)=StickerPack(id,id,"artist","https://example.com","Apache-2.0",true,100,"a".repeat(64),items)
    private fun sticker(id: String,name: String,tags: String)=Sticker(id,name,tags,"b".repeat(64),20)
    @Test fun searchEnglishBanglaAndRomanAliases() {
        val p=pack("test",listOf(sticker("happy","Happy face","খুশি হাসি khushi hashi smile"),sticker("cry","Crying","কান্না kanna")))
        for(q in listOf("happy","HAPPY face","হাসি","hashi","khushi"))assertEquals("happy",StickerCatalog.search(listOf(p),q).single().second.id)
        assertTrue(StickerCatalog.search(listOf(p),"happy kanna").isEmpty());assertTrue(StickerCatalog.search(listOf(p),"unknown").isEmpty())
    }
    @Test fun searchIsBounded() { val p=pack("test",(1..200).map { sticker("i$it","Happy","happy") });assertEquals(12,StickerCatalog.search(listOf(p),"").size);assertEquals(120,StickerCatalog.search(listOf(p),"happy").size) }
}
