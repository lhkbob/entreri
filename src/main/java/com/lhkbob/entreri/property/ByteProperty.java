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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.lhkbob.entreri.Attribute;
import com.lhkbob.entreri.Attributes;
import com.lhkbob.entreri.ComponentData;
import com.lhkbob.entreri.Factory;
import com.lhkbob.entreri.IndexedDataStore;
import com.lhkbob.entreri.Property;

/**
 * ByteProperty is an implementation of Property that stores the property data
 * as a number of packed ints for each property.
 * 
 * @author Michael Ludwig
 */
@Factory(ByteProperty.Factory.class)
public final class ByteProperty implements Property {
    private ByteDataStore store;
    
    /**
     * Create an ByteProperty with an element size of 1.
     */
    public ByteProperty() {
        this(1);
    }
    
    /**
     * Create a new ByteProperty where each property will have
     * <tt>elementSize</tt> array elements together.
     * 
     * @param elementSize The element size of the property
     * @throws IllegalArgumentException if elementSize is less than 1
     */
    public ByteProperty(int elementSize) {
        store = new ByteDataStore(elementSize, new byte[elementSize]);
    }

    /**
     * Return the backing int array of this property's IndexedDataStore. The
     * array may be longer than necessary for the number of components in the
     * system. Data may be looked up for a specific component by scaling the
     * {@link ComponentData#getIndex() component's index} by the element size of the
     * property.
     * 
     * @return The byte data for all packed properties that this property has
     *         been packed with
     */
    public byte[] getIndexedData() {
        return store.array;
    }
    
    /**
     * Get the value stored in this property for the given component index, and
     * offset. Offset is measured from 0 to 1 minus the element size the
     * property was originally created with.
     * 
     * @param componentIndex The component's index
     * @param offset The offset into the component's data
     * @return The object at the given offset for the given component
     * @throws ArrayIndexOutOfBoundsException if the componentIndex and offset
     *             would access illegal indices
     */
    public byte get(int componentIndex, int offset) {
        return store.array[componentIndex * store.elementSize + offset];
    }

    /**
     * Store <tt>val</tt> in this property for the given component index, at the
     * specified offset. The offset is measured from 0 to 1 minus the element
     * size that this property was originally created with.
     * 
     * @param val The value to store, can be null
     * @param componentIndex The index of the component being modified
     * @param offset The offset into the component's data
     * @throws ArrayIndexOutOfBoundsException if the componentIndex and offset
     *             would access illegal indices
     */
    public void set(byte val, int componentIndex, int offset) {
        store.array[componentIndex * store.elementSize + offset] = val;
    }
    
    @Override
    public IndexedDataStore getDataStore() {
        return store;
    }
    
    @Override
    public void setDataStore(IndexedDataStore store) {
        if (store == null)
            throw new NullPointerException("Store cannot be null");
        if (!(store instanceof ByteDataStore))
            throw new IllegalArgumentException("Store not compatible with ByteProperty, wrong type: " + store.getClass());
        
        ByteDataStore newStore = (ByteDataStore) store;
        if (newStore.elementSize != this.store.elementSize)
            throw new IllegalArgumentException("Store not compatible with ByteProperty, wrong element size: " + newStore.elementSize);
        
        this.store = newStore;
    }
    
    /**
     * Factory to create ByteProperties. Properties annotated with DefaultByte
     * will use that value as the default for all components.
     * 
     * @author Michael Ludwig
     */
    public static class Factory extends AbstractPropertyFactory<ByteProperty> {
        private final int elementSize;
        private final byte defaultValue;
        
        public Factory(Attributes attrs) {
            super(attrs);
            
            if (attrs.hasAttribute(DefaultByte.class))
                defaultValue = attrs.getAttribute(DefaultByte.class).value();
            else
                defaultValue = 0;
            
            if (attrs.hasAttribute(ElementSize.class))
                elementSize = attrs.getAttribute(ElementSize.class).value();
            else
                elementSize = 1;
        }
        
        public Factory(int elementSize, byte defaultValue) {
            super(null);
            this.elementSize = elementSize;
            this.defaultValue = defaultValue;
        }

        @Override
        public ByteProperty create() {
            return new ByteProperty(elementSize);
        }

        @Override
        public void setDefaultValue(ByteProperty property, int index) {
            for (int i = 0; i < elementSize; i++)
                property.set(defaultValue, index, i);
        }
    }
    
    /**
     * Default byte attribute for properties.
     * @author Michael Ludwig
     *
     */
    @Attribute
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface DefaultByte {
        byte value();
    }

    private static class ByteDataStore extends AbstractIndexedDataStore<byte[]> {
        private final byte[] array;
        
        public ByteDataStore(int elementSize, byte[] array) {
            super(elementSize);
            this.array = array;
        }
        
        @Override
        public long memory() {
            return array.length;
        }
        
        @Override
        public ByteDataStore create(int size) {
            return new ByteDataStore(elementSize, new byte[elementSize * size]);
        }

        @Override
        protected byte[] getArray() {
            return array;
        }

        @Override
        protected int getArrayLength(byte[] array) {
            return array.length;
        }
    }
}
