/*
 * Entreri, an entity-component framework in Java
 *
 * Copyright (c) 2013, Michael Ludwig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright notice,
 *         this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above copyright notice,
 *         this list of conditions and the following disclaimer in the
 *         documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.lhkbob.entreri;

import java.lang.annotation.*;

/**
 * Validate is a generic validation annotation that lets you specify a Java-like snippet to be inserted into
 * the generated proxy to perform validation on a setter method.  Unlike {@link NotNull} and {@link Within},
 * this annotation cannot be placed on setter parameters. Because of the flexibility this offers, Validate
 * allows you to perform validation between different properties of the same component (such as ensuring a
 * minimum is less than a maximum value).
 * <p/>
 * The Java-like validation snippet must evaluate to a boolean expression. When that expression is true, the
 * inputs are considered valid; otherwise, the proxy will throw an IllegalArgumentException. The snippet must
 * use valid Java syntax, except that the symbols {@code $1 - $n} should be used to refer to the first through
 * nth setter parameters. Those symbols will be replaced with the generated parameter name at compile time.
 * Additionally, the syntax {@code $propertyName} will be replaced with {@code getPropertyName()} to refer to
 * properties on a component. Validation is performed before the property values are assigned, so referencing
 * a property name with this syntax in the setter method for that property will produce the old value.
 * <p/>
 * After this syntax replacement, any other errors may produce Java syntax errors when the generated source is
 * compiled.
 * <p/>
 * This annotation is ignored if placed on the getter property method.
 *
 * @author Michael Ludwig
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Validate {
    /**
     * @return Get the Java-like validation snippet that represents a boolean expression evaluating to true
     *         when input parameters are valid
     */
    String value();

    /**
     * @return Optional error message to include in the thrown exception
     */
    String errorMsg() default "";
}