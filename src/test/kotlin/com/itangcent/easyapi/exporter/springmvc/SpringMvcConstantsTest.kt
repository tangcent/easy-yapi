package com.itangcent.easyapi.exporter.springmvc

import org.junit.Assert.*
import org.junit.Test

class SpringMvcConstantsTest {

    @Test
    fun testAnnotations() {
        assertEquals("org.springframework.web.bind.annotation.RequestBody", SpringMvcConstants.Annotations.REQUEST_BODY)
        assertEquals("org.springframework.web.bind.annotation.RequestParam", SpringMvcConstants.Annotations.REQUEST_PARAM)
        assertEquals("org.springframework.web.bind.annotation.RequestPart", SpringMvcConstants.Annotations.REQUEST_PART)
        assertEquals("org.springframework.web.bind.annotation.PathVariable", SpringMvcConstants.Annotations.PATH_VARIABLE)
        assertEquals("org.springframework.web.bind.annotation.RequestHeader", SpringMvcConstants.Annotations.REQUEST_HEADER)
        assertEquals("org.springframework.web.bind.annotation.CookieValue", SpringMvcConstants.Annotations.COOKIE_VALUE)
        assertEquals("org.springframework.web.bind.annotation.ModelAttribute", SpringMvcConstants.Annotations.MODEL_ATTRIBUTE)
        assertEquals("org.springframework.web.bind.annotation.SessionAttribute", SpringMvcConstants.Annotations.SESSION_ATTRIBUTE)
    }

    @Test
    fun testInfrastructureTypes() {
        assertEquals("org.springframework.validation.BindingResult", SpringMvcConstants.BINDING_RESULT)
        assertEquals("org.springframework.ui.Model", SpringMvcConstants.MODEL)
        assertEquals("org.springframework.ui.ModelMap", SpringMvcConstants.MODEL_MAP)
        assertEquals("org.springframework.web.servlet.ModelAndView", SpringMvcConstants.MODEL_AND_VIEW)
    }

    @Test
    fun testJavaxServletTypes() {
        assertEquals("javax.servlet.http.HttpServletRequest", SpringMvcConstants.Javax.HTTP_SERVLET_REQUEST)
        assertEquals("javax.servlet.http.HttpServletResponse", SpringMvcConstants.Javax.HTTP_SERVLET_RESPONSE)
        assertEquals("javax.servlet.http.HttpSession", SpringMvcConstants.Javax.HTTP_SERVLET_SESSION)
    }

    @Test
    fun testJakartaServletTypes() {
        assertEquals("jakarta.servlet.http.HttpServletRequest", SpringMvcConstants.Jakarta.HTTP_SERVLET_REQUEST)
        assertEquals("jakarta.servlet.http.HttpServletResponse", SpringMvcConstants.Jakarta.HTTP_SERVLET_RESPONSE)
        assertEquals("jakarta.servlet.http.HttpSession", SpringMvcConstants.Jakarta.HTTP_SERVLET_SESSION)
    }
}
