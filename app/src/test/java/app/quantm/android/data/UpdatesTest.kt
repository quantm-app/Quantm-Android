package app.quantm.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    private fun newer(a: String, b: String) = Updates.newer(a, b)

    @Test
    fun releasesCompareByNumber() {
        assertTrue(newer("1.0.1", "1.0.0"))
        assertTrue(newer("1.10.0", "1.9.9"))
        assertTrue(newer("2.0", "1.99.99"))
        assertFalse(newer("1.0.0", "1.0.0"))
        assertFalse(newer("0.9.0", "1.0.0"))
    }

    @Test
    fun releaseBeatsItsDevBuilds() {
        assertTrue(newer("1.0.1", "1.0.1-dev.7"))
        assertFalse(newer("1.0.1-dev.7", "1.0.1"))
    }

    @Test
    fun devBuildsCompareByNumberNotText() {
        assertTrue(newer("1.0.1-dev.10", "1.0.1-dev.9"))
        assertTrue(newer("1.0.2-dev.1", "1.0.1"))
        assertFalse(newer("1.0.1-dev.2", "1.0.1-dev.2"))
    }
}
