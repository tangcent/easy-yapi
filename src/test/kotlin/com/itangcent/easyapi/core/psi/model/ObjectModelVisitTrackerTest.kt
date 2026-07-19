package com.itangcent.easyapi.core.psi.model

import org.junit.Assert.*
import org.junit.Test

class ObjectModelVisitTrackerTest {

    @Test
    fun testTryEnter_allowsEntryUnderLimit() {
        val tracker = ObjectModelVisitTracker()
        val obj = ObjectModel.Object(emptyMap())

        assertTrue("First entry should be allowed", tracker.tryEnter(obj))
        assertTrue("Second entry should be allowed (limit = 2)", tracker.tryEnter(obj))
    }

    @Test
    fun testTryEnter_blocksEntryOverLimit() {
        val tracker = ObjectModelVisitTracker()
        val obj = ObjectModel.Object(emptyMap())

        tracker.tryEnter(obj)
        tracker.tryEnter(obj)
        assertFalse("Third entry should be blocked (limit = 2)", tracker.tryEnter(obj))
    }

    @Test
    fun testExit_restoresCountForSiblingVisits() {
        val tracker = ObjectModelVisitTracker()
        val shared = ObjectModel.Object(emptyMap())

        // First visit: enter + exit
        assertTrue(tracker.tryEnter(shared))
        tracker.exit(shared)

        // Count should be restored to 0, so sibling visit is allowed twice again
        assertTrue("Sibling visit after exit should be allowed", tracker.tryEnter(shared))
        assertTrue("Second sibling visit should be allowed", tracker.tryEnter(shared))
        assertFalse("Third visit should be blocked", tracker.tryEnter(shared))
    }

    @Test
    fun testExit_restoresCountAfterNestedReentry() {
        val tracker = ObjectModelVisitTracker()
        val obj = ObjectModel.Object(emptyMap())

        // Root visit
        assertTrue(tracker.tryEnter(obj))
        // Nested re-encounter (e.g. self-reference): allowed once more
        assertTrue(tracker.tryEnter(obj))
        assertFalse("Third nested visit should be blocked", tracker.tryEnter(obj))
        tracker.exit(obj) // exit the nested visit

        // After exiting the nested visit, one more entry is allowed again
        assertTrue("Entry after exiting nested visit should be allowed", tracker.tryEnter(obj))
        tracker.exit(obj) // exit the new visit
        tracker.exit(obj) // exit the root visit
    }

    @Test
    fun testTryEnter_independentObjectsTrackedSeparately() {
        val tracker = ObjectModelVisitTracker()
        val a = ObjectModel.Object(emptyMap())
        val b = ObjectModel.Object(emptyMap())

        assertTrue(tracker.tryEnter(a))
        assertTrue(tracker.tryEnter(b))
        // Each object has its own count
        assertTrue(tracker.tryEnter(a))
        assertTrue(tracker.tryEnter(b))
    }

    @Test
    fun testWithVisit_runsBlockOnFirstEntry() {
        val tracker = ObjectModelVisitTracker()
        val obj = ObjectModel.Object(emptyMap())

        val result = tracker.withVisit(obj) { "expanded" }

        assertEquals("expanded", result)
    }

    @Test
    fun testWithVisit_returnsNullWhenLimitHit() {
        val tracker = ObjectModelVisitTracker()
        val obj = ObjectModel.Object(emptyMap())

        tracker.tryEnter(obj)
        tracker.tryEnter(obj)

        val result = tracker.withVisit(obj) { "should not run" }

        assertNull("withVisit should return null when limit is hit", result)
    }

    @Test
    fun testWithVisit_restoresCountOnNormalExit() {
        val tracker = ObjectModelVisitTracker()
        val obj = ObjectModel.Object(emptyMap())

        tracker.withVisit(obj) { /* first visit */ }
        tracker.withVisit(obj) { /* second visit */ }

        // After two withVisit calls, count should be back to 0
        assertTrue("Count should be restored after withVisit exits", tracker.tryEnter(obj))
        assertTrue(tracker.tryEnter(obj))
        assertFalse(tracker.tryEnter(obj))
    }

    @Test
    fun testWithVisit_restoresCountOnException() {
        val tracker = ObjectModelVisitTracker()
        val obj = ObjectModel.Object(emptyMap())

        try {
            tracker.withVisit(obj) { throw RuntimeException("boom") }
        } catch (e: RuntimeException) {
            // expected
        }

        // Count must be restored even though the block threw
        assertTrue(
            "Count should be restored after exception in withVisit",
            tracker.tryEnter(obj)
        )
        assertTrue(tracker.tryEnter(obj))
        assertFalse(tracker.tryEnter(obj))
    }

    @Test
    fun testWithVisit_nestedCallsRespectLimit() {
        val tracker = ObjectModelVisitTracker()
        val obj = ObjectModel.Object(emptyMap())

        // Simulate a self-referencing object: root visit + one nested visit
        val outer = tracker.withVisit(obj) {
            val inner = tracker.withVisit(obj) {
                val blocked = tracker.withVisit(obj) { "third level" }
                assertNull("Third level should be blocked", blocked)
                "second level"
            }
            assertEquals("second level", inner)
            "first level"
        }

        assertEquals("first level", outer)
    }

    @Test
    fun testExit_withoutEntry_isNoOp() {
        val tracker = ObjectModelVisitTracker()
        val obj = ObjectModel.Object(emptyMap())

        // Calling exit without a prior tryEnter should not throw
        tracker.exit(obj)

        // And the object should still be enterable up to the limit
        assertTrue(tracker.tryEnter(obj))
        assertTrue(tracker.tryEnter(obj))
        assertFalse(tracker.tryEnter(obj))
    }
}
