package com.itangcent.idea.plugin.utils

import com.google.inject.Inject
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [AbstractStorage]
 */
abstract class AbstractStorageTest : AdvancedContextTest() {

    @Inject
    lateinit var storage: Storage

    abstract val storageClass: KClass<out Storage>

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Storage::class) { it.with(storageClass) }
    }

    @Test
    fun testStorage() {
        //test empty storage
        assertNull(storage.get("a"))
        assertNull(storage.get("groupA", "a"))
        assertNull(storage.pop("a"))
        assertNull(storage.pop("groupA", "a"))
        assertNull(storage.peek("a"))
        assertNull(storage.peek("groupA", "a"))
        assertTrue(storage.keys().isEmpty())
        assertTrue(storage.keys("groupA").isEmpty())

        //test set
        storage.set("a", 1)
        storage.set("groupA", "a", "1A")
        storage.set("groupB", "a", "1B")
        assertEquals(1, storage.get("a"))
        assertEquals("1A", storage.get("groupA", "a"))
        assertEquals("1B", storage.get("groupB", "a"))

        storage.set("a", null)
        storage.set("groupA", "a", null)
        storage.set("groupB", "a", null)
        assertNull(storage.get("a"))
        assertNull(storage.get("groupA", "a"))
        assertNull(storage.get("groupB", "a"))


        //test push
        storage.push("b", 2)
        storage.push("b", 3)
        storage.push("groupA", "b", "2A")
        storage.push("groupA", "b", "3A")
        storage.push("groupB", "b", "2B")
        storage.push("groupB", "b", "3B")
        assertEquals(3, storage.peek("b"))
        assertEquals("3A", storage.peek("groupA", "b"))
        assertEquals("3B", storage.peek("groupB", "b"))
        assertEquals(3, storage.pop("b"))
        assertEquals("3A", storage.pop("groupA", "b"))
        assertEquals("3B", storage.pop("groupB", "b"))
        assertEquals(2, storage.peek("b"))
        assertEquals("2A", storage.peek("groupA", "b"))
        assertEquals("2B", storage.peek("groupB", "b"))
        storage.remove("b")
        storage.remove("groupA", "b")
        storage.remove("groupB", "b")
        assertNull(storage.peek("b"))
        assertNull(storage.peek("groupA", "b"))
        assertNull(storage.peek("groupB", "b"))

        storage.set("c", 3)
        storage.set("groupA", "c", "3A")
        storage.set("groupB", "c", "3B")

        storage.clear()
        assertTrue(storage.keys().isEmpty())
        assertFalse(storage.keys("groupA").isEmpty())
        assertFalse(storage.keys("groupB").isEmpty())

        storage.clear("groupA")
        assertTrue(storage.keys().isEmpty())
        assertTrue(storage.keys("groupA").isEmpty())
        assertFalse(storage.keys("groupB").isEmpty())


    }

    @Test
    fun crossThread() {
        actionContext.withBoundary {
            actionContext.runAsync {
                storage.set("a", 1)
                repeat(50) {
                    storage.push("share", it * 2)
                }
            }
            actionContext.runAsync {
                storage.set("b", 2)
                repeat(50) {
                    storage.push("share", it * 2 + 1)
                }
            }
        }
        assertEquals(1, storage.get("a"))
        assertEquals(2, storage.get("b"))
        val share = storage.get("share") as Collection<*>
        assertEquals(100, share.size)
        assertEquals((0..99).toSet(), share.toSet())
        assertEquals(setOf("a", "b", "share"), storage.keys().toSet())
    }

    @Test
    fun testWrongType() {
        //test push element to non-collection
        storage.set("a", 1)
        assertEquals(1, storage.get("a"))
        storage.push("a", 2)
        assertEquals(2, storage.peek("a"))

        //test push element to collection(not linked list)
        storage.set("b", listOf(1))
        assertEquals(listOf(1), storage.get("b"))
        storage.push("b", 2)
        assertEquals(listOf(1, 2), storage.get("b"))

        //test push element to linked list
        storage.set("c", LinkedList<Any>())
        storage.push("c", 1)
        assertEquals(1, storage.peek("c"))
        storage.push("c", 2)
        assertEquals(2, storage.peek("c"))
    }
}