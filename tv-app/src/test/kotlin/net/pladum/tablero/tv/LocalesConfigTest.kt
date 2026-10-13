package net.pladum.tablero.tv

import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals

/** Unit tests run from the module directory. */
private val RES = File("src/main/res")
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val DEFAULT_LANGUAGE = "en"

/** Resource folder qualifiers that are only a locale: `es`, `pt-rBR`, `b+sr+Latn`. */
private val LOCALE_QUALIFIER = Regex("""[a-z]{2,3}(-r[A-Z]{2})?|b\+[A-Za-z0-9+]+""")

/**
 * `res/xml/locales_config.xml` is the single list of bundled languages; this keeps it in step with the translated
 * `strings.xml` files (docs/architecture.md → Adding a language).
 */
class LocalesConfigTest {
    @Test
    fun `locales_config lists the default language first and every translated strings file`() {
        val translated = RES.listFiles().orEmpty()
            .filter { File(it, "strings.xml").isFile }
            .mapNotNull { it.name.removePrefix("values-").takeIf(LOCALE_QUALIFIER::matches) }
            .map(::languageTag)
            .sorted()

        val listed = localesConfig()
        assertEquals(DEFAULT_LANGUAGE, listed.first(), "The first entry is the default language (res/values/)")
        assertEquals(translated, listed.drop(1).sorted(), "locales_config.xml vs values-*/strings.xml")
    }

    private fun localesConfig(): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File(RES, "xml/locales_config.xml"))
        val locales = document.getElementsByTagName("locale")
        return (0 until locales.length).map { (locales.item(it) as Element).getAttributeNS(ANDROID_NS, "name") }
    }

    /** `pt-rBR` → `pt-BR`, `b+sr+Latn` → `sr-Latn`. */
    private fun languageTag(qualifier: String): String =
        if (qualifier.startsWith("b+")) qualifier.removePrefix("b+").replace('+', '-') else qualifier.replace("-r", "-")
}
