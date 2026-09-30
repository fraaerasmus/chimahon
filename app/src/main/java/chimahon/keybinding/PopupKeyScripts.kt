package chimahon.keybinding

/**
 * What the keys run in the dictionary popup's page. They go through what the page already has, its
 * own entry navigation and the buttons of an entry, so nothing is added to the page itself.
 */
object PopupKeyScripts {
    /** [by] entries on, negative for back. */
    fun entry(by: Int) = "window.DictionaryRenderer?.navigate($by);"

    /** [by] screens down, negative for up. */
    fun scroll(by: Int) = "window.scrollBy(0, window.innerHeight * 0.8 * $by);"

    val PLAY_WORD_AUDIO = clickInCurrentEntry(".word-audio-btn")
    val MINE_ENTRY = clickInCurrentEntry(".anki-add-btn")

    /** The current entry is the one nearest the top of the view, which is how the page navigates. */
    private fun clickInCurrentEntry(selector: String) = """
        (function () {
          var current = null, nearest = Infinity;
          document.querySelectorAll('.entry').forEach(function (entry) {
            var distance = Math.abs(entry.getBoundingClientRect().top);
            if (distance < nearest) { nearest = distance; current = entry; }
          });
          var button = current && current.querySelector('$selector');
          if (button) button.click();
        })();
    """.trimIndent()
}
