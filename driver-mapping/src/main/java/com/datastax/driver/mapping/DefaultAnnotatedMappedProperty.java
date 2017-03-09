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
import com.google.common.reflect.TypeToken;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default implementation of {@link AnnotatedMappedProperty}.
 * <p/>
 * This is the concrete class that the mapper will use if the
 * default {@link com.datastax.driver.mapping.config.PropertyAccessStrategy property access strategy}
 * is in use.
 * <p/>
 * Property values are read and written using the Java reflection API. Subclasses
 * may override relevant methods if they are capable of accessing
 * properties without incurring the cost of reflection.
 */
public class DefaultAnnotatedMappedProperty<T> extends DefaultMappedProperty<T> implements AnnotatedMappedProperty<T> {

    private final Field field;
    private final Method getter;
    private final Method setter;
    private final Map<Class<? extends Annotation>, Annotation> annotations;

    @SuppressWarnings("unchecked")
    public DefaultAnnotatedMappedProperty(String propertyName, Field field, Method getter, Method setter, Map<Class<? extends Annotation>, Annotation> annotations) {
        super(propertyName,
                inferMappedName(propertyName, annotations),
                (TypeToken<T>) inferType(field, getter),
                annotations.containsKey(PartitionKey.class),
                annotations.containsKey(ClusteringColumn.class),
                annotations.containsKey(Computed.class),
                inferPosition(annotations),
                (TypeCodec<T>) createCustomCodec(annotations)
        );
        this.field = field;
        this.getter = getter;
        this.setter = setter;
        this.annotations = annotations;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getValue(Object entity) {
        try {
            // try getter first, if available, otherwise direct field access
            if (getter != null && getter.isAccessible())
                return (T) getter.invoke(entity);
            else
                return (T) checkNotNull(field).get(entity);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read property '" + getPropertyName() + "' in " + entity.getClass(), e);
        }
    }

    @Override
    public void setValue(Object entity, T value) {
        try {
            // try setter first, if available, otherwise direct field access
            if (setter != null && setter.isAccessible())
                setter.invoke(entity, value);
            else
                checkNotNull(field).set(entity, value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to write property '" + getPropertyName() + "' in " + entity.getClass(), e);
        }
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationClass) {
        return annotations.containsKey(annotationClass);
    }

    @Override
    public Collection<Annotation> getAnnotations() {
        return annotations.values();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return (A) annotations.get(annotationClass);
    }

    private static TypeToken<?> inferType(Field field, Method getter) {
        if (getter != null)
            return TypeToken.of(getter.getGenericReturnType());
        else
            return TypeToken.of(checkNotNull(field).getGenericType());
    }

    private static String inferMappedName(String propertyName, Map<Class<? extends Annotation>, Annotation> annotations) {
        if (annotations.containsKey(Computed.class)) {
            String expression = ((Computed) annotations.get(Computed.class)).value();
            if (expression.isEmpty())
                throw new IllegalArgumentException(String.format("Property '%s': attribute 'value' of annotation @Computed is mandatory for computed properties", propertyName));
            return expression;
        }
        boolean caseSensitive = false;
        String columnName = propertyName;
        if (annotations.containsKey(Column.class)) {
            Column column = (Column) annotations.get(Column.class);
            caseSensitive = column.caseSensitive();
            if (!column.name().isEmpty())
                columnName = column.name();
        } else if (annotations.containsKey(com.datastax.driver.mapping.annotations.Field.class)) {
            com.datastax.driver.mapping.annotations.Field udtmappedField =
                    (com.datastax.driver.mapping.annotations.Field) annotations.get(com.datastax.driver.mapping.annotations.Field.class);
            caseSensitive = udtmappedField.caseSensitive();
            if (!udtmappedField.name().isEmpty())
                columnName = udtmappedField.name();
        }
        if (columnName.isEmpty())
            throw new IllegalArgumentException(String.format("Property '%s': could not infer mapped name", propertyName));
        return caseSensitive ? Metadata.quote(columnName) : columnName.toLowerCase();
    }

    private static int inferPosition(Map<Class<? extends Annotation>, Annotation> annotations) {
        if (annotations.containsKey(PartitionKey.class)) {
            return ((PartitionKey) annotations.get(PartitionKey.class)).value();
        }
        if (annotations.containsKey(ClusteringColumn.class)) {
            return ((ClusteringColumn) annotations.get(ClusteringColumn.class)).value();
        }
        return -1;
    }

    private static TypeCodec<?> createCustomCodec(Map<Class<? extends Annotation>, Annotation> annotations) {
        Class<? extends TypeCodec<?>> codecClass = getCustomCodecClass(annotations);
        if (codecClass.equals(Defaults.NoCodec.class))
            return null;
        return ReflectionUtils.newInstance(codecClass);
    }

    private static Class<? extends TypeCodec<?>> getCustomCodecClass(Map<Class<? extends Annotation>, Annotation> annotations) {
        Column column = (Column) annotations.get(Column.class);
        if (column != null)
            return column.codec();
        com.datastax.driver.mapping.annotations.Field udtField =
                (com.datastax.driver.mapping.annotations.Field) annotations.get(com.datastax.driver.mapping.annotations.Field.class);
        if (udtField != null)
            return udtField.codec();
        return Defaults.NoCodec.class;
    }

    @Override
    public String toString() {
        return getPropertyName();
    }

}
