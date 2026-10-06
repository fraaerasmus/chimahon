package eu.kanade.tachiyomi.ui.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeWatchHistoryTest {

    private val base = "https://s.youtube.com/api/stats/watchtime?docid=abc&cl=1&el=shorts&len=120.5"

    @Test
    fun watchTimePingCarriesSegmentAndMarksTheLastOne() {
        val url = YouTubeWatchHistory.statsUrl(base, cpn = "cpn123", position = 42.5f, segmentStart = 30f, final = true)

        val query = url.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals("2", query["ver"])
        assertEquals("cpn123", query["cpn"])
        assertEquals("42.5", query["cmt"])
        assertEquals("30.0", query["st"])
        assertEquals("42.5", query["et"])
        assertEquals("1", query["final"])
        assertEquals("detailpage", query["el"])
        assertEquals("abc", query["docid"])
    }

    @Test
    fun playbackPingHasNoSegment() {
        val url = YouTubeWatchHistory.statsUrl(base, cpn = "cpn123", position = 0f)

        assertTrue("st=" !in url && "et=" !in url && "final=" !in url)
        assertTrue("cmt=0.0" in url)
    }

    @Test
    fun trackingUrlsComeFromThePlayerResponse() {
        val json = """
            {"playbackTracking":{
              "videostatsPlaybackUrl":{"baseUrl":"https://s.youtube.com/api/stats/playback?docid=abc"},
              "videostatsWatchtimeUrl":{"baseUrl":"https://s.youtube.com/api/stats/watchtime?docid=abc"},
              "ptrackingUrl":{"baseUrl":"https://www.youtube.com/ptracking?video_id=abc"}}}
        """.trimIndent()

        assertEquals(
            "https://s.youtube.com/api/stats/playback?docid=abc" to "https://s.youtube.com/api/stats/watchtime?docid=abc",
            YouTubeWatchHistory.trackingUrls(json),
        )
        assertNull(YouTubeWatchHistory.trackingUrls("""{"playabilityStatus":{"status":"ERROR"}}"""))
    }

    @Test
    fun sapisidHashIsTimestampAndSha1() {
        val hash = YouTubeWatchHistory.sapisidHash("secret", "https://www.youtube.com", nowSeconds = 1700000000)

        assertEquals("1700000000_", hash.substring(0, 11))
        assertEquals(40, hash.substringAfter('_').length)
    }
}
