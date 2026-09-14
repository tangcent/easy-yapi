package com.itangcent.easyapi.core.psi.type

import com.itangcent.easyapi.core.psi.model.ObjectModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins [ResolvedType.declaredText] — the accessor that keeps `Integer` and `int` apart.
 *
 * [ResolvedType.qualifiedName] answers `int` for both, which is correct for the things it feeds
 * (rule keys, script `type().name()`) but destroys the one fact a model recording its origin needs
 * to keep. `declaredText` is the second accessor for exactly that case; this file pins both the
 * difference and the (deliberate) sameness everywhere else, so a future "unify the two" refactor
 * fails here rather than silently degrading `ObjectModel.ref` back to the collapsed spelling.
 */
class ResolvedTypeDeclaredTextTest {

    @Test
    fun `a primitive keeps the keyword and a boxed primitive keeps the wrapper fqn`() {
        assertEquals("int", ResolvedType.PrimitiveType(PrimitiveKind.INT).declaredText())
        assertEquals(
            "java.lang.Integer",
            ResolvedType.PrimitiveType(PrimitiveKind.INT, boxed = true).declaredText()
        )
        assertEquals("char", ResolvedType.PrimitiveType(PrimitiveKind.CHAR).declaredText())
        assertEquals(
            "java.lang.Character",
            ResolvedType.PrimitiveType(PrimitiveKind.CHAR, boxed = true).declaredText()
        )
        assertEquals("boolean", ResolvedType.PrimitiveType(PrimitiveKind.BOOLEAN).declaredText())
        assertEquals(
            "java.lang.Boolean",
            ResolvedType.PrimitiveType(PrimitiveKind.BOOLEAN, boxed = true).declaredText()
        )
    }

    @Test
    fun `declaredText and qualifiedName deliberately disagree on a boxed primitive`() {
        val boxed = ResolvedType.PrimitiveType(PrimitiveKind.INT, boxed = true)
        assertEquals("int", boxed.qualifiedName())
        assertEquals("java.lang.Integer", boxed.declaredText())
    }

    @Test
    fun `void has no wrapper, so boxed falls back to the keyword`() {
        assertEquals("void", ResolvedType.PrimitiveType(PrimitiveKind.VOID).declaredText())
        assertEquals("void", ResolvedType.PrimitiveType(PrimitiveKind.VOID, boxed = true).declaredText())
    }

    @Test
    fun `every other kind of type keeps its qualifiedName`() {
        assertEquals("com.acme.User", ResolvedType.UnresolvedType("com.acme.User").declaredText())
        assertEquals(
            "X[]",
            ResolvedType.ArrayType(ResolvedType.UnresolvedType("X")).declaredText()
        )
        assertEquals(
            "? extends X",
            ResolvedType.WildcardType(ResolvedType.UnresolvedType("X"), null).declaredText()
        )
    }

    @Test
    fun `a marker keeps its spelling as the declaration while the marker answers qualifiedName`() {
        // The file case: the resolver replaced the class with `__file__` before a ClassType could
        // form, so `qualifiedName` is the marker and only `declaration` remembers the class.
        val file = ResolvedType.UnresolvedType(
            "__file__",
            "org.springframework.web.multipart.MultipartFile"
        )
        assertEquals("__file__", file.qualifiedName())
        assertEquals("org.springframework.web.multipart.MultipartFile", file.declaredText())

        // The configured-scalar case: same shape, the target word is the marker.
        val mapped = ResolvedType.UnresolvedType("date", "java.util.Date")
        assertEquals("date", mapped.qualifiedName())
        assertEquals("java.util.Date", mapped.declaredText())
    }

    @Test
    fun `models default to carrying no ref`() {
        assertNull(ObjectModel.single(IrType.STRING).ref)
        assertNull(ObjectModel.array(ObjectModel.single(IrType.STRING)).ref)
        assertNull(ObjectModel.map(ObjectModel.single(IrType.STRING), ObjectModel.single(IrType.OBJECT)).ref)
        assertNull(ObjectModel.emptyObject().ref)
        assertNull(ObjectModel.nullValue().ref)
    }
}
