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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link com.lhkbob.entreri.property.ValueListProperty}.
 *
 * @author Michael Ludwig
 */
public class ValueListPropertyTest {
    private void doDefaultValueTest(ValueListProperty<Object> property, Class<? extends List> baseList)
            throws Exception {
        property.setCapacity(1);
        property.setDefaultValue(0);
        assertTrue(property.get(0).isEmpty());

        // pull out original data list from the property (wrapped in an unmodifiable list)
        Field origList = getBaseCollectionField(property.get(0).getClass());
        origList.setAccessible(true);
        assertTrue(baseList.isInstance(origList.get(property.get(0))));
    }

    private static Field getBaseCollectionField(Class<?> type) throws Exception {
        try {
            return type.getDeclaredField("c");
        } catch (NoSuchFieldException e) {
            Class<?> spr = type.getSuperclass();
            if (spr != null) {
                return getBaseCollectionField(spr);
            } else {
                throw e;
            }
        }
    }

    private void doClonePolicy(ValueListProperty<Object> property, boolean clone) {
        List<Object> v1 = new ArrayList<>();
        v1.add(new Object());

        property.setCapacity(2);
        property.set(0, v1);
        property.set(1, new ArrayList<>());

        // now for the actual clone
        property.clone(property, 0, 1);
        if (clone) {
            assertEquals(v1, property.get(1));
            assertNotSame(v1, property.get(1));
        } else {
            // sets the default value on clone (does not leave the value alone)
            assertTrue(property.get(1).isEmpty());
        }
    }

    @Test
    public void testSimpleConstructorDefaultValue() throws Exception {
        doDefaultValueTest(new ValueListProperty<>(ArrayList.class, true), ArrayList.class);
    }

    @Test
    public void testSimpleConstructorClone() {
        doClonePolicy(new ValueListProperty<>(ArrayList.class, true), true);
    }

    @Test
    public void testSimpleConstructorDoNotClone() {
        doClonePolicy(new ValueListProperty<>(ArrayList.class, false), false);
    }

    @Test
    public void testAnnotationConstructorDefaultValue() throws Exception {
        doDefaultValueTest(new ValueListProperty<>(getCollection(), null), LinkedList.class);
    }

    @Test
    public void testAnnotationConstructorClone() {
        // no DoNotClone annotation maps to true
        doClonePolicy(new ValueListProperty<>(null, null), true);
    }

    @Test
    public void testAnnotationConstructorDoNotClone() throws NoSuchMethodException {
        doClonePolicy(new ValueListProperty<>(null, getClonePolicy()), false);
    }

    @Test
    public void testInitialCapacity() {
        assertEquals(1, new ValueListProperty<>(ArrayList.class, false).getCapacity());
    }

    @Test
    public void testSetCapacity() {
        ValueListProperty<Object> p = new ValueListProperty<>(ArrayList.class, false);
        p.setCapacity(5);
        assertEquals(5, p.getCapacity());
    }

    @Test
    public void testGetAndSet() {
        List<Object> v1 = new ArrayList<>();
        ValueListProperty<Object> p = new ValueListProperty<>(ArrayList.class, false);
        assertNotNull(p.get(0));
        p.set(0, v1);
        assertNotSame(v1, p.get(0));
        assertEquals(v1, p.get(0));

        v1.add(new Object());
        assertFalse(v1.equals(p.get(0)));

        try {
            p.get(0).add(new Object());
            fail("Exception expected");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testAddContainsRemove() {
        Object e = new Object();
        ValueListProperty<Object> p = new ValueListProperty<>(ArrayList.class, false);
        List<Object> v = p.get(0);

        assertFalse(p.contains(0, e));
        assertTrue(p.add(0, e));
        assertTrue(v.contains(e));
        assertTrue(p.contains(0, e));
        assertEquals(1, v.size());

        assertTrue(p.remove(0, e));
        assertFalse(v.contains(e));
        assertFalse(p.contains(0, e));
        assertEquals(0, v.size());
    }

    @Test
    public void testSwap() {
        List<Object> v1 = Arrays.asList(new Object(), new Object());
        List<Object> v2 = Arrays.asList(new Object(), new Object());

        ValueListProperty<Object> p = new ValueListProperty<>(ArrayList.class, false);
        p.setCapacity(2);
        p.set(0, v1);
        p.set(1, v2);
        p.swap(0, 1);

        assertEquals(v1, p.get(1));
        assertEquals(v2, p.get(0));

        // make sure order doesn't matter
        p.swap(1, 0);
        assertEquals(v1, p.get(0));
        assertEquals(v2, p.get(1));
    }

    @Test
    public void testNullValuesDisallowed() {
        ValueListProperty<Object> p = new ValueListProperty<>(ArrayList.class, false);
        assertNotNull(p.get(0));
        try {
            p.set(0, null);
            fail("Exception expected");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @DoNotClone
    private static DoNotClone getClonePolicy() throws NoSuchMethodException {
        return getAnnotation(DoNotClone.class, "getClonePolicy");
    }

    @Collection(listImpl = LinkedList.class)
    private static Collection getCollection() throws NoSuchMethodException {
        return getAnnotation(Collection.class, "getCollection");
    }

    private static <T extends Annotation> T getAnnotation(Class<T> type, String methodName)
            throws NoSuchMethodException {
        return ValueListPropertyTest.class.getDeclaredMethod(methodName).getAnnotation(type);
    }
}
