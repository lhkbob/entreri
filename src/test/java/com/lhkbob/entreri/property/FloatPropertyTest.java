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
package com.lhkbob.entreri.property;

import org.junit.Test;

import java.lang.annotation.Annotation;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link com.lhkbob.entreri.property.FloatProperty}.
 *
 * @author Michael Ludwig
 */
public class FloatPropertyTest {
    private void doDefaultValueTest(FloatProperty property, float expectedValue) {
        property.setCapacity(1);
        property.setDefaultValue(0);
        assertEquals(expectedValue, property.get(0), 0.00001f);
    }

    private void doClonePolicy(FloatProperty property, boolean clone) {
        property.setCapacity(2);
        property.set(0, 5.0f);
        property.set(1, 0.0f);

        // now for the actual clone
        property.clone(property, 0, 1);
        if (clone) {
            assertEquals(5.0f, property.get(1), 0.00001f);
        } else {
            assertEquals(0.0f, property.get(1), 0.00001f);
        }
    }

    @Test
    public void testSimpleConstructorDefaultValue() {
        doDefaultValueTest(new FloatProperty(5.0f, true), 5.0f);
    }

    @Test
    public void testSimpleConstructorClone() {
        doClonePolicy(new FloatProperty(0.0f, true), true);
    }

    @Test
    public void testSimpleConstructorDoNotClone() {
        doClonePolicy(new FloatProperty(0.0f, false), false);
    }

    @Test
    public void testAnnotationConstructorDefaultValue() throws NoSuchMethodException {
        doDefaultValueTest(new FloatProperty(getDefaultValue(), null), 5.0f);
    }

    @Test
    public void testAnnotationConstructorNoDefaultValue() {
        // no DefaultFloat annotation maps to 0
        doDefaultValueTest(new FloatProperty(null, null), 0.0f);
    }

    @Test
    public void testAnnotationConstructorClone() {
        // no DoNotClone annotation maps to true
        doClonePolicy(new FloatProperty(null, null), true);
    }

    @Test
    public void testAnnotationConstructorDoNotClone() throws NoSuchMethodException {
        doClonePolicy(new FloatProperty(null, getClonePolicy()), false);
    }

    @Test
    public void testInitialCapacity() {
        assertEquals(1, new FloatProperty(0.0f, false).getCapacity());
    }

    @Test
    public void testSetCapacity() {
        FloatProperty p = new FloatProperty(0.0f, false);
        p.setCapacity(5);
        assertEquals(5, p.getCapacity());
        assertEquals(5, p.getIndexedData().length);
    }

    @Test
    public void testGetAndSet() {
        FloatProperty p = new FloatProperty(0.0f, false);
        assertEquals(0.0f, p.get(0), 0.00001f);
        p.set(0, 5.0f);
        assertEquals(5.0f, p.get(0), 0.00001f);
    }

    @Test
    public void testSwap() {
        FloatProperty p = new FloatProperty(0.0f, false);
        p.setCapacity(2);
        p.set(0, 5.0f);
        p.set(1, 0.0f);
        p.swap(0, 1);

        assertEquals(5.0f, p.get(1), 0.00001f);
        assertEquals(0.0f, p.get(0), 0.00001f);

        // make sure order doesn't matter
        p.swap(1, 0);
        assertEquals(5.0f, p.get(0), 0.00001f);
        assertEquals(0.0f, p.get(1), 0.00001f);
    }

    @Test
    public void testGetIndexedData() {
        FloatProperty p = new FloatProperty(0.0f, false);
        float[] data = p.getIndexedData();
        p.set(0, 5);
        assertEquals(5.0, data[0], 0.00001);
    }

    @DefaultFloat(5.0f)
    private static DefaultFloat getDefaultValue() throws NoSuchMethodException {
        return getAnnotation(DefaultFloat.class, "getDefaultValue");
    }

    @DoNotClone
    private static DoNotClone getClonePolicy() throws NoSuchMethodException {
        return getAnnotation(DoNotClone.class, "getClonePolicy");
    }

    private static <T extends Annotation> T getAnnotation(Class<T> type, String methodName)
            throws NoSuchMethodException {
        return FloatPropertyTest.class.getDeclaredMethod(methodName).getAnnotation(type);
    }
}
