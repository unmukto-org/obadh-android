package helium314.keyboard.event

import helium314.keyboard.latin.obadh.ObadhColors
import org.junit.Assert.*
import org.junit.Test

class ObadhThemeCatalogTest {
    @Test fun solidReferenceColorsDoNotDriftWithSystemAppearance() {
        val expected = mapOf("red" to 0xffc62828.toInt(), "green" to 0xff43a047.toInt(), "blue" to 0xff1565c0.toInt(), "classic_light" to 0xffe8eaed.toInt(), "classic_dark" to 0xff292e33.toInt())
        expected.forEach { (name, background) ->
            assertEquals(background, ObadhColors.palette(name,false).background)
            assertEquals(ObadhColors.palette(name,false), ObadhColors.palette(name,true))
        }
        assertEquals(0xff1f1f1f.toInt(),ObadhColors.palette("dark",false).actionText)
    }
    @Test fun gradientCatalogHasStableIdsAndDrawableSafeStops() {
        val themes=ObadhColors.catalog
        assertEquals(themes.size,themes.map { it.id }.distinct().size)
        assertEquals(18,themes.count { it.group=="Colors" })
        for(group in listOf("Light gradient","Dark gradient")) {
            assertEquals(25,themes.count { it.group==group })
            for(theme in themes.filter { it.group==group }) {
                val colors=theme.gradient!!;val stops=theme.positions!!
                assertEquals(colors.size,stops.size)
                assertEquals(0f,stops.first(),0f);assertEquals(1f,stops.last(),0f)
                assertTrue(stops.asList().zipWithNext().all { (a,b) -> b>a })
                assertTrue(colors.all { it ushr 24==255 })
                assertEquals(0x40ffffff,theme.palette.keys)
            }
        }
    }
    @Test fun photoOverlaysMatchMeasuredOpacityAndLegacyColorsRemainValid() {
        val photo=ObadhColors.palette("photo",false)
        assertEquals(0x4dffffff,photo.keys);assertEquals(0x0dffffff,photo.functional)
        assertEquals(0xff5e97f6.toInt(),photo.accent)
        assertEquals(ObadhColors.palette("pink",false),ObadhColors.palette("rose",true))
    }
}
