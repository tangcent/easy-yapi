package com.itangcent.idea.binder

import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@DisabledOnOs(OS.WINDOWS)
internal class DbBeanBinderTest : AdvancedContextTest() {

    @Test
    fun testBeanBinder() {
        //init binder
        val dbBeanBinderFactory = DbBeanBinderFactory("$tempDir${File.separator}simple.db")
        { NULL }
        val beanBinder = dbBeanBinderFactory.getBeanBinder("1-1")
        assertNull(beanBinder.tryRead())
        assertSame(NULL, beanBinder.read())

        //save data{1,1}
        val data = DbBeanBinderTestData()
        data.x = 1
        data.y = 1
        beanBinder.save(data)
        assertEquals(data, beanBinder.read())

        //save null
        beanBinder.save(null)
        assertNull(beanBinder.tryRead())

        //save then delete
        beanBinder.save(data)
        dbBeanBinderFactory.deleteBinder("1-1")
        assertNull(beanBinder.tryRead())
    }
}

class DbBeanBinderTestData {
    var x: Int? = null
    var y: Int? = null
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DbBeanBinderTestData

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x ?: 0
        result = 31 * result + (y ?: 0)
        return result
    }

}

val NULL = DbBeanBinderTestData()