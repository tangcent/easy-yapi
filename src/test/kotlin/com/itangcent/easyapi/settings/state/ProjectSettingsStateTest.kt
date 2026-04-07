package com.itangcent.easyapi.settings.state

import org.junit.Assert.*
import org.junit.Test

class ProjectSettingsStateTest {

    @Test
    fun testDefaultState() {
        val state = ProjectSettingsState()
        val s = state.state
        assertNotNull(s)
        assertNull(s.postmanWorkspace)
        assertEquals(defaultPostmanExportMode(), s.postmanExportMode)
        assertNull(s.postmanCollections)
        assertTrue(s.postmanBuildExample)
        assertTrue(s.builtInConfig)
        assertNull(s.remoteConfig)
        assertNull(s.recommendConfig)
        assertNull(s.postmanToken)
    }

    @Test
    fun testLoadState() {
        val state = ProjectSettingsState()
        val newState = ProjectSettingsState.State(
            postmanWorkspace = "ws-1",
            postmanExportMode = "UPDATE_EXISTING",
            postmanCollections = "col=id",
            postmanBuildExample = false,
            builtInConfig = false,
            remoteConfig = "http://example.com",
            recommendConfig = "spring",
            postmanToken = "token-123"
        )
        state.loadState(newState)
        val loaded = state.state
        assertEquals("ws-1", loaded.postmanWorkspace)
        assertEquals("UPDATE_EXISTING", loaded.postmanExportMode)
        assertEquals("col=id", loaded.postmanCollections)
        assertFalse(loaded.postmanBuildExample)
        assertFalse(loaded.builtInConfig)
        assertEquals("http://example.com", loaded.remoteConfig)
        assertEquals("spring", loaded.recommendConfig)
        assertEquals("token-123", loaded.postmanToken)
    }

    @Test
    fun testGetState_returnsSameInstance() {
        val state = ProjectSettingsState()
        assertSame(state.state, state.state)
    }

    @Test
    fun testState_copyTo() {
        val source = ProjectSettingsState.State(
            postmanWorkspace = "ws",
            postmanExportMode = "CREATE_NEW",
            postmanCollections = "data",
            postmanBuildExample = false
        )
        val target = ProjectSettingsState.State()
        source.copyTo(target)
        assertEquals("ws", target.postmanWorkspace)
        assertEquals("CREATE_NEW", target.postmanExportMode)
        assertEquals("data", target.postmanCollections)
        assertFalse(target.postmanBuildExample)
    }
}
