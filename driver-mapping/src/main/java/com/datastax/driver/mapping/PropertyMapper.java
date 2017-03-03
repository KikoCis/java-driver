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

import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.mapping.annotations.*;
import com.datastax.driver.mapping.config.MappingConfiguration;
import com.google.common.reflect.TypeToken;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Maps a Java bean property to a table column or a UDT field.
 * <p>
 * Properties can be either accessed through getter and setter pairs,
 * or by direct field access, depending on what is available in the
 * entity/UDT class.
 */
class PropertyMapper {

    final String alias;
    final String columnName;
    final int position;
    final TypeToken<Object> javaType;
    final TypeCodec<Object> customCodec;
    private final String propertyName;
    private final Field field;
    private final Method getter;
    private final Method setter;
    private final Map<Class<? extends Annotation>, Annotation> annotations;
    private final MappingConfiguration configuration;

    PropertyMapper(String propertyName, String alias, Field field, Method getter, Method setter, Map<Class<? extends Annotation>, Annotation> annotations, MappingConfiguration configuration) {
        this.propertyName = propertyName;
        this.alias = alias;
        this.field = field;
        this.getter = getter;
        this.setter = setter;
        this.annotations = annotations;
        this.configuration = configuration;
        if (field != null)
            ReflectionUtils.tryMakeAccessible(field);
        if (getter != null)
            ReflectionUtils.tryMakeAccessible(getter);
        if (setter != null)
            ReflectionUtils.tryMakeAccessible(setter);
        checkArgument((field != null && field.isAccessible()) || (getter != null && getter.isAccessible()),
                "Property '%s' is not readable", propertyName);
        checkArgument((field != null && field.isAccessible()) || (setter != null && setter.isAccessible()),
                "Property '%s' is not writable", propertyName);
        columnName = inferColumnName();
        position = inferPosition();
        javaType = inferJavaType();
        customCodec = createCustomCodec();
    }

    Object getValue(Object entity) {
        return configuration.getPropertyAccessStrategy().getValue(entity, propertyName, field, getter);
    }

    void setValue(Object entity, Object value) {
        configuration.getPropertyAccessStrategy().setValue(entity, value, propertyName, field, setter);
    }

    boolean hasAnnotation(Class<? extends Annotation> annotationClass) {
        return annotations.containsKey(annotationClass);
    }

    Collection<Annotation> getAnnotations() {
        return annotations.values();
    }

    @SuppressWarnings("unchecked")
    <A extends Annotation> A annotation(Class<A> annotationClass) {
        return (A) annotations.get(annotationClass);
    }

    boolean isComputed() {
        return hasAnnotation(Computed.class);
    }

    boolean isPartitionKey() {
        return hasAnnotation(PartitionKey.class);
    }

    boolean isClusteringColumn() {
        return hasAnnotation(ClusteringColumn.class);
    }

    private String inferColumnName() {
        if (isComputed()) {
            return annotation(Computed.class).value();
        }
        boolean caseSensitive = false;
        String columnName = propertyName;
        if (hasAnnotation(Column.class)) {
            Column column = annotation(Column.class);
            caseSensitive = column.caseSensitive();
            if (!column.name().isEmpty())
                columnName = column.name();
        } else if (hasAnnotation(com.datastax.driver.mapping.annotations.Field.class)) {
            com.datastax.driver.mapping.annotations.Field udtField = annotation(com.datastax.driver.mapping.annotations.Field.class);
            caseSensitive = udtField.caseSensitive();
            if (!udtField.name().isEmpty())
                columnName = udtField.name();
        }
        return caseSensitive ? Metadata.quote(columnName) : columnName.toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private TypeToken<Object> inferJavaType() {
        Type type;
        if (getter != null)
            type = getter.getGenericReturnType();
        else
            type = field.getGenericType();
        return (TypeToken<Object>) TypeToken.of(type);
    }

    private int inferPosition() {
        if (isPartitionKey()) {
            return annotation(PartitionKey.class).value();
        }
        if (isClusteringColumn()) {
            return annotation(ClusteringColumn.class).value();
        }
        return -1;
    }

    private TypeCodec<Object> createCustomCodec() {
        Class<? extends TypeCodec<?>> codecClass = getCustomCodecClass();
        if (codecClass.equals(Defaults.NoCodec.class))
            return null;
        @SuppressWarnings("unchecked")
        TypeCodec<Object> instance = (TypeCodec<Object>) ReflectionUtils.newInstance(codecClass);
        return instance;
    }

    private Class<? extends TypeCodec<?>> getCustomCodecClass() {
        Column column = annotation(Column.class);
        if (column != null)
            return column.codec();
        com.datastax.driver.mapping.annotations.Field udtField = annotation(com.datastax.driver.mapping.annotations.Field.class);
        if (udtField != null)
            return udtField.codec();
        return Defaults.NoCodec.class;
    }

    @Override
    public String toString() {
        return propertyName;
    }

}
