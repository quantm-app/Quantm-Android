package app.quantm.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private fun track(transcodings: String) = QuantmJson.decodeFromString<Track>(
        """{"id": 5, "title": "Song", "duration": 180000, "artwork_url": "https://i1.sndcdn.com/a-large.jpg",
            "user": {"id": 9, "username": "Artist", "unknown_field": true},
            "media": {"transcodings": [$transcodings]}, "has_downloads_left": false, "downloadable": true}"""
    )

    private fun t(protocol: String, mime: String = "audio/mpeg", snipped: Boolean = false) =
        """{"url": "https://api-v2.soundcloud.com/x", "snipped": $snipped, "format": {"protocol": "$protocol", "mime_type": "$mime"}}"""

    @Test
    fun decodesSnakeCaseAndIgnoresUnknownFields() {
        val track = track(t("hls"))
        assertEquals("Artist", track.artist)
        assertEquals(180000L, track.duration)
        assertEquals("https://i1.sndcdn.com/a-large.jpg", track.artworkUrl)
        assertFalse("no downloads left", track.isDownloadable)
    }

    @Test
    fun onlyEncryptedStreamsMeansCopyProtected() {
        assertTrue(track(t("ctr-encrypted-hls") + "," + t("cbc-encrypted-hls")).isProtected)
        assertFalse(track(t("ctr-encrypted-hls") + "," + t("hls")).isProtected)
        assertFalse("no transcodings at all isn't marked protected", track("").isProtected)
    }

    @Test
    fun snippedOnlyIsAPreview() {
        assertTrue(track(t("hls", snipped = true)).isPreview)
        assertFalse(track(t("hls", snipped = true) + "," + t("progressive")).isPreview)
    }

    @Test
    fun stubTracksAreRecognized() {
        assertTrue(QuantmJson.decodeFromString<Track>("""{"id": 1, "kind": "track"}""").isStub)
    }
}
