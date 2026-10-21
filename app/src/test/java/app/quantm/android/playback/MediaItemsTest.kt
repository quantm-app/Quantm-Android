package app.quantm.android.playback

import androidx.media3.common.MimeTypes
import app.quantm.android.data.Format
import app.quantm.android.data.Media
import app.quantm.android.data.Track
import app.quantm.android.data.Transcoding
import app.quantm.android.data.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaItemsTest {
    private fun track(vararg protocols: String) = Track(
        id = 42, title = "Song", duration = 1000, user = User(id = 7, username = "Artist"),
        media = Media(protocols.map { Transcoding(format = Format(protocol = it)) }),
    )

    @Test
    fun hlsByDefault() {
        val item = MediaItems.playable(MediaItems.from(track("hls", "progressive")), offline = null)
        assertEquals("quantm://track/42?hls=1", item.localConfiguration!!.uri.toString())
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration!!.mimeType)
    }

    @Test
    fun progressiveWhenThatsAllThereIs() {
        val item = MediaItems.playable(MediaItems.from(track("progressive")), offline = null)
        assertEquals("quantm://track/42?hls=0", item.localConfiguration!!.uri.toString())
        assertEquals(MimeTypes.AUDIO_MPEG, item.localConfiguration!!.mimeType)
    }

    @Test
    fun downloadsPlayFromTheirFile() {
        val file = File.createTempFile("quantm", ".mp3")
        val item = MediaItems.playable(MediaItems.from(track("hls")), offline = file)
        assertEquals("file", item.localConfiguration!!.uri.scheme)
        assertNull(item.localConfiguration!!.mimeType)
    }

    @Test
    fun itemsCarryEnoughToRebuildTheSong() {
        val back = MediaItems.trackOf(MediaItems.from(track("hls")))
        assertEquals(42L, back.id)
        assertEquals("Song", back.title)
        assertEquals(7L, back.user!!.id)
        assertEquals("Artist", back.artist)
    }
}
