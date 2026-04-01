/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package javax.ws.rs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Associates the name of a HTTP method with an annotation. A Java method annotated
 * with a runtime annotation that is itself annotated with this annotation will
 * be used to handle HTTP requests of the indicated HTTP method. It is an error
 * for a method to be annotated with more than one annotation that is annotated
 * with {@code HttpMethod}.
 *
 * @author Paul Sandoz
 * @author Marc Hadley
 * @see GET
 * @see POST
 * @see PUT
 * @see DELETE
 * @see PATCH
 * @see HEAD
 * @see OPTIONS
 * @since 1.0
 */
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpMethod {

    /**
     * HTTP GET method.
     */
    public static final String GET = "GET";
    /**
     * HTTP POST method.
     */
    public static final String POST = "POST";
    /**
     * HTTP PUT method.
     */
    public static final String PUT = "PUT";
    /**
     * HTTP DELETE method.
     */
    public static final String DELETE = "DELETE";
    /**
     * HTTP PATCH method.
     *
     * @since 2.1
     */
    public static final String PATCH = "PATCH";
    /**
     * HTTP HEAD method.
     */
    public static final String HEAD = "HEAD";
    /**
     * HTTP OPTIONS method.
     */
    public static final String OPTIONS = "OPTIONS";

    /**
     * Specifies the name of a HTTP method. E.g. "GET".
     */
    String value();
}
