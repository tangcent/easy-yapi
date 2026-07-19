package com.itangcent.easyapi.core.settings.state

import com.itangcent.easyapi.core.settings.PostmanExportMode
import org.junit.Assert.*
import org.junit.Test

class ProjectSettingsStateTest {

    @Test
    fun testDefaultState() {
        val state = ProjectSettingsState()
        val s = state.state
        assertNotNull(s)
        assertNull(s.postmanWorkspace)
        assertEquals(PostmanExportMode.CREATE_NEW.name, s.postmanExportMode)
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

    @Test
    fun testDefault_newRuleFileFields() {
        val s = ProjectSettingsState.State()
        assertArrayEquals(emptyArray(), s.disabledAutoRuleFiles)
    }

    @Test
    fun testState_copyTo_newRuleFileFields() {
        val source = ProjectSettingsState.State(
            disabledAutoRuleFiles = arrayOf("/proj/.easy.api.config")
        )
        val target = ProjectSettingsState.State()
        source.copyTo(target)
        assertArrayEquals(arrayOf("/proj/.easy.api.config"), target.disabledAutoRuleFiles)
    }

    @Test
    fun testState_equality_newRuleFileArrays() {
        val s1 = ProjectSettingsState.State(disabledAutoRuleFiles = arrayOf("/b"))
        val s2 = ProjectSettingsState.State(disabledAutoRuleFiles = arrayOf("/b"))
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
        val s3 = ProjectSettingsState.State(disabledAutoRuleFiles = arrayOf("/c"))
        assertNotEquals(s1, s3)
    }

    @Test
    fun testLoadState_preservesNewRuleFileFields() {
        val state = ProjectSettingsState()
        val newState = ProjectSettingsState.State(
            disabledAutoRuleFiles = arrayOf("/p/d1")
        )
        state.loadState(newState)
        val loaded = state.state
        assertArrayEquals(arrayOf("/p/d1"), loaded.disabledAutoRuleFiles)
    }
}
