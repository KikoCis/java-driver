/*
 *      Copyright (C) 2012-2015 DataStax Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.datastax.driver.mapping;

import com.datastax.driver.core.TypeCodec;
import com.google.common.reflect.TypeToken;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A base implementation of {@link MappedProperty}.
 * <p/>
 * Actual read and write operations are left for subclasses to implement.
 */
public abstract class DefaultMappedProperty<T> implements MappedProperty<T> {

    private final String propertyName;
    private final TypeToken<T> propertyType;
    private final String mappedName;
    private final boolean partitionKey;
    private final boolean clusteringColumn;
    private final boolean computed;
    private final int position;
    private final TypeCodec<T> customCodec;

    /**
     * Creates a new "regular" property, i.e. a property that is neither a partition key,
     * nor clustering column, nor a computed expression.
     *
     * @param propertyName The property name; may not be {@code null}.
     * @param mappedName   The mapped name; may not be {@code null}.
     * @param propertyType The property type; may not be {@code null}.
     */
    public DefaultMappedProperty(String propertyName, String mappedName, TypeToken<T> propertyType) {
        this(propertyName, mappedName, propertyType, false, false, false, -1, null);
    }

    /**
     * Creates a new property.
     *
     * @param propertyName     The property name; may not be {@code null}.
     * @param mappedName       The mapped name; may not be {@code null} nor empty.
     * @param propertyType     The property type; may not be {@code null} nor empty.
     * @param partitionKey     Whether or not this property is part of the partition key.
     * @param clusteringColumn Whether or not this property is a clustering column.
     * @param computed         Whether or not this property is computed.
     * @param position         The position of this property among partition key columns, or clustering columns.
     * @param customCodec      The custom codec to use for this property; may be {@code null}.
     */
    public DefaultMappedProperty(String propertyName, String mappedName, TypeToken<T> propertyType,
                                 boolean partitionKey, boolean clusteringColumn, boolean computed, int position, TypeCodec<T> customCodec) {
        checkArgument(propertyName != null && !propertyName.isEmpty());
        checkArgument(mappedName != null && !mappedName.isEmpty());
        checkNotNull(propertyType);
        this.propertyName = propertyName;
        this.mappedName = mappedName;
        this.propertyType = propertyType;
        this.partitionKey = partitionKey;
        this.clusteringColumn = clusteringColumn;
        this.computed = computed;
        this.position = position;
        this.customCodec = customCodec;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public TypeToken<T> getPropertyType() {
        return propertyType;
    }

    @Override
    public String getMappedName() {
        return mappedName;
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public TypeCodec<T> getCustomCodec() {
        return customCodec;
    }

    @Override
    public boolean isComputed() {
        return computed;
    }

    @Override
    public boolean isPartitionKey() {
        return partitionKey;
    }

    @Override
    public boolean isClusteringColumn() {
        return clusteringColumn;
    }

    @Override
    public String toString() {
        return getPropertyName();
    }

}
