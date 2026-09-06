package com.itangcent.easyapi.core.dashboard

import org.junit.Assert.*
import org.junit.Test

class RequestEditCacheServiceLogicTest {

    // ── orphanKeys ────────────────────────────────────────────────────────────

    @Test
    fun `orphanKeys returns keys whose class is absent`() {
        val cached = setOf(
            "com.foo.LiveController#get",
            "com.foo.DeletedController#post",
            "com.foo.DeletedController#list"
        )
        val live = setOf("com.foo.LiveController")

        val orphans = RequestEditCacheService.orphanKeys(cached, live)

        assertEquals(setOf("com.foo.DeletedController#post", "com.foo.DeletedController#list"), orphans)
    }

    @Test
    fun `orphanKeys keeps methods of a still-present class`() {
        val cached = setOf("com.foo.LiveController#get", "com.foo.LiveController#post")
        val live = setOf("com.foo.LiveController")

        val orphans = RequestEditCacheService.orphanKeys(cached, live)

        assertTrue(orphans.isEmpty())
    }

    @Test
    fun `orphanKeys treats empty class prefix as orphan`() {
        val cached = setOf("", "#method", "com.foo.Live#get")
        val live = setOf("com.foo.Live")

        val orphans = RequestEditCacheService.orphanKeys(cached, live)

        // "" and "#method" have no class name -> orphaned; "com.foo.Live#get" is live.
        assertTrue(orphans.contains(""))
        assertTrue(orphans.contains("#method"))
        assertFalse(orphans.contains("com.foo.Live#get"))
    }

    @Test
    fun `orphanKeys returns all keys when no class is live`() {
        val cached = setOf("com.foo.A#m1", "com.foo.B#m2")
        val orphans = RequestEditCacheService.orphanKeys(cached, emptySet())
        assertEquals(cached, orphans)
    }

    @Test
    fun `orphanKeys returns empty for empty cached keys`() {
        assertTrue(RequestEditCacheService.orphanKeys(emptySet(), setOf("com.foo.A")).isEmpty())
    }
}
