package com.itangcent.http

import org.apache.http.entity.ContentType

/**
 * define raw content types without charset
 *
 * @author tangcent
 * @date 2024/06/29
 */
object RawContentType {

    val APPLICATION_JSON = ContentType.create("application/json")!!
}