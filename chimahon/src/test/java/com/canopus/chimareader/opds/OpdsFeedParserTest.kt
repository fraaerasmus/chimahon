package com.canopus.chimareader.opds

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpdsFeedParserTest {
    private val calibreFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/" xmlns:opds="http://opds-spec.org/2010/catalog">
          <title>calibre Library</title>
          <id>urn:calibre:main</id>
          <link rel="search" type="application/atom+xml" href="/opds/search/{searchTerms}?library_id=calibre"/>
          <link rel="next" type="application/atom+xml" href="/opds/navcatalog/4e6577657374?library_id=calibre&amp;offset=25"/>
          <entry>
            <title>By Authors</title>
            <id>calibre:authors</id>
            <link type="application/atom+xml;profile=opds-catalog;kind=navigation" href="/opds/navcatalog/4f61757468?library_id=calibre" rel="subsection"/>
          </entry>
          <entry>
            <title>また、同じ夢を見ていた</title>
            <id>urn:uuid:1</id>
            <author><name>住野よる</name></author>
            <summary>A &lt;b&gt;novel&lt;/b&gt;   about dreams.</summary>
            <link type="image/jpeg" href="/get/cover/18/calibre" rel="http://opds-spec.org/image"/>
            <link type="application/epub+zip" href="/get/epub/18/calibre" rel="http://opds-spec.org/acquisition" length="180000"/>
            <link type="application/x-mobipocket-ebook" href="/get/mobi/18/calibre" rel="http://opds-spec.org/acquisition"/>
          </entry>
          <entry>
            <title>PDF only</title>
            <id>urn:uuid:2</id>
            <link type="application/pdf" href="/get/pdf/19/calibre" rel="http://opds-spec.org/acquisition"/>
          </entry>
        </feed>
    """.trimIndent().toByteArray()

    @Test
    fun resolvesCalibreRootRelativeLinksAgainstTheCatalogUrl() {
        val feed = OpdsFeedParser.parseFeed("http://calibre.example:8083/opds?library_id=calibre", calibreFeed)

        assertEquals("calibre Library", feed.title)
        assertEquals("http://calibre.example:8083/opds/search/{searchTerms}?library_id=calibre", feed.searchTemplate)
        assertNull(feed.searchDescriptionHref)
        assertEquals("http://calibre.example:8083/opds/navcatalog/4e6577657374?library_id=calibre&offset=25", feed.nextHref)
        val navigation = feed.entries[0]
        assertEquals("http://calibre.example:8083/opds/navcatalog/4f61757468?library_id=calibre", navigation.navigationHref)
        assertNull(navigation.epubHref)
        val book = feed.entries[1]
        assertNull(book.navigationHref)
        assertEquals("http://calibre.example:8083/get/epub/18/calibre", book.epubHref)
        assertEquals(listOf("住野よる"), book.authors)
        assertEquals("A novel about dreams.", book.summary)
        val pdfOnly = feed.entries[2]
        assertNull(pdfOnly.epubHref)
        assertTrue(pdfOnly.hasOtherFormatsOnly)
    }

    @Test
    fun readsComicLinksAndCalibreSeriesFromTheContent() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>calibre Library</title>
              <entry>
                <title>Berserk, Vol. 3</title>
                <id>urn:uuid:3</id>
                <author><name>Kentaro Miura</name></author>
                <content type="xhtml">
                  <div xmlns="http://www.w3.org/1999/xhtml">RATING: ★★★★<br />TAGS: Manga, Seinen<br />SERIES: Berserk [3]<br /><p>Guts returns.</p></div>
                </content>
                <link type="application/x-cbz" href="/get/cbz/20/calibre" rel="http://opds-spec.org/acquisition" length="90000"/>
              </entry>
              <entry>
                <title>Dune: The Graphic Novel, Book 1</title>
                <id>urn:uuid:4</id>
                <summary>No series here.</summary>
                <link type="application/vnd.comicbook-rar" href="/get/cbr/21/calibre" rel="http://opds-spec.org/acquisition"/>
                <link type="application/epub+zip" href="/get/epub/21/calibre" rel="http://opds-spec.org/acquisition"/>
              </entry>
            </feed>
        """.trimIndent().toByteArray()
        val feed = OpdsFeedParser.parseFeed("http://h:8083/opds", xml)

        val berserk = feed.entries[0]
        assertEquals("Berserk", berserk.series)
        assertEquals(3.0, berserk.seriesIndex)
        assertEquals("http://h:8083/get/cbz/20/calibre", berserk.acquisitionHref(OpdsFormat.COMIC))
        assertNull(berserk.epubHref)
        assertTrue(berserk.hasOtherFormatsOnly(OpdsFormat.EPUB))
        assertEquals("cbz", OpdsFormat.COMIC.extensionFor(berserk.acquisitionLink(OpdsFormat.COMIC)?.type))

        val dune = feed.entries[1]
        assertNull(dune.series)
        assertNull(dune.seriesIndex)
        val comic = dune.acquisitionLink(OpdsFormat.COMIC)
        assertEquals("http://h:8083/get/cbr/21/calibre", comic?.href)
        assertEquals("cbr", OpdsFormat.COMIC.extensionFor(comic?.type))
        assertEquals("http://h:8083/get/epub/21/calibre", dune.epubHref)
    }

    @Test
    fun prefersCbzOverCbrWhenAnEntryOffersBoth() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>calibre Library</title>
              <entry>
                <title>Berserk, Ch. 012</title>
                <id>urn:uuid:5</id>
                <link type="application/x-cbr" href="/get/cbr/22/calibre" rel="http://opds-spec.org/acquisition"/>
                <link type="application/x-cbz" href="/get/cbz/22/calibre" rel="http://opds-spec.org/acquisition"/>
              </entry>
              <entry>
                <title>Vagabond, Ch. 003</title>
                <id>urn:uuid:6</id>
                <link type="application/vnd.comicbook-rar" href="/get/cbr/23/calibre" rel="http://opds-spec.org/acquisition"/>
                <link type="application/epub+zip" href="/get/epub/23/calibre" rel="http://opds-spec.org/acquisition"/>
                <link type="application/vnd.comicbook+zip" href="/get/cbz/23/calibre" rel="http://opds-spec.org/acquisition"/>
              </entry>
              <entry>
                <title>CBR only</title>
                <id>urn:uuid:7</id>
                <link type="application/x-cbr" href="/get/cbr/24/calibre" rel="http://opds-spec.org/acquisition"/>
              </entry>
            </feed>
        """.trimIndent().toByteArray()
        val feed = OpdsFeedParser.parseFeed("http://h:8083/opds", xml)

        assertEquals("http://h:8083/get/cbz/22/calibre", feed.entries[0].acquisitionHref(OpdsFormat.COMIC))
        assertEquals("http://h:8083/get/cbz/23/calibre", feed.entries[1].acquisitionHref(OpdsFormat.COMIC))
        assertEquals("http://h:8083/get/epub/23/calibre", feed.entries[1].epubHref)
        assertEquals("http://h:8083/get/cbr/24/calibre", feed.entries[2].acquisitionHref(OpdsFormat.COMIC))
        assertEquals("cbr", OpdsFormat.COMIC.extensionFor(feed.entries[2].acquisitionLink(OpdsFormat.COMIC)?.type))
    }

    @Test
    fun honoursXmlBaseAndRelativeHrefs() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom" xml:base="https://books.example.com/opds/">
              <title>t</title>
              <entry><title>e</title><id>1</id>
                <link type="application/epub+zip" href="files/one.epub" rel="http://opds-spec.org/acquisition/open-access"/>
              </entry>
              <link rel="search" type="application/opensearchdescription+xml" href="../search.xml"/>
            </feed>
        """.trimIndent().toByteArray()
        val feed = OpdsFeedParser.parseFeed("https://other.example.com/catalog/root.xml", xml)

        assertEquals("https://books.example.com/opds/files/one.epub", feed.entries.single().epubHref)
        assertEquals("https://books.example.com/search.xml", feed.searchDescriptionHref)
    }

    @Test
    fun buildsSearchUrlsAndParsesOpenSearchDescriptions() {
        assertEquals(
            "http://h/opds/search/%E5%A4%A2%20two?library_id=calibre",
            OpdsFeedParser.searchUrl("http://h/opds/search/{searchTerms}?library_id=calibre", "夢 two"),
        )
        val description = """
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
              <ShortName>Search</ShortName>
              <Url type="text/html" template="/search?q={searchTerms}"/>
              <Url type="application/atom+xml" template="/opds/search?q={searchTerms}"/>
            </OpenSearchDescription>
        """.trimIndent().toByteArray()
        assertEquals(
            "http://h:8083/opds/search?q={searchTerms}",
            OpdsFeedParser.parseOpenSearchDescription("http://h:8083/opds/search.xml", description),
        )
    }

    @Test
    fun resolvesHrefsThatAreNotValidUris() {
        assertEquals("http://h:8083/get/epub/My Book.epub", OpdsFeedParser.resolve("http://h:8083/opds/nav", "/get/epub/My Book.epub"))
        assertEquals("http://h:8083/opds/a b", OpdsFeedParser.resolve("http://h:8083/opds/nav", "a b"))
        assertEquals("https://x/y", OpdsFeedParser.resolve("http://h/opds", "https://x/y"))
        assertEquals("Book - Author.epub", OpdsClient.fileNameFromDisposition("attachment; filename=\"Book - Author.epub\""))
        assertEquals("夢.epub", OpdsClient.fileNameFromDisposition("attachment; filename*=UTF-8''%E5%A4%A2.epub"))
    }
}
