/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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
 * The annotation that may be used to inject a custom "parameter aggregator" value object
 * into a resource class field, property or resource method parameter.
 * <p>
 * The runtime will instantiate the object and inject all it's fields and properties annotated
 * with either one of the {@code @XxxParam} annotation ({@link PathParam &#64;PathParam},
 * {@link FormParam &#64;FormParam} ...) or the {@link javax.ws.rs.core.Context &#64;Context}
 * annotation. For the POJO classes same instantiation and injection rules apply as in case of instantiation
 * and injection of request-scoped root resource classes.
 * </p>
 * <p>
 * For example:
 * <pre>
 * public class MyBean {
 *   &#64;FormParam("myData")
 *   private String data;
 *
 *   &#64;HeaderParam("myHeader")
 *   private String header;
 *
 *   &#64;PathParam("id")
 *   public void setResourceId(String id) {...}
 *
 *   ...
 * }
 *
 * &#64;Path("myresources")
 * public class MyResources {
 *   &#64;POST
 *   &#64;Path("{id}")
 *   public void post(&#64;BeanParam MyBean myBean) {...}
 *
 *   ...
 * }
 * </pre>
 * </p>
 * <p>
 * Because injection occurs at object creation time, use of this annotation on resource
 * class fields and bean properties is only supported for the default per-request resource
 * class lifecycle. Resource classes using other lifecycles should only use this annotation
 * on resource method parameters.
 * </p>
 *
 * @author Marek Potociar
 * @since 2.0
 */
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BeanParam {
}
