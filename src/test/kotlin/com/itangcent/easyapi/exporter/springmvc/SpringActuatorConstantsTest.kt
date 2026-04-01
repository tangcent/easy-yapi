package com.itangcent.easyapi.exporter.springmvc

import org.junit.Assert.*
import org.junit.Test

class SpringActuatorConstantsTest {

    @Test
    fun testEndpointAnnotation() {
        assertEquals(
            "org.springframework.boot.actuate.endpoint.annotation.Endpoint",
            SpringActuatorConstants.ENDPOINT_ANNOTATION
        )
    }

    @Test
    fun testWebEndpointAnnotation() {
        assertEquals(
            "org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint",
            SpringActuatorConstants.WEB_ENDPOINT_ANNOTATION
        )
    }

    @Test
    fun testControllerEndpointAnnotation() {
        assertEquals(
            "org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpoint",
            SpringActuatorConstants.CONTROLLER_ENDPOINT_ANNOTATION
        )
    }

    @Test
    fun testRestControllerEndpointAnnotation() {
        assertEquals(
            "org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint",
            SpringActuatorConstants.REST_CONTROLLER_ENDPOINT_ANNOTATION
        )
    }

    @Test
    fun testReadOperationAnnotation() {
        assertEquals(
            "org.springframework.boot.actuate.endpoint.annotation.ReadOperation",
            SpringActuatorConstants.READ_OPERATION_ANNOTATION
        )
    }

    @Test
    fun testWriteOperationAnnotation() {
        assertEquals(
            "org.springframework.boot.actuate.endpoint.annotation.WriteOperation",
            SpringActuatorConstants.WRITE_OPERATION_ANNOTATION
        )
    }

    @Test
    fun testDeleteOperationAnnotation() {
        assertEquals(
            "org.springframework.boot.actuate.endpoint.annotation.DeleteOperation",
            SpringActuatorConstants.DELETE_OPERATION_ANNOTATION
        )
    }

    @Test
    fun testSelectorAnnotation() {
        assertEquals(
            "org.springframework.boot.actuate.endpoint.annotation.Selector",
            SpringActuatorConstants.SELECTOR_ANNOTATION
        )
    }

    @Test
    fun testEndpointAnnotationsSet() {
        assertEquals(4, SpringActuatorConstants.ENDPOINT_ANNOTATIONS.size)
        assertTrue(SpringActuatorConstants.ENDPOINT_ANNOTATIONS.contains(SpringActuatorConstants.ENDPOINT_ANNOTATION))
        assertTrue(SpringActuatorConstants.ENDPOINT_ANNOTATIONS.contains(SpringActuatorConstants.WEB_ENDPOINT_ANNOTATION))
    }

    @Test
    fun testEndpointOperationAnnotationsSet() {
        assertEquals(3, SpringActuatorConstants.ENDPOINT_OPERATION_ANNOTATIONS.size)
        assertTrue(SpringActuatorConstants.ENDPOINT_OPERATION_ANNOTATIONS.contains(SpringActuatorConstants.READ_OPERATION_ANNOTATION))
        assertTrue(SpringActuatorConstants.ENDPOINT_OPERATION_ANNOTATIONS.contains(SpringActuatorConstants.WRITE_OPERATION_ANNOTATION))
        assertTrue(SpringActuatorConstants.ENDPOINT_OPERATION_ANNOTATIONS.contains(SpringActuatorConstants.DELETE_OPERATION_ANNOTATION))
    }
}
