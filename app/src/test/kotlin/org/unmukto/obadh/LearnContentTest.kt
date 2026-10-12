package org.unmukto.obadh

import org.junit.Assert.*
import org.junit.Test
import org.unmukto.obadh.app.*

class LearnContentTest {
    @Test fun lessonIdsAreUniqueAndNonEmpty() {
        val ids = LearnContent.lessons.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        LearnContent.lessons.forEach { assertTrue(it.id, it.challenges.isNotEmpty()) }
    }

    @Test fun everyChallengeHasATargetAndKeys() {
        LearnContent.lessons.flatMap { it.challenges }.forEach {
            assertTrue(it.target.isNotBlank()); assertTrue(it.roman.isNotBlank())
        }
    }

    @Test fun answersIgnoreCompositionForm() {
        val c = Challenge("কো", "kO")
        assertTrue(isCorrect("কো", c))
        assertFalse(isCorrect("কা", c))
    }

    @Test fun starsDropWithHelp() {
        assertEquals(3, starsFor(0, 0)); assertEquals(2, starsFor(1, 1)); assertEquals(1, starsFor(3, 0))
    }
}
