/*
 * Entreri, an entity-component framework in Java
 *
 * Copyright (c) 2012, Michael Ludwig
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

import com.lhkbob.entreri.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * ShortProperty is an implementation of Property that stores a single short value.
 *
 * @author Michael Ludwig
 */
@Factory(ShortProperty.Factory.class)
public final class ShortProperty implements Property {
    private ShortDataStore store;

    /**
     * Create an ShortProperty.
     */
    public ShortProperty() {
        store = new ShortDataStore(1, new short[1]);
    }

    /**
     * Return the backing int array of this property's IndexedDataStore. The array may be
     * longer than necessary for the number of components in the system. Data can be
     * accessed for a component directly using the component's index.
     *
     * @return The short data for all packed properties that this property has been packed
     *         with
     */
    public short[] getIndexedData() {
        return store.getArray();
    }

    /**
     * Get the value stored in this property for the given component index.
     *
     * @param componentIndex The component's index
     *
     * @return The object at the given offset for the given component
     *
     * @throws ArrayIndexOutOfBoundsException if the componentIndex is invalid
     */
    public short get(int componentIndex) {
        return store.getArray()[componentIndex];
    }

    /**
     * Store <tt>val</tt> in this property for the given component index.
     *
     * @param val            The value to store, can be null
     * @param componentIndex The index of the component being modified
     *
     * @throws ArrayIndexOutOfBoundsException if the componentIndex is invalid
     */
    public void set(short val, int componentIndex) {
        store.getArray()[componentIndex] = val;
    }

    @Override
    public IndexedDataStore getDataStore() {
        return store;
    }

    @Override
    public void setDataStore(IndexedDataStore store) {
        if (store == null) {
            throw new NullPointerException("Store cannot be null");
        }
        if (!(store instanceof ShortDataStore)) {
            throw new IllegalArgumentException(
                    "Store not compatible with ShortProperty, wrong type: " +
                    store.getClass());
        }

        ShortDataStore newStore = (ShortDataStore) store;
        if (newStore.elementSize != this.store.elementSize) {
            throw new IllegalArgumentException(
                    "Store not compatible with ShortProperty, wrong element size: " +
                    newStore.elementSize);
        }

        this.store = newStore;
    }

    /**
     * Factory to create ShortProperties. Properties annotated with DefaultShort will use
     * that value as the default for all components.
     *
     * @author Michael Ludwig
     */
    public static class Factory extends AbstractPropertyFactory<ShortProperty> {
        private final short defaultValue;

        public Factory(Attributes attrs) {
            super(attrs);

            if (attrs.hasAttribute(DefaultShort.class)) {
                defaultValue = attrs.getAttribute(DefaultShort.class).value();
            } else {
                defaultValue = 0;
            }
        }

        public Factory(short defaultValue) {
            super(null);
            this.defaultValue = defaultValue;
        }

        @Override
        public ShortProperty create() {
            return new ShortProperty();
        }

        @Override
        public void setDefaultValue(ShortProperty property, int index) {
            property.set(defaultValue, index);
        }
    }

    /**
     * Default short attribute for properties.
     *
     * @author Michael Ludwig
     */
    @Attribute
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface DefaultShort {
        short value();
    }
}
