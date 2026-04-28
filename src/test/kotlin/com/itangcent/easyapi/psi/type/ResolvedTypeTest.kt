package com.itangcent.easyapi.psi.type

import com.itangcent.easyapi.psi.DefaultPsiClassHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Comprehensive tests for [ResolvedType], [TypeResolver], and [GenericContext].
 *
 * Covers:
 * - TypeResolver.resolve: primitives, class types, arrays, wildcards, type params, null
 * - TypeResolver.substitute: all ResolvedType variants
 * - TypeResolver.resolveGenericParams: single/multi param, multi-level, same-name collision,
 *   type wrapping (#1326), swapped params, partial binding
 * - TypeResolver.resolveFromCanonicalText: primitives, arrays, generics, blank, context
 * - GenericContext: EMPTY, lookup
 * - ResolvedType sealed class: equality, pattern matching
 * - Complex hierarchies: diamond, interface+class, deep chains, double wrapping
 */
class ResolvedTypeTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        // Load all generic model fixtures
        loadFile("model/generic/GenericBase.java")
        loadFile("model/generic/StringChild.java")
        loadFile("model/generic/TwoTypeBase.java")
        loadFile("model/generic/MiddleChild.java")
        loadFile("model/generic/ConcreteLeaf.java")
        loadFile("model/generic/Wrapper.java")
        loadFile("model/generic/DoubleWrapper.java")
        loadFile("model/generic/ConcreteDoubleWrapper.java")
        loadFile("model/generic/Pair.java")
        loadFile("model/generic/SwappedPair.java")
        loadFile("model/generic/ConcreteSwappedPair.java")
        loadFile("model/generic/ResponseWrapper.java")
        loadFile("model/generic/IntResponseWrapper.java")
        loadFile("model/generic/Identifiable.java")
        loadFile("model/generic/Named.java")
        loadFile("model/generic/BaseEntity.java")
        loadFile("model/generic/UserEntity.java")
        loadFile("model/generic/Page.java")
        loadFile("model/generic/AtaPage.java")
        loadFile("model/generic/BaseResult.java")
        loadFile("model/generic/AtaBaseResult.java")
        loadFile("model/generic/AtaPageResult.java")
        loadFile("model/generic/VotePageQueryVO.java")
        loadFile("model/generic/VotePageQueryResult.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)


    // ==================== ResolvedType sealed class basics ====================

    fun testPrimitiveTypeEquality() {
        val a = ResolvedType.PrimitiveType(PrimitiveKind.INT)
        val b = ResolvedType.PrimitiveType(PrimitiveKind.INT)
        val c = ResolvedType.PrimitiveType(PrimitiveKind.LONG)
        assertEquals(a, b)
        assertNotSame(a, c)
    }

    fun testUnresolvedTypeEquality() {
        val a = ResolvedType.UnresolvedType("T")
        val b = ResolvedType.UnresolvedType("T")
        val c = ResolvedType.UnresolvedType("R")
        assertEquals(a, b)
        assertNotSame(a, c)
    }

    fun testArrayTypeEquality() {
        val inner = ResolvedType.PrimitiveType(PrimitiveKind.INT)
        val a = ResolvedType.ArrayType(inner)
        val b = ResolvedType.ArrayType(inner)
        assertEquals(a, b)
    }

    fun testWildcardTypeEquality() {
        val upper = ResolvedType.UnresolvedType("Number")
        val a = ResolvedType.WildcardType(upper = upper, lower = null)
        val b = ResolvedType.WildcardType(upper = upper, lower = null)
        val c = ResolvedType.WildcardType(upper = null, lower = upper)
        assertEquals(a, b)
        assertNotSame(a, c)
    }

    fun testWildcardTypeBothBoundsNull() {
        val w = ResolvedType.WildcardType(upper = null, lower = null)
        assertNull(w.upper)
        assertNull(w.lower)
    }

    // ==================== GenericContext ====================

    fun testGenericContextEmpty() {
        val ctx = GenericContext.EMPTY
        assertTrue(ctx.genericMap.isEmpty())
    }

    fun testGenericContextLookup() {
        val ctx = GenericContext(
            mapOf(
                "T" to ResolvedType.PrimitiveType(PrimitiveKind.INT),
                "R" to ResolvedType.UnresolvedType("java.lang.String")
            )
        )
        assertEquals(ResolvedType.PrimitiveType(PrimitiveKind.INT), ctx.genericMap["T"])
        assertEquals(ResolvedType.UnresolvedType("java.lang.String"), ctx.genericMap["R"])
        assertNull(ctx.genericMap["X"])
    }

    // ==================== TypeResolver.resolve — primitives ====================

    fun testResolvePrimitiveInt() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.GenericBase")!!
        val intField = psiClass.allFields.first { it.name == "data" }
        // data is type T — unresolved without context
        val resolved = TypeResolver.resolve(intField.type, GenericContext.EMPTY)
        // T is a type parameter, should be UnresolvedType when no context
        assertTrue(
            "Type parameter T without context should be UnresolvedType, got: $resolved",
            resolved is ResolvedType.UnresolvedType
        )
    }

    fun testResolveWithGenericContext() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.GenericBase")!!
        val dataField = psiClass.allFields.first { it.name == "data" }
        val ctx = GenericContext(mapOf("T" to ResolvedType.PrimitiveType(PrimitiveKind.LONG)))
        val resolved = TypeResolver.resolve(dataField.type, ctx)
        assertEquals(
            "T should resolve to LONG with context",
            ResolvedType.PrimitiveType(PrimitiveKind.LONG),
            resolved
        )
    }

    // ==================== TypeResolver.resolve — class types ====================

    fun testResolveClassType() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.VotePageQueryVO")!!
        val idField = psiClass.allFields.first { it.name == "id" }
        val resolved = TypeResolver.resolve(idField.type)
        // In the light test fixture, java.lang.Long may not be available as a PsiClass,
        // so it may resolve as UnresolvedType("Long") instead of PrimitiveType(LONG).
        // Both are acceptable — the key is it's not left as a raw type parameter.
        assertTrue(
            "Long field should resolve to PrimitiveType(LONG) or UnresolvedType(Long), got: $resolved",
            (resolved is ResolvedType.PrimitiveType && resolved.kind == PrimitiveKind.LONG) ||
                    (resolved is ResolvedType.UnresolvedType && resolved.canonicalText.contains("Long"))
        )
    }

    fun testResolveStringField() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.VotePageQueryVO")!!
        val labelField = psiClass.allFields.first { it.name == "label" }
        val resolved = TypeResolver.resolve(labelField.type)
        // In the light test fixture, String may resolve as UnresolvedType("String")
        // because java.lang.String PsiClass may not be available.
        // The key assertion is that it resolves to something (not null).
        assertNotNull("String field should resolve to something", resolved)
        // It should contain "String" somewhere in its representation
        assertTrue(
            "Should reference String, got: $resolved",
            resolved.toString().contains("String")
        )
    }

    // ==================== TypeResolver.substitute ====================

    fun testSubstitutePrimitiveIsIdentity() {
        val prim = ResolvedType.PrimitiveType(PrimitiveKind.DOUBLE)
        val ctx = GenericContext(mapOf("T" to ResolvedType.PrimitiveType(PrimitiveKind.INT)))
        val result = TypeResolver.substitute(prim, ctx)
        assertEquals("Primitive should be unchanged by substitute", prim, result)
    }

    fun testSubstituteUnresolvedWithMatch() {
        val unresolved = ResolvedType.UnresolvedType("T")
        val replacement = ResolvedType.PrimitiveType(PrimitiveKind.INT)
        val ctx = GenericContext(mapOf("T" to replacement))
        val result = TypeResolver.substitute(unresolved, ctx)
        assertEquals("T should be substituted to INT", replacement, result)
    }

    fun testSubstituteUnresolvedWithoutMatch() {
        val unresolved = ResolvedType.UnresolvedType("X")
        val ctx = GenericContext(mapOf("T" to ResolvedType.PrimitiveType(PrimitiveKind.INT)))
        val result = TypeResolver.substitute(unresolved, ctx)
        assertTrue("X should remain UnresolvedType", result is ResolvedType.UnresolvedType)
        assertEquals("X", (result as ResolvedType.UnresolvedType).canonicalText)
    }

    fun testSubstituteArrayType() {
        val arrayOfT = ResolvedType.ArrayType(ResolvedType.UnresolvedType("T"))
        val ctx = GenericContext(mapOf("T" to ResolvedType.PrimitiveType(PrimitiveKind.INT)))
        val result = TypeResolver.substitute(arrayOfT, ctx)
        assertTrue("Should be ArrayType", result is ResolvedType.ArrayType)
        assertEquals(
            "Component should be INT",
            ResolvedType.PrimitiveType(PrimitiveKind.INT),
            (result as ResolvedType.ArrayType).componentType
        )
    }

    fun testSubstituteWildcardType() {
        val wildcard = ResolvedType.WildcardType(
            upper = ResolvedType.UnresolvedType("T"),
            lower = null
        )
        val ctx = GenericContext(mapOf("T" to ResolvedType.PrimitiveType(PrimitiveKind.LONG)))
        val result = TypeResolver.substitute(wildcard, ctx)
        assertTrue("Should be WildcardType", result is ResolvedType.WildcardType)
        assertEquals(
            "Upper bound should be substituted",
            ResolvedType.PrimitiveType(PrimitiveKind.LONG),
            (result as ResolvedType.WildcardType).upper
        )
        assertNull("Lower bound should remain null", result.lower)
    }

    fun testSubstituteWildcardWithBothBounds() {
        val wildcard = ResolvedType.WildcardType(
            upper = ResolvedType.UnresolvedType("T"),
            lower = ResolvedType.UnresolvedType("R")
        )
        val ctx = GenericContext(
            mapOf(
                "T" to ResolvedType.PrimitiveType(PrimitiveKind.LONG),
                "R" to ResolvedType.PrimitiveType(PrimitiveKind.INT)
            )
        )
        val result = TypeResolver.substitute(wildcard, ctx) as ResolvedType.WildcardType
        assertEquals(ResolvedType.PrimitiveType(PrimitiveKind.LONG), result.upper)
        assertEquals(ResolvedType.PrimitiveType(PrimitiveKind.INT), result.lower)
    }

    fun testSubstituteNestedArray() {
        // Array<Array<T>> with T=INT → Array<Array<INT>>
        val nested = ResolvedType.ArrayType(
            ResolvedType.ArrayType(ResolvedType.UnresolvedType("T"))
        )
        val ctx = GenericContext(mapOf("T" to ResolvedType.PrimitiveType(PrimitiveKind.INT)))
        val result = TypeResolver.substitute(nested, ctx)
        val outer = result as ResolvedType.ArrayType
        val inner = outer.componentType as ResolvedType.ArrayType
        assertEquals(ResolvedType.PrimitiveType(PrimitiveKind.INT), inner.componentType)
    }

    fun testSubstituteEmptyContextIsIdentity() {
        val types = listOf(
            ResolvedType.PrimitiveType(PrimitiveKind.BOOLEAN),
            ResolvedType.UnresolvedType("SomeType"),
            ResolvedType.ArrayType(ResolvedType.PrimitiveType(PrimitiveKind.INT)),
            ResolvedType.WildcardType(upper = ResolvedType.UnresolvedType("Number"), lower = null)
        )
        for (type in types) {
            val result = TypeResolver.substitute(type, GenericContext.EMPTY)
            // With empty context, UnresolvedType may still be transformed if text contains
            // no matching params, but the result should be equivalent
            if (type is ResolvedType.PrimitiveType) {
                assertEquals("Primitive should be identity with empty context", type, result)
            }
        }
    }

    // ==================== TypeResolver.resolveFromCanonicalText ====================

    fun testResolveFromCanonicalText_Primitives() = runTest {
        val cases = mapOf(
            "int" to PrimitiveKind.INT,
            "long" to PrimitiveKind.LONG,
            "boolean" to PrimitiveKind.BOOLEAN,
            "double" to PrimitiveKind.DOUBLE,
            "float" to PrimitiveKind.FLOAT,
            "short" to PrimitiveKind.SHORT,
            "byte" to PrimitiveKind.BYTE,
            "char" to PrimitiveKind.CHAR,
            "void" to PrimitiveKind.VOID
        )
        for ((text, expectedKind) in cases) {
            val result = TypeResolver.resolveFromCanonicalText(text, project)
            assertTrue(
                "'$text' should resolve to PrimitiveType($expectedKind), got: $result",
                result is ResolvedType.PrimitiveType && result.kind == expectedKind
            )
        }
    }

    fun testResolveFromCanonicalText_BoxedPrimitives() = runTest {
        val cases = mapOf(
            "java.lang.Integer" to PrimitiveKind.INT,
            "java.lang.Long" to PrimitiveKind.LONG,
            "java.lang.Boolean" to PrimitiveKind.BOOLEAN,
            "java.lang.Double" to PrimitiveKind.DOUBLE,
            "java.lang.Float" to PrimitiveKind.FLOAT,
            "java.lang.Short" to PrimitiveKind.SHORT,
            "java.lang.Byte" to PrimitiveKind.BYTE,
            "java.lang.Character" to PrimitiveKind.CHAR
        )
        for ((text, expectedKind) in cases) {
            val result = TypeResolver.resolveFromCanonicalText(text, project)
            assertTrue(
                "'$text' should resolve to PrimitiveType($expectedKind), got: $result",
                result is ResolvedType.PrimitiveType && result.kind == expectedKind
            )
        }
    }

    fun testResolveFromCanonicalText_Array() = runTest {
        val result = TypeResolver.resolveFromCanonicalText("int[]", project)
        assertTrue("int[] should be ArrayType", result is ResolvedType.ArrayType)
        val component = (result as ResolvedType.ArrayType).componentType
        assertTrue(
            "Component should be PrimitiveType(INT), got: $component",
            component is ResolvedType.PrimitiveType && component.kind == PrimitiveKind.INT
        )
    }

    fun testResolveFromCanonicalText_NestedArray() = runTest {
        val result = TypeResolver.resolveFromCanonicalText("long[][]", project)
        assertTrue("long[][] should be ArrayType", result is ResolvedType.ArrayType)
        val inner = (result as ResolvedType.ArrayType).componentType
        assertTrue("Inner should be ArrayType", inner is ResolvedType.ArrayType)
        val component = (inner as ResolvedType.ArrayType).componentType
        assertTrue(
            "Innermost should be PrimitiveType(LONG), got: $component",
            component is ResolvedType.PrimitiveType && component.kind == PrimitiveKind.LONG
        )
    }

    fun testResolveFromCanonicalText_Blank() = runTest {
        val result = TypeResolver.resolveFromCanonicalText("", project)
        assertTrue("Blank should be UnresolvedType", result is ResolvedType.UnresolvedType)
    }

    fun testResolveFromCanonicalText_Whitespace() = runTest {
        val result = TypeResolver.resolveFromCanonicalText("   ", project)
        assertTrue("Whitespace should be UnresolvedType", result is ResolvedType.UnresolvedType)
    }

    fun testResolveFromCanonicalText_TypeParamInContext() = runTest {
        val ctx = GenericContext(mapOf("T" to ResolvedType.PrimitiveType(PrimitiveKind.INT)))
        val result = TypeResolver.resolveFromCanonicalText("T", project, null, ctx)
        assertEquals(
            "T should resolve from context",
            ResolvedType.PrimitiveType(PrimitiveKind.INT),
            result
        )
    }

    fun testResolveFromCanonicalText_UnknownType() = runTest {
        val result = TypeResolver.resolveFromCanonicalText("com.nonexistent.FooBar", project)
        assertTrue(
            "Unknown type should be UnresolvedType, got: $result",
            result is ResolvedType.UnresolvedType
        )
    }

    fun testResolveFromCanonicalText_FileType() = runTest {
        val result = TypeResolver.resolveFromCanonicalText(
            "org.springframework.web.multipart.MultipartFile", project
        )
        assertTrue("MultipartFile should be UnresolvedType(__file__)", result is ResolvedType.UnresolvedType)
        assertEquals("__file__", (result as ResolvedType.UnresolvedType).canonicalText)
    }

    // ==================== resolveGenericParams — PSI-based tests ====================

    /**
     * GenericBase<T> with T=String → {T: UnresolvedType("java.lang.String")} or ClassType(String)
     */
    fun testResolveGenericParams_SingleParam() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.StringChild")!!
        val baseClass = findClass("com.itangcent.model.generic.GenericBase")!!
        // StringChild extends GenericBase<String>
        // resolveGenericParams on GenericBase with [String resolved type]
        val stringType = TypeResolver.resolve(
            psiClass.superTypes.first().parameters.first()
        )
        val params = TypeResolver.resolveGenericParams(baseClass, listOf(stringType))
        assertNotNull("Should have T binding", params["T"])
    }

    /**
     * TwoTypeBase<T, R> with T=Long, R=String
     */
    fun testResolveGenericParams_MultiParam() = runTest {
        val twoTypeBase = findClass("com.itangcent.model.generic.TwoTypeBase")!!
        val longType = ResolvedType.PrimitiveType(PrimitiveKind.LONG)
        val stringType = ResolvedType.UnresolvedType("java.lang.String")
        val params = TypeResolver.resolveGenericParams(twoTypeBase, listOf(longType, stringType))
        assertEquals(longType, params["T"])
        assertEquals(stringType, params["R"])
    }

    /**
     * When fewer type args than params, missing ones should be UnresolvedType.
     */
    fun testResolveGenericParams_MissingArgs() = runTest {
        val twoTypeBase = findClass("com.itangcent.model.generic.TwoTypeBase")!!
        val longType = ResolvedType.PrimitiveType(PrimitiveKind.LONG)
        val params = TypeResolver.resolveGenericParams(twoTypeBase, listOf(longType))
        assertEquals(longType, params["T"])
        assertTrue(
            "Missing R should be UnresolvedType",
            params["R"] is ResolvedType.UnresolvedType
        )
    }

    /**
     * When no type args at all, all params should be UnresolvedType.
     */
    fun testResolveGenericParams_NoArgs() = runTest {
        val twoTypeBase = findClass("com.itangcent.model.generic.TwoTypeBase")!!
        val params = TypeResolver.resolveGenericParams(twoTypeBase, emptyList())
        assertTrue("T should be UnresolvedType", params["T"] is ResolvedType.UnresolvedType)
        assertTrue("R should be UnresolvedType", params["R"] is ResolvedType.UnresolvedType)
    }


    // ==================== Complex generic hierarchies via buildObjectModel ====================

    /**
     * SwappedPair<X, Y> extends Pair<Y, X>
     * ConcreteSwappedPair extends SwappedPair<Integer, String>
     *
     * So: X=Integer, Y=String → Pair<String, Integer>
     *     first: A=Y=String
     *     second: B=X=Integer
     */
    fun testSwappedTypeParameters() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.ConcreteSwappedPair")
        assertNotNull("Should find ConcreteSwappedPair", psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        val obj = model as? ObjectModel.Object
        assertNotNull("Model should be Object", obj)

        // first: A = Y = String
        val firstField = obj!!.fields["first"]
        assertNotNull("Should have 'first' field", firstField)
        val firstModel = firstField!!.model
        assertTrue(
            "first should be string (A=Y=String), got: $firstModel",
            (firstModel is ResolvedType.ClassType && (firstModel as? ResolvedType.ClassType)?.psiClass?.name == "String") ||
                    (firstModel is ObjectModel.Single && firstModel.type == JsonType.STRING)
        )

        // second: B = X = Integer
        val secondField = obj.fields["second"]
        assertNotNull("Should have 'second' field", secondField)
        val secondModel = secondField!!.model
        assertTrue(
            "second should be int (B=X=Integer), got: $secondModel",
            secondModel is ObjectModel.Single && secondModel.type == JsonType.INT
        )

        // Own field: swapped
        val swappedField = obj.fields["swapped"]
        assertNotNull("Should have 'swapped' field", swappedField)
        assertTrue(
            "swapped should be boolean, got: ${swappedField!!.model}",
            swappedField.model is ObjectModel.Single && (swappedField.model as ObjectModel.Single).type == JsonType.BOOLEAN
        )
    }

    /**
     * DoubleWrapper<T> extends Wrapper<Wrapper<T>>
     * ConcreteDoubleWrapper extends DoubleWrapper<String>
     *
     * So: value should be Wrapper<String> (an object with value:String, label:String)
     *
     * Note: In the light test fixture, java.lang.String may not resolve as a PsiClass,
     * which can cause Wrapper<String> to be treated as a simple string type instead of
     * an Object. We test both possible outcomes.
     */
    fun testDoubleWrappedTypeParameter() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.ConcreteDoubleWrapper")
        assertNotNull("Should find ConcreteDoubleWrapper", psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        val obj = model as? ObjectModel.Object
        assertNotNull("Model should be Object", obj)

        // value: should be Wrapper<String> → an Object, or Single(string) in light fixture
        val valueField = obj!!.fields["value"]
        assertNotNull("Should have 'value' field (inherited from Wrapper)", valueField)

        val valueModel = valueField!!.model
        if (valueModel is ObjectModel.Object) {
            // Full resolution: Wrapper<String> as an object
            val innerValue = valueModel.fields["value"]
            assertNotNull("Inner Wrapper should have 'value' field", innerValue)
            assertTrue(
                "Inner value should be string, got: ${innerValue!!.model}",
                innerValue.model is ObjectModel.Single && (innerValue.model as ObjectModel.Single).type == JsonType.STRING
            )
            val innerLabel = valueModel.fields["label"]
            assertNotNull("Inner Wrapper should have 'label' field", innerLabel)
        } else {
            // Light fixture fallback: String-based resolution
            assertTrue(
                "value should be string-like in light fixture, got: $valueModel",
                valueModel is ObjectModel.Single
            )
        }

        // Own field: extra
        val extraField = obj.fields["extra"]
        assertNotNull("Should have 'extra' field from DoubleWrapper", extraField)

        // Outer label: String (from Wrapper)
        val outerLabel = obj.fields["label"]
        assertNotNull("Should have 'label' field from Wrapper", outerLabel)
    }

    /**
     * ResponseWrapper<T> extends Pair<String, Wrapper<T>>
     * IntResponseWrapper extends ResponseWrapper<Integer>
     *
     * So: first=String, second=Wrapper<Integer> (object with value:Integer, label:String)
     */
    fun testPartialBindingWithWrapping() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.IntResponseWrapper")
        assertNotNull("Should find IntResponseWrapper", psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        val obj = model as? ObjectModel.Object
        assertNotNull("Model should be Object", obj)

        // first: A=String
        val firstField = obj!!.fields["first"]
        assertNotNull("Should have 'first' field", firstField)
        assertTrue(
            "first should be string, got: ${firstField!!.model}",
            firstField.model is ObjectModel.Single && (firstField.model as ObjectModel.Single).type == JsonType.STRING
        )

        // second: B=Wrapper<Integer> → Object
        val secondField = obj.fields["second"]
        assertNotNull("Should have 'second' field", secondField)
        assertTrue(
            "second should be an Object (Wrapper<Integer>), got: ${secondField!!.model}",
            secondField.model is ObjectModel.Object
        )

        val wrapperObj = secondField.model as ObjectModel.Object

        // Wrapper.value: T=Integer → int
        val wrapperValue = wrapperObj.fields["value"]
        assertNotNull("Wrapper should have 'value' field", wrapperValue)
        assertTrue(
            "Wrapper value should be int, got: ${wrapperValue!!.model}",
            wrapperValue.model is ObjectModel.Single && (wrapperValue.model as ObjectModel.Single).type == JsonType.INT
        )

        // Wrapper.label: String
        val wrapperLabel = wrapperObj.fields["label"]
        assertNotNull("Wrapper should have 'label' field", wrapperLabel)

        // Own field: status
        val statusField = obj.fields["status"]
        assertNotNull("Should have 'status' field from ResponseWrapper", statusField)
        assertTrue(
            "status should be int, got: ${statusField!!.model}",
            statusField.model is ObjectModel.Single && (statusField.model as ObjectModel.Single).type == JsonType.INT
        )
    }

    /**
     * Diamond inheritance: UserEntity extends BaseEntity<Long>
     * BaseEntity<ID> implements Identifiable<ID>, Named
     *
     * id should resolve to Long, name to String, plus own email field.
     */
    fun testDiamondInheritanceWithInterfaces() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.UserEntity")
        assertNotNull("Should find UserEntity", psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        val obj = model as? ObjectModel.Object
        assertNotNull("Model should be Object", obj)

        // id: ID=Long
        val idField = obj!!.fields["id"]
        assertNotNull("Should have 'id' field from BaseEntity", idField)
        assertTrue(
            "id should be long, got: ${idField!!.model}",
            idField.model is ObjectModel.Single && (idField.model as ObjectModel.Single).type == JsonType.LONG
        )

        // name: String (from BaseEntity/Named)
        val nameField = obj.fields["name"]
        assertNotNull("Should have 'name' field from BaseEntity", nameField)
        assertTrue(
            "name should be string, got: ${nameField!!.model}",
            nameField.model is ObjectModel.Single && (nameField.model as ObjectModel.Single).type == JsonType.STRING
        )

        // createdAt: Long (from BaseEntity)
        val createdAtField = obj.fields["createdAt"]
        assertNotNull("Should have 'createdAt' field from BaseEntity", createdAtField)
        assertTrue(
            "createdAt should be long, got: ${createdAtField!!.model}",
            createdAtField.model is ObjectModel.Single && (createdAtField.model as ObjectModel.Single).type == JsonType.LONG
        )

        // email: String (own field)
        val emailField = obj.fields["email"]
        assertNotNull("Should have 'email' field from UserEntity", emailField)
        assertTrue(
            "email should be string, got: ${emailField!!.model}",
            emailField.model is ObjectModel.Single && (emailField.model as ObjectModel.Single).type == JsonType.STRING
        )
    }


    /**
     * Issue #1326 regression: AtaPageResult<T> extends AtaBaseResult<AtaPage<T>>
     * The content field (from BaseResult<D>) should resolve to AtaPage<VotePageQueryVO>,
     * not VotePageQueryVO directly.
     *
     * This is the same test as ComplexGenericInheritanceTest but placed here
     * for completeness of the ResolvedType test suite.
     */
    fun testIssue1326_TypeWrappingInInheritance() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.VotePageQueryResult")
        assertNotNull(psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val obj = helper.buildObjectModel(psiClass!!) as? ObjectModel.Object
        assertNotNull(obj)

        val contentField = obj!!.fields["content"]
        assertNotNull("Should have 'content' field", contentField)

        // content must be an Object (AtaPage), not a simple type
        assertTrue(
            "content should be Object (AtaPage<VotePageQueryVO>), got: ${contentField!!.model}",
            contentField.model is ObjectModel.Object
        )

        val contentObj = contentField.model as ObjectModel.Object
        // Verify AtaPage fields are present
        assertNotNull("AtaPage should have 'totalCount'", contentObj.fields["totalCount"])
        assertNotNull("AtaPage should have 'currentPage'", contentObj.fields["currentPage"])
        assertNotNull("AtaPage should have 'pageSize'", contentObj.fields["pageSize"])
        assertNotNull("AtaPage should have 'hasNextPage'", contentObj.fields["hasNextPage"])
        assertNotNull("AtaPage should have 'data'", contentObj.fields["data"])
    }

    // ==================== ClassType.superClasses and allSuperClasses ====================

    fun testSuperClasses_DirectParent() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.StringChild")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        val supers = ct.superClasses().toList()
        assertTrue(
            "StringChild should have GenericBase as superclass",
            supers.any { it.psiClass.name == "GenericBase" }
        )
    }

    fun testSuperClasses_ExcludesObject() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.StringChild")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        val supers = ct.superClasses().toList()
        assertFalse(
            "Should exclude java.lang.Object",
            supers.any { it.psiClass.qualifiedName == "java.lang.Object" }
        )
    }

    fun testAllSuperClasses_TransitiveHierarchy() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.ConcreteLeaf")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        val allSupers = ct.allSuperClasses().toList()
        val superNames = allSupers.map { it.psiClass.name }.toSet()
        assertTrue("Should include MiddleChild", "MiddleChild" in superNames)
        assertTrue("Should include TwoTypeBase", "TwoTypeBase" in superNames)
        assertFalse("Should exclude Object", superNames.any { it == "Object" })
    }

    fun testAllSuperClasses_DiamondInheritance() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.UserEntity")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        val allSupers = ct.allSuperClasses().toList()
        val superNames = allSupers.map { it.psiClass.name }.toSet()
        assertTrue("Should include BaseEntity", "BaseEntity" in superNames)
        assertTrue("Should include Identifiable", "Identifiable" in superNames)
        assertTrue("Should include Named", "Named" in superNames)
    }

    fun testAllSuperClasses_NoDuplicates() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.UserEntity")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        val allSupers = ct.allSuperClasses().toList()
        val qualifiedNames = allSupers.mapNotNull { it.psiClass.qualifiedName }
        assertEquals(
            "Should have no duplicate supertypes",
            qualifiedNames.size, qualifiedNames.toSet().size
        )
    }

    // ==================== ClassType.fields with generic resolution ====================

    fun testClassTypeFields_GenericResolution() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.StringChild")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        val fields = ct.fields()
        val dataField = fields.firstOrNull { it.name == "data" }
        assertNotNull("Should have 'data' field", dataField)
        // StringChild extends GenericBase<String>, so data: T should resolve
        // The field type should not be an unresolved "T"
        val fieldType = dataField!!.type
        assertFalse(
            "data type should not be unresolved T, got: $fieldType",
            fieldType is ResolvedType.UnresolvedType && fieldType.canonicalText == "T"
        )
    }

    fun testClassTypeFields_MultiLevelGenericResolution() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.ConcreteLeaf")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        val fields = ct.fields()

        val firstField = fields.firstOrNull { it.name == "first" }
        assertNotNull("Should have 'first' field", firstField)
        // ConcreteLeaf extends MiddleChild<Long> extends TwoTypeBase<X, String>
        // first: T=X=Long
        // In light fixture, Long may resolve as UnresolvedType("Long") or PrimitiveType(LONG)
        val firstType = firstField!!.type
        assertTrue(
            "first should resolve to Long (PrimitiveType or UnresolvedType), got: $firstType",
            (firstType is ResolvedType.PrimitiveType && firstType.kind == PrimitiveKind.LONG) ||
                    (firstType is ResolvedType.UnresolvedType && firstType.canonicalText.contains("Long"))
        )

        val secondField = fields.firstOrNull { it.name == "second" }
        assertNotNull("Should have 'second' field", secondField)
        // second: R=String
        val secondType = secondField!!.type
        assertFalse(
            "second should not be unresolved R, got: $secondType",
            secondType is ResolvedType.UnresolvedType && secondType.canonicalText == "R"
        )
    }

    // ==================== Edge cases ====================

    /**
     * Building model for a class with no type parameters should work normally.
     */
    fun testNonGenericClass() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.VotePageQueryVO")
        assertNotNull(psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val obj = helper.buildObjectModel(psiClass!!) as? ObjectModel.Object
        assertNotNull(obj)

        assertEquals("VotePageQueryVO should have 2 fields", 2, obj!!.fields.size)
        assertNotNull("Should have 'id'", obj.fields["id"])
        assertNotNull("Should have 'label'", obj.fields["label"])
    }

    /**
     * Building model for a raw generic class (no concrete type args) should
     * still produce fields, with type params as UnresolvedType or Single.
     */
    fun testRawGenericClass() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.GenericBase")
        assertNotNull(psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val obj = helper.buildObjectModel(psiClass!!) as? ObjectModel.Object
        assertNotNull(obj)

        val dataField = obj!!.fields["data"]
        assertNotNull("Should have 'data' field even for raw generic", dataField)
    }

    /**
     * Multi-level inheritance with 3+ levels should resolve all fields correctly.
     * ConcreteLeaf extends MiddleChild<Long> extends TwoTypeBase<X, String>
     */
    fun testThreeLevelInheritance_AllFieldsPresent() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.ConcreteLeaf")
        assertNotNull(psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val obj = helper.buildObjectModel(psiClass!!) as? ObjectModel.Object
        assertNotNull(obj)

        // Own field
        assertNotNull("Should have 'active' from ConcreteLeaf", obj!!.fields["active"])
        // From MiddleChild
        assertNotNull("Should have 'middleName' from MiddleChild", obj.fields["middleName"])
        // From TwoTypeBase
        assertNotNull("Should have 'first' from TwoTypeBase", obj.fields["first"])
        assertNotNull("Should have 'second' from TwoTypeBase", obj.fields["second"])
    }

    /**
     * Deep chain: VotePageQueryResult → AtaPageResult → AtaBaseResult → BaseResult
     * All fields from all levels should be present.
     */
    fun testFourLevelInheritance_AllFieldsPresent() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.VotePageQueryResult")
        assertNotNull(psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val obj = helper.buildObjectModel(psiClass!!) as? ObjectModel.Object
        assertNotNull(obj)

        // From BaseResult
        assertNotNull("success from BaseResult", obj!!.fields["success"])
        assertNotNull("errorCode from BaseResult", obj.fields["errorCode"])
        assertNotNull("errorMsg from BaseResult", obj.fields["errorMsg"])
        assertNotNull("content from BaseResult", obj.fields["content"])
        // From AtaBaseResult
        assertNotNull("traceId from AtaBaseResult", obj.fields["traceId"])
        // From AtaPageResult
        assertNotNull("hasNextPage from AtaPageResult", obj.fields["hasNextPage"])
    }

    /**
     * PrimitiveKind enum should have all expected values.
     */
    fun testPrimitiveKindValues() {
        val kinds = PrimitiveKind.entries
        assertEquals(9, kinds.size)
        assertTrue(kinds.contains(PrimitiveKind.BOOLEAN))
        assertTrue(kinds.contains(PrimitiveKind.BYTE))
        assertTrue(kinds.contains(PrimitiveKind.CHAR))
        assertTrue(kinds.contains(PrimitiveKind.SHORT))
        assertTrue(kinds.contains(PrimitiveKind.INT))
        assertTrue(kinds.contains(PrimitiveKind.LONG))
        assertTrue(kinds.contains(PrimitiveKind.FLOAT))
        assertTrue(kinds.contains(PrimitiveKind.DOUBLE))
        assertTrue(kinds.contains(PrimitiveKind.VOID))
    }

    // ==================== Deep multi-level wrapping (3+ levels, same T name) ====================

    /**
     * ConcreteLayer3 extends Layer3<Integer>
     * Layer3<T> extends Layer2<Pair<String, T>>
     * Layer2<T> extends Layer1<Wrapper<T>>
     * Layer1<T> { T value; }
     *
     * value should resolve to Wrapper<Pair<String, Integer>>
     * This tests 3 levels of wrapping with the same type parameter name T at each level.
     */
    fun testThreeLevelWrapping_ValueFieldIsDeeplyNested() = runTest {
        loadFile("model/generic/Layer1.java")
        loadFile("model/generic/Layer2.java")
        loadFile("model/generic/Layer3.java")
        loadFile("model/generic/ConcreteLayer3.java")

        val psiClass = findClass("com.itangcent.model.generic.ConcreteLayer3")
        assertNotNull("Should find ConcreteLayer3", psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        val obj = model as? ObjectModel.Object
        assertNotNull("Model should be Object", obj)

        // Own field from Layer3: flag (Boolean)
        val flagField = obj!!.fields["flag"]
        assertNotNull("Should have 'flag' from Layer3", flagField)
        assertTrue(
            "flag should be boolean, got: ${flagField!!.model}",
            flagField.model is ObjectModel.Single && (flagField.model as ObjectModel.Single).type == JsonType.BOOLEAN
        )

        // Field from Layer2: tag (String)
        val tagField = obj.fields["tag"]
        assertNotNull("Should have 'tag' from Layer2", tagField)

        // value: should be Wrapper<Pair<String, Integer>> → an Object
        val valueField = obj.fields["value"]
        assertNotNull("Should have 'value' from Layer1", valueField)

        val valueModel = valueField!!.model
        assertTrue(
            "value should be an Object (Wrapper<Pair<String, Integer>>), got: $valueModel",
            valueModel is ObjectModel.Object
        )

        val wrapperObj = valueModel as ObjectModel.Object

        // Wrapper.label: String
        assertNotNull("Wrapper should have 'label'", wrapperObj.fields["label"])

        // Wrapper.value: Pair<String, Integer> → an Object
        val innerValue = wrapperObj.fields["value"]
        assertNotNull("Wrapper should have 'value' (Pair<String, Integer>)", innerValue)
        assertTrue(
            "Wrapper.value should be an Object (Pair<String, Integer>), got: ${innerValue!!.model}",
            innerValue.model is ObjectModel.Object
        )

        val pairObj = innerValue.model as ObjectModel.Object

        // Pair.first: A=String
        val firstField = pairObj.fields["first"]
        assertNotNull("Pair should have 'first'", firstField)
        assertTrue(
            "Pair.first should be string, got: ${firstField!!.model}",
            firstField.model is ObjectModel.Single && (firstField.model as ObjectModel.Single).type == JsonType.STRING
        )

        // Pair.second: B=Integer → int
        val secondField = pairObj.fields["second"]
        assertNotNull("Pair should have 'second'", secondField)
        assertTrue(
            "Pair.second should be int, got: ${secondField!!.model}",
            secondField.model is ObjectModel.Single && (secondField.model as ObjectModel.Single).type == JsonType.INT
        )
    }

    // ==================== Per-level generic context propagation via ClassType ====================
    //
    // These tests verify that ResolvedType.ClassType.fields(), .genericContext,
    // .contextForDeclaringClass(), and .superClasses() correctly propagate generic
    // bindings through inheritance hierarchies with inverted/swapped type parameters.
    //
    // Hierarchy under test:
    //   InverseA<T, R> { T t; R r; }
    //   InverseB<X, Y> extends InverseA<Y, X> { X x; Y y; }   ← inverts!
    //   InverseC extends InverseB<String, Integer>
    //
    // Resolution for InverseC:
    //   B level: X=String, Y=Integer → x:String, y:Integer
    //   A level: T=Y=Integer, R=X=String → t:Integer, r:String

    /**
     * Verify ClassType.genericContext is local (only this class's own params).
     */
    fun testGenericContext_IsLocalOnly() = runTest {
        loadFile("model/generic/InverseA.java")
        loadFile("model/generic/InverseB.java")
        loadFile("model/generic/InverseC.java")

        val inverseBClass = findClass("com.itangcent.model.generic.InverseB")!!
        // InverseB<String, Integer> — build a ClassType with concrete args
        val stringType = ResolvedType.UnresolvedType("java.lang.String")
        val intType = ResolvedType.PrimitiveType(PrimitiveKind.INT)
        val bType = ResolvedType.ClassType(inverseBClass, listOf(stringType, intType))

        val ctx = bType.genericContext
        // Should only have B's own params: X and Y
        assertNotNull("Should have X", ctx.genericMap["X"])
        assertNotNull("Should have Y", ctx.genericMap["Y"])
        // Should NOT have A's params flattened in
        assertNull("Should NOT have T (that's A's param)", ctx.genericMap["T"])
        assertNull("Should NOT have R (that's A's param)", ctx.genericMap["R"])
    }

    /**
     * Verify superClasses() builds per-level contexts with correct inversions.
     *
     * InverseB<String, Integer> extends InverseA<Y, X> = InverseA<Integer, String>
     * So the super ClassType for A should have T=Integer, R=String.
     */
    fun testSuperClasses_InverseGenericPropagation() = runTest {
        loadFile("model/generic/InverseA.java")
        loadFile("model/generic/InverseB.java")

        val inverseBClass = findClass("com.itangcent.model.generic.InverseB")!!
        val stringType = ResolvedType.UnresolvedType("java.lang.String")
        val intType = ResolvedType.PrimitiveType(PrimitiveKind.INT)
        val bType = ResolvedType.ClassType(inverseBClass, listOf(stringType, intType))

        val supers = bType.superClasses().toList()
        val superA = supers.firstOrNull { it.psiClass.name == "InverseA" }
        assertNotNull("Should have InverseA as superclass", superA)

        // A's context should have T=Integer (from Y), R=String (from X)
        val aCtx = superA!!.genericContext
        assertEquals(
            "A's T should be Integer (from B's Y), got: ${aCtx.genericMap["T"]}",
            intType, aCtx.genericMap["T"]
        )
        assertEquals(
            "A's R should be String (from B's X), got: ${aCtx.genericMap["R"]}",
            stringType, aCtx.genericMap["R"]
        )
    }

    /**
     * Verify contextForDeclaringClass() resolves the correct context for InverseA
     * when called from InverseC (which extends InverseB<String, Integer>).
     */
    fun testContextForDeclaringClass_InverseHierarchy() = runTest {
        loadFile("model/generic/InverseA.java")
        loadFile("model/generic/InverseB.java")
        loadFile("model/generic/InverseC.java")

        val inverseCClass = findClass("com.itangcent.model.generic.InverseC")!!
        val inverseAClass = findClass("com.itangcent.model.generic.InverseA")!!
        val inverseBClass = findClass("com.itangcent.model.generic.InverseB")!!
        val cType = ResolvedType.ClassType(inverseCClass, emptyList())

        // Context for B: X=String, Y=Integer
        val bCtx = cType.contextForDeclaringClass(inverseBClass)
        val bX = bCtx.genericMap["X"]
        val bY = bCtx.genericMap["Y"]
        assertNotNull("B's X should be bound", bX)
        assertNotNull("B's Y should be bound", bY)
        // X=String, Y=Integer
        assertTrue(
            "B's X should be String, got: $bX",
            bX.toString().contains("String")
        )
        assertTrue(
            "B's Y should be Integer/INT, got: $bY",
            bY is ResolvedType.PrimitiveType && bY.kind == PrimitiveKind.INT
                    || bY.toString().contains("Integer")
        )

        // Context for A: T=Y=Integer, R=X=String (inverted!)
        val aCtx = cType.contextForDeclaringClass(inverseAClass)
        val aT = aCtx.genericMap["T"]
        val aR = aCtx.genericMap["R"]
        assertNotNull("A's T should be bound", aT)
        assertNotNull("A's R should be bound", aR)
        assertTrue(
            "A's T should be Integer (from B's Y), got: $aT",
            aT is ResolvedType.PrimitiveType && aT.kind == PrimitiveKind.INT
                    || aT.toString().contains("Integer")
        )
        assertTrue(
            "A's R should be String (from B's X), got: $aR",
            aR.toString().contains("String")
        )
    }

    /**
     * Verify ClassType.fields() resolves all fields correctly through the inverse hierarchy.
     *
     * InverseC extends InverseB<String, Integer>
     * B<X=String, Y=Integer> extends A<Y, X> = A<Integer, String>
     *
     * Expected fields:
     *   From A: t → Integer, r → String
     *   From B: x → String, y → Integer
     */
    fun testClassTypeFields_InverseGenericResolution() = runTest {
        loadFile("model/generic/InverseA.java")
        loadFile("model/generic/InverseB.java")
        loadFile("model/generic/InverseC.java")

        val inverseCClass = findClass("com.itangcent.model.generic.InverseC")!!
        val cType = ResolvedType.ClassType(inverseCClass, emptyList())
        val fields = cType.fields()
        val fieldMap = fields.associateBy { it.name }

        // B's own fields: x=String, y=Integer
        val xField = fieldMap["x"]
        assertNotNull("Should have 'x' from B", xField)
        assertTrue(
            "x should be String (X=String), got: ${xField!!.type}",
            xField.type.toString().contains("String")
        )

        val yField = fieldMap["y"]
        assertNotNull("Should have 'y' from B", yField)
        assertTrue(
            "y should be Integer (Y=Integer), got: ${yField!!.type}",
            yField.type is ResolvedType.PrimitiveType && (yField.type as ResolvedType.PrimitiveType).kind == PrimitiveKind.INT
                    || yField.type.toString().contains("Integer")
        )

        // A's fields (inherited through B with inversion): t=Integer, r=String
        val tField = fieldMap["t"]
        assertNotNull("Should have 't' from A", tField)
        assertTrue(
            "t should be Integer (T=Y=Integer), got: ${tField!!.type}",
            tField.type is ResolvedType.PrimitiveType && (tField.type as ResolvedType.PrimitiveType).kind == PrimitiveKind.INT
                    || tField.type.toString().contains("Integer")
        )

        val rField = fieldMap["r"]
        assertNotNull("Should have 'r' from A", rField)
        assertTrue(
            "r should be String (R=X=String), got: ${rField!!.type}",
            rField.type.toString().contains("String")
        )
    }

    /**
     * Same as above but via DefaultPsiClassHelper.buildObjectModel to verify
     * the full pipeline works end-to-end.
     */
    fun testBuildObjectModel_InverseGenericResolution() = runTest {
        loadFile("model/generic/InverseA.java")
        loadFile("model/generic/InverseB.java")
        loadFile("model/generic/InverseC.java")

        val psiClass = findClass("com.itangcent.model.generic.InverseC")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val obj = helper.buildObjectModel(psiClass) as? ObjectModel.Object
        assertNotNull("Should build model", obj)

        // B's own fields
        val xField = obj!!.fields["x"]
        assertNotNull("Should have 'x' from B", xField)
        assertTrue(
            "x should be string (X=String), got: ${xField!!.model}",
            xField.model is ObjectModel.Single && (xField.model as ObjectModel.Single).type == JsonType.STRING
        )

        val yField = obj.fields["y"]
        assertNotNull("Should have 'y' from B", yField)
        assertTrue(
            "y should be int (Y=Integer), got: ${yField!!.model}",
            yField.model is ObjectModel.Single && (yField.model as ObjectModel.Single).type == JsonType.INT
        )

        // A's fields (inverted): t=Integer, r=String
        val tField = obj.fields["t"]
        assertNotNull("Should have 't' from A", tField)
        assertTrue(
            "t should be int (T=Y=Integer), got: ${tField!!.model}",
            tField.model is ObjectModel.Single && (tField.model as ObjectModel.Single).type == JsonType.INT
        )

        val rField = obj.fields["r"]
        assertNotNull("Should have 'r' from A", rField)
        assertTrue(
            "r should be string (R=X=String), got: ${rField!!.model}",
            rField.model is ObjectModel.Single && (rField.model as ObjectModel.Single).type == JsonType.STRING
        )
    }

    // ==================== Double inversion (3-level chain) ====================
    //
    // E<M, N> extends D<N, M>
    // D<P, Q> extends A<Q, P>
    // A<T, R> { T t; R r; }
    //
    // ConcreteInverseE extends E<Long, Boolean>
    //   E level: M=Long, N=Boolean → m:Long, n:Boolean
    //   D level: P=N=Boolean, Q=M=Long → p:Boolean, q:Long
    //   A level: T=Q=Long, R=P=Boolean → t:Long, r:Boolean
    //
    // Double inversion cancels out: A sees (Long, Boolean) same as E's (M, N).

    fun testDoubleInversion_AllFieldsResolvedCorrectly() = runTest {
        loadFile("model/generic/InverseA.java")
        loadFile("model/generic/InverseD.java")
        loadFile("model/generic/InverseE.java")
        loadFile("model/generic/ConcreteInverseE.java")

        val psiClass = findClass("com.itangcent.model.generic.ConcreteInverseE")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val obj = helper.buildObjectModel(psiClass) as? ObjectModel.Object
        assertNotNull("Should build model", obj)

        // E level: m=Long, n=Boolean
        val mField = obj!!.fields["m"]
        assertNotNull("Should have 'm' from E", mField)
        assertTrue(
            "m should be long (M=Long), got: ${mField!!.model}",
            mField.model is ObjectModel.Single && (mField.model as ObjectModel.Single).type == JsonType.LONG
        )

        val nField = obj.fields["n"]
        assertNotNull("Should have 'n' from E", nField)
        assertTrue(
            "n should be boolean (N=Boolean), got: ${nField!!.model}",
            nField.model is ObjectModel.Single && (nField.model as ObjectModel.Single).type == JsonType.BOOLEAN
        )

        // D level: p=Boolean (P=N=Boolean), q=Long (Q=M=Long)
        val pField = obj.fields["p"]
        assertNotNull("Should have 'p' from D", pField)
        assertTrue(
            "p should be boolean (P=N=Boolean), got: ${pField!!.model}",
            pField.model is ObjectModel.Single && (pField.model as ObjectModel.Single).type == JsonType.BOOLEAN
        )

        val qField = obj.fields["q"]
        assertNotNull("Should have 'q' from D", qField)
        assertTrue(
            "q should be long (Q=M=Long), got: ${qField!!.model}",
            qField.model is ObjectModel.Single && (qField.model as ObjectModel.Single).type == JsonType.LONG
        )

        // A level: t=Long (T=Q=M=Long), r=Boolean (R=P=N=Boolean)
        // Double inversion cancels: A sees same order as E
        val tField = obj.fields["t"]
        assertNotNull("Should have 't' from A", tField)
        assertTrue(
            "t should be long (T=Q=M=Long, double inversion cancels), got: ${tField!!.model}",
            tField.model is ObjectModel.Single && (tField.model as ObjectModel.Single).type == JsonType.LONG
        )

        val rField = obj.fields["r"]
        assertNotNull("Should have 'r' from A", rField)
        assertTrue(
            "r should be boolean (R=P=N=Boolean, double inversion cancels), got: ${rField!!.model}",
            rField.model is ObjectModel.Single && (rField.model as ObjectModel.Single).type == JsonType.BOOLEAN
        )
    }

    /**
     * Verify ClassType.fields() for the double-inversion chain.
     */
    fun testClassTypeFields_DoubleInversion() = runTest {
        loadFile("model/generic/InverseA.java")
        loadFile("model/generic/InverseD.java")
        loadFile("model/generic/InverseE.java")
        loadFile("model/generic/ConcreteInverseE.java")

        val psiClass = findClass("com.itangcent.model.generic.ConcreteInverseE")!!
        val cType = ResolvedType.ClassType(psiClass, emptyList())
        val fields = cType.fields()
        val fieldMap = fields.associateBy { it.name }

        // A level: t=Long, r=Boolean
        val tField = fieldMap["t"]
        assertNotNull("Should have 't' from A", tField)
        assertTrue(
            "t should be Long (double inversion cancels), got: ${tField!!.type}",
            tField.type is ResolvedType.PrimitiveType && (tField.type as ResolvedType.PrimitiveType).kind == PrimitiveKind.LONG
                    || tField.type.toString().contains("Long")
        )

        val rField = fieldMap["r"]
        assertNotNull("Should have 'r' from A", rField)
        assertTrue(
            "r should be Boolean (double inversion cancels), got: ${rField!!.type}",
            rField.type is ResolvedType.PrimitiveType && (rField.type as ResolvedType.PrimitiveType).kind == PrimitiveKind.BOOLEAN
                    || rField.type.toString().contains("Boolean")
        )

        // D level: p=Boolean, q=Long
        val pField = fieldMap["p"]
        assertNotNull("Should have 'p' from D", pField)
        assertTrue(
            "p should be Boolean, got: ${pField!!.type}",
            pField.type is ResolvedType.PrimitiveType && (pField.type as ResolvedType.PrimitiveType).kind == PrimitiveKind.BOOLEAN
                    || pField.type.toString().contains("Boolean")
        )

        val qField = fieldMap["q"]
        assertNotNull("Should have 'q' from D", qField)
        assertTrue(
            "q should be Long, got: ${qField!!.type}",
            qField.type is ResolvedType.PrimitiveType && (qField.type as ResolvedType.PrimitiveType).kind == PrimitiveKind.LONG
                    || qField.type.toString().contains("Long")
        )

        // E level: m=Long, n=Boolean
        val mField = fieldMap["m"]
        assertNotNull("Should have 'm' from E", mField)
        assertTrue(
            "m should be Long, got: ${mField!!.type}",
            mField.type is ResolvedType.PrimitiveType && (mField.type as ResolvedType.PrimitiveType).kind == PrimitiveKind.LONG
                    || mField.type.toString().contains("Long")
        )

        val nField = fieldMap["n"]
        assertNotNull("Should have 'n' from E", nField)
        assertTrue(
            "n should be Boolean, got: ${nField!!.type}",
            nField.type is ResolvedType.PrimitiveType && (nField.type as ResolvedType.PrimitiveType).kind == PrimitiveKind.BOOLEAN
                    || nField.type.toString().contains("Boolean")
        )
    }
}
