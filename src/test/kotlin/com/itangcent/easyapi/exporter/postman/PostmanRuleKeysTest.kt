package com.itangcent.easyapi.exporter.postman

import org.junit.Assert.*
import org.junit.Test

class PostmanRuleKeysTest {

    @Test
    fun testPostPreRequest() {
        assertNotNull(PostmanRuleKeys.POST_PRE_REQUEST)
    }

    @Test
    fun testClassPostPreRequest() {
        assertNotNull(PostmanRuleKeys.CLASS_POST_PRE_REQUEST)
    }

    @Test
    fun testCollectionPostPreRequest() {
        assertNotNull(PostmanRuleKeys.COLLECTION_POST_PRE_REQUEST)
    }

    @Test
    fun testPostTest() {
        assertNotNull(PostmanRuleKeys.POST_TEST)
    }

    @Test
    fun testClassPostTest() {
        assertNotNull(PostmanRuleKeys.CLASS_POST_TEST)
    }

    @Test
    fun testCollectionPostTest() {
        assertNotNull(PostmanRuleKeys.COLLECTION_POST_TEST)
    }

    @Test
    fun testPostManHost() {
        assertNotNull(PostmanRuleKeys.POST_MAN_HOST)
    }

    @Test
    fun testAfterFormat() {
        assertNotNull(PostmanRuleKeys.AFTER_FORMAT)
    }
}
