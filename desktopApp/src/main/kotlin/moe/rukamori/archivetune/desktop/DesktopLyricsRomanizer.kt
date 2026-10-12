/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.desktop

import com.ibm.icu.text.Transliterator
import java.lang.Character.UnicodeScript

/**
 * Best-effort offline romanization used when a desktop lyrics provider does not ship a romanized line.
 * Japanese kanji mixed with kana is left alone because generic Han-to-Latin chooses Chinese readings
 * and would produce a misleading Japanese romanization. Provider-supplied romanization always wins.
 */
internal object DesktopLyricsRomanizer {
    fun romanize(value: String): String? {
        val scripts = value.codePoints().toArray()
            .filter { Character.isLetter(it) }
            .map { UnicodeScript.of(it) }
            .toSet()
        if (scripts.isEmpty() || scripts.all { it == UnicodeScript.LATIN }) return null
        val hasHan = UnicodeScript.HAN in scripts
        val hasKana = UnicodeScript.HIRAGANA in scripts || UnicodeScript.KATAKANA in scripts
        if (hasHan && hasKana) return null
        val transliteratorId = when {
            UnicodeScript.HANGUL in scripts -> "Hangul-Latin"
            hasKana -> "Hiragana-Latin; Katakana-Latin"
            hasHan -> "Han-Latin"
            UnicodeScript.DEVANAGARI in scripts -> "Devanagari-Latin"
            UnicodeScript.CYRILLIC in scripts -> "Cyrillic-Latin"
            UnicodeScript.GREEK in scripts -> "Greek-Latin"
            UnicodeScript.ARABIC in scripts -> "Arabic-Latin"
            UnicodeScript.HEBREW in scripts -> "Hebrew-Latin"
            UnicodeScript.THAI in scripts -> "Thai-Latin"
            UnicodeScript.LAO in scripts -> "Lao-Latin"
            else -> "Any-Latin"
        }
        return runCatching {
            Transliterator.getInstance(transliteratorId).transliterate(value)
                .replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() && it != value }
        }.getOrNull()
    }
}
