package net.pladum.tablero.tv

import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLanguageTest {
    private val bundled = listOf(Locale.forLanguageTag("en"), Locale.forLanguageTag("es"))

    @Test
    fun `a bundled choice is applied`() {
        assertEquals(Locale.forLanguageTag("es"), resolveChosen("es", bundled))
    }

    @Test
    fun `nothing chosen follows the device`() {
        assertNull(resolveChosen(null, bundled))
    }

    @Test
    fun `a choice that is no longer bundled follows the device`() {
        assertNull(resolveChosen("pt", bundled))
    }

    @Test
    fun `languages are named in their own language, capitalized`() {
        assertEquals("English", Locale.forLanguageTag("en").nativeName())
        assertEquals("Español", Locale.forLanguageTag("es").nativeName())
    }
}
