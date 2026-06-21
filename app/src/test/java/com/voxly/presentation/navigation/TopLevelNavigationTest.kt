package com.voxly.presentation.navigation

import org.junit.Assert.*
import org.junit.Test
import org.junit.Before

class TopLevelNavigationTest {

    private lateinit var backStack: TopLevelBackStack<String>

    @Before
    fun setup() {
        backStack = TopLevelBackStack("home")
    }

    @Test
    fun `add adds entry to backStack`() {
        backStack.add("settings")
        assertTrue(backStack.backStack.contains("settings"))
    }

    @Test
    fun `removeLast returns false when only root remains`() {
        val result = backStack.removeLast()
        assertFalse(result)
    }

    @Test
    fun `removeLast removes from backStack`() {
        backStack.add("details")
        backStack.add("player")
        val result = backStack.removeLast()
        assertTrue(result)
        assertFalse(backStack.backStack.contains("player"))
    }

    @Test
    fun `addTopLevel switches to new tab`() {
        backStack.addTopLevel("albums")
        assertEquals("albums", backStack.topLevelKey)
    }

    @Test
    fun `addTopLevel preserves existing stack on tab switch`() {
        backStack.add("details")
        backStack.addTopLevel("albums")
        backStack.addTopLevel("home")
        assertEquals("home", backStack.topLevelKey)
        assertTrue(backStack.backStack.contains("details"))
    }

    @Test
    fun `removeLast on multi-tab stack removes from active tab`() {
        backStack.addTopLevel("albums")
        backStack.add("album1")
        backStack.addTopLevel("home")
        backStack.add("nowplaying")
        backStack.removeLast()
        assertFalse(backStack.backStack.contains("nowplaying"))
        assertTrue(backStack.backStack.contains("album1"))
    }
}
