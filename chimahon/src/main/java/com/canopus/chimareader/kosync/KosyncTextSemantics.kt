package com.canopus.chimareader.kosync

/**
 * The ttu character alphabet, identical to the class
 * `EpubBook.getChapterCharacters` filters with. Reader progress is converted to and from character
 * counts with that alphabet, so the XPointer mapping has to count the same characters or the
 * position it picks would drift from the one the reader reports.
 */
object KosyncTextSemantics {
    private val NotCounted =
        Regex("[^0-9A-Za-z○◯々-〇〻ぁ-ゖゝ-ゞァ-ヺー０-９Ａ-Ｚａ-ｚｦ-ﾝ\\p{IsHan}\\p{IsHangul}]")

    fun countChars(text: CharSequence): Int {
        val filtered = NotCounted.replace(text.toString(), "")
        return filtered.codePointCount(0, filtered.length)
    }
}
