package com.itangcent.easyapi.exporter.springmvc

/**
 * Constants for Spring MVC framework classes and annotations.
 *
 * Contains fully qualified class names for:
 * - Spring MVC request mapping annotations
 * - Spring MVC infrastructure types
 * - Servlet API types (both javax and jakarta namespaces)
 */
object SpringMvcConstants {

    /**
     * Spring MVC request binding annotations.
     */
    object Annotations {
        const val REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody"
        const val REQUEST_PARAM = "org.springframework.web.bind.annotation.RequestParam"
        const val REQUEST_PART = "org.springframework.web.bind.annotation.RequestPart"
        const val PATH_VARIABLE = "org.springframework.web.bind.annotation.PathVariable"
        const val REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader"
        const val COOKIE_VALUE = "org.springframework.web.bind.annotation.CookieValue"
        const val MODEL_ATTRIBUTE = "org.springframework.web.bind.annotation.ModelAttribute"
        const val SESSION_ATTRIBUTE = "org.springframework.web.bind.annotation.SessionAttribute"
    }

    // ==================== Spring MVC Infrastructure Types ====================

    const val BINDING_RESULT = "org.springframework.validation.BindingResult"
    const val MODEL = "org.springframework.ui.Model"
    const val MODEL_MAP = "org.springframework.ui.ModelMap"
    const val MODEL_AND_VIEW = "org.springframework.web.servlet.ModelAndView"

    /**
     * Servlet API types for javax namespace (Servlet 4.x and earlier).
     */
    object Javax {
        const val HTTP_SERVLET_REQUEST = "javax.servlet.http.HttpServletRequest"
        const val HTTP_SERVLET_RESPONSE = "javax.servlet.http.HttpServletResponse"
        const val HTTP_SERVLET_SESSION = "javax.servlet.http.HttpSession"
    }

    /**
     * Servlet API types for jakarta namespace (Jakarta EE 9+).
     */
    object Jakarta {
        const val HTTP_SERVLET_REQUEST = "jakarta.servlet.http.HttpServletRequest"
        const val HTTP_SERVLET_RESPONSE = "jakarta.servlet.http.HttpServletResponse"
        const val HTTP_SERVLET_SESSION = "jakarta.servlet.http.HttpSession"
    }
}
