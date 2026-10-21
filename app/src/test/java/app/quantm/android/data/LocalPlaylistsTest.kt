package app.quantm.android.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalPlaylistsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "local-playlists.json").delete()
    }

    /** What desktop Quantm's Export writes (src/js/local-playlists.js) */
    private val desktopExport = """
        {
          "version": 1,
          "playlists": [
            {
              "id": "qabc123",
              "title": "Gym",
              "description": "",
              "createdAt": 1729000000000,
              "updatedAt": 1729000000001,
              "tracks": [
                {
                  "id": 11, "title": "One", "duration": 200000,
                  "permalink_url": "https://soundcloud.com/a/one", "artwork_url": null,
                  "user": { "id": 3, "username": "A", "avatar_url": null },
                  "downloadable": false, "has_downloads_left": true,
                  "media": { "transcodings": [ { "format": { "protocol": "hls" } } ] }
                },
                {
                  "id": 12, "title": "Two", "duration": 100000, "permalink_url": null, "artwork_url": null,
                  "user": { "id": 3, "username": "A", "avatar_url": null },
                  "downloadable": false, "has_downloads_left": true, "media": { "transcodings": [] }
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun importsDesktopExports() {
        val store = LocalPlaylists(context)
        assertEquals(1, store.importJson(desktopExport))
        val gym = store.playlists.value.single()
        assertEquals("qabc123", gym.id)
        assertEquals("Gym", gym.title)
        assertEquals(listOf(11L, 12L), gym.tracks.map { it.id })
        assertEquals("A", gym.tracks[0].artist)
    }

    @Test
    fun exportRoundTripsAndSurvivesRestart() {
        val store = LocalPlaylists(context)
        store.importJson(desktopExport)
        val id = store.playlists.value.single().id
        store.move(id, 0, 1)
        store.rename(id, "Gym 2")

        val reopened = LocalPlaylists(context)
        assertEquals("Gym 2", reopened.get(id)!!.title)
        assertEquals(listOf(12L, 11L), reopened.get(id)!!.tracks.map { it.id })

        // importing our own export again adds a copy with a new id
        val fresh = LocalPlaylists(context)
        assertEquals(1, fresh.importJson(reopened.exportJson()))
        assertEquals(2, fresh.playlists.value.size)
        assertEquals(2, fresh.playlists.value.map { it.id }.toSet().size)
    }

    @Test
    fun addingTheSameSongTwiceDoesNothing() {
        val store = LocalPlaylists(context)
        val song = Track(id = 1, title = "Song", user = User(id = 2, username = "B"))
        val p = store.create("Mine")
        assertTrue(store.add(p.id, song))
        assertFalse(store.add(p.id, song))
        assertEquals(1, store.get(p.id)!!.tracks.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherFiles() {
        LocalPlaylists(context).importJson("""{"version": 2, "playlists": []}""")
    }
}
