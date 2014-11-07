/*
 * Entreri, an entity-component framework in Java
 *
 * Copyright (c) 2014, Michael Ludwig
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
package com.lhkbob.entreri.impl.methods;

import com.lhkbob.entreri.attr.Validate;
import com.lhkbob.entreri.attr.Within;
import com.lhkbob.entreri.impl.Generator;

import java.util.List;

/**
 * Validations
 * ===========
 *
 * A static utility class to append source code that implements the currently defined validation annotations
 * supported by BeanSetterPattern and MultiSetterPattern.
 *
 * @author Michael Ludwig
 */
public final class Validations {
    private Validations() {
    }

    public static void appendNotNull(String variable, Generator generator) {
        generator.appendSyntax("if (" + variable + " == null) {",
                               "\tthrow new NullPointerException(\"" + variable + " cannot be null\");", "}");
    }

    public static void appendWithin(String variable, Within within, Generator generator) {
        if (Double.isInfinite(within.max())) {
            // less than min check only
            generator.appendSyntax("if (" + variable + " < " + within.min() + ") {",
                                   "\tthrow new IllegalArgumentException(\"" + variable +
                                   " must be greater than or equal to " + within.min() + "\");", "}");
        } else if (Double.isInfinite(within.min())) {
            // greater than max check only
            generator.appendSyntax("if (" + variable + " > " + within.max() + ") {",
                                   "\tthrow new IllegalArgumentException(\"" + variable +
                                   " must be less than or equal to " + within.max() + "\");", "}");
        } else {
            // both
            generator.appendSyntax("if (" + variable + " < " + within.min() + " || " + variable + " > " +
                                   within.max() + ") {",
                                   "\tthrow new IllegalArgumentException(\"" + variable +
                                   " must be in [" + within.min() + ", " + within.max() + "]\");", "}");
        }
    }

    public static void appendValidation(List<String> paramNames, Validate validation, Generator generator) {
        // filter ${n} parameter names
        String snippet = validation.value();
        for (int i = 0; i < paramNames.size(); i++) {
            snippet = snippet.replace("${" + (i + 1) + "}", paramNames.get(i));
        }

        generator.appendSyntax("if (!(" + snippet + ")) {",
                               "\tthrow new IllegalArgumentException(\"" + validation.errorMsg() + "\");",
                               "}");
    }
}
