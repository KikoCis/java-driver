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
package com.datastax.driver.mapping.config;

import com.datastax.driver.mapping.AnnotatedMappedProperty;
import com.datastax.driver.mapping.DefaultAnnotatedMappedProperty;
import com.datastax.driver.mapping.MappedProperty;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * The default access strategy used by the mapper.
 * <p/>
 * This strategy can be configured to scan for {@link #isFieldScanAllowed() fields},
 * {@link #isGetterSetterScanAllowed() getters and setters}, or both.
 * The default is to scan for both.
 * <p/>
 * Note that if both field scan and getter/setter scan are disabled,
 * this strategy will not be able to detect any mapped property.
 * <p/>
 * This strategy recognizes standard getter and setter methods
 * (as defined by the Java Beans specification),
 * and also "relaxed" setter methods, i.e., setter methods
 * whose return type are not {@code void}.
 *
 * @see DefaultAnnotatedMappedProperty
 */
public class DefaultPropertyAccessStrategy implements PropertyAccessStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPropertyAccessStrategy.class);

    @Override
    public Set<AnnotatedMappedProperty<?>> mapProperties(List<Class<?>> classHierarchy) {
        Map<String, Object[]> fieldsGettersAndSetters = new HashMap<String, Object[]>();
        if (isFieldScanAllowed()) {
            Map<String, Field> fields = scanFields(classHierarchy);
            for (Map.Entry<String, Field> entry : fields.entrySet()) {
                String propertyName = entry.getKey();
                Field field = tryMakeAccessible(entry.getValue());
                fieldsGettersAndSetters.put(propertyName, new Object[]{field, null, null});
            }
        }
        if (isGetterSetterScanAllowed()) {
            Map<String, PropertyDescriptor> properties = scanProperties(classHierarchy);
            for (Map.Entry<String, PropertyDescriptor> entry : properties.entrySet()) {
                PropertyDescriptor property = entry.getValue();
                Method getter = tryMakeAccessible(locateGetter(classHierarchy.get(0), property));
                Method setter = tryMakeAccessible(locateSetter(classHierarchy.get(0), property));
                Object[] value = fieldsGettersAndSetters.get(entry.getKey());
                if (value != null) {
                    value[1] = getter;
                    value[2] = setter;
                } else if (getter != null || setter != null) {
                    fieldsGettersAndSetters.put(entry.getKey(), new Object[]{null, getter, setter});
                }
            }
        }
        Set<AnnotatedMappedProperty<?>> mappedProperties = new HashSet<AnnotatedMappedProperty<?>>(fieldsGettersAndSetters.size());
        for (Map.Entry<String, Object[]> entry : fieldsGettersAndSetters.entrySet()) {
            String propertyName = entry.getKey();
            Field field = (Field) entry.getValue()[0];
            Method getter = (Method) entry.getValue()[1];
            Method setter = (Method) entry.getValue()[2];
            Map<Class<? extends Annotation>, Annotation> annotations = scanPropertyAnnotations(field, getter);
            DefaultAnnotatedMappedProperty<?> property = new DefaultAnnotatedMappedProperty<Object>(propertyName, field, getter, setter, annotations);
            if (!property.isComputed() && field == null && getter == null) {
                LOGGER.warn(String.format("Property '%s' is not readable and will not be mapped", propertyName));
                continue;
            }
            if (field == null && setter == null) {
                LOGGER.warn(String.format("Property '%s' is not writable and will not be mapped", propertyName));
                continue;
            }
            mappedProperties.add(property);
        }
        return mappedProperties;
    }

    /**
     * Returns {@code true} if field scan is allowed, {@code false} otherwise.
     * <p/>
     * If this method returns {@code false}, the mapper will not attempt to scan mapped classes
     * for mappable fields.
     * <p/>
     * If both this method and {@link #isGetterSetterScanAllowed()} return {@code false},
     * then the mapper will not be able to read and write properties using built-in implementations
     * of this interface; the client application will have to provide its own implementation for this
     * strategy, possibly resorting to non-conventional access mechanisms, such as compile-time code generation.
     *
     * @return {@code true} if field access is allowed, {@code false} otherwise.
     */
    protected boolean isFieldScanAllowed() {
        return true;
    }

    /**
     * Returns {@code true} if getter and setter scan is allowed, {@code false} otherwise.
     * <p/>
     * If this method returns {@code false}, the mapper will not attempt to scan mapped classes
     * for mappable getters and setters.
     * <p/>
     * If both this method and {@link #isFieldScanAllowed()} return {@code false},
     * then the mapper will not be able to read and write properties using built-in implementations
     * of this interface; the client application will have to provide its own implementation for this
     * strategy, possibly resorting to non-conventional access mechanisms, such as compile-time code generation.
     *
     * @return {@code true} if getter and setter access is allowed, {@code false} otherwise.
     */
    protected boolean isGetterSetterScanAllowed() {
        return true;
    }

    /**
     * Locates a getter method for the given mapped class and given property.
     * <p/>
     * Most users should rely on the implementation provided here.
     * It is however possible to return any non-standard method, as long as it does
     * not take parameters, and its return type is assignable to (and covariant with) the property's type.
     * This might be particularly useful for boolean properties whose names are verbs, e.g. "{@code hasAccount}":
     * one could then return the non-standard method {@code boolean hasAccount()} as its getter.
     * <p/>
     * This method is never called if {@link #isGetterSetterScanAllowed()} returns {@code false}.
     * Besides, implementors are free to return {@code null} if access to the property through reflection is not required
     * (in which case, they will likely have to provide a custom implementation of {@link MappedProperty}).
     *
     * @param mappedClass The mapped class; this is necessarily a class annotated with
     *                    either {@link com.datastax.driver.mapping.annotations.Table @Table} or
     *                    {@link com.datastax.driver.mapping.annotations.UDT @UDT}.
     * @param property    The property to locate a getter for; never {@code null}.
     * @return The getter method for the given base class and given property, or {@code null} if no getter was found, or reflection is not required.
     */
    protected Method locateGetter(@SuppressWarnings("unused") Class<?> mappedClass, PropertyDescriptor property) {
        return property.getReadMethod();
    }

    /**
     * Locates a setter method for the given mapped class and given property.
     * <p/>
     * Most users should rely on the implementation provided here.
     * It is however possible to return any non-standard method, as long as it accepts one single parameter type
     * that is contravariant with the property's type.
     * <p/>
     * This method is never called if {@link #isGetterSetterScanAllowed()} returns {@code false}.
     * Besides, implementors are free to return {@code null} if access to the property through reflection is not required
     * (in which case, they will likely have to provide a custom implementation of {@link MappedProperty}).
     *
     * @param mappedClass The mapped class; this is necessarily a class annotated with
     *                    either {@link com.datastax.driver.mapping.annotations.Table @Table} or
     *                    {@link com.datastax.driver.mapping.annotations.UDT @UDT}.
     * @param property    The property to locate a setter for; never {@code null}.
     * @return The setter method for the given base class and given property, or {@code null} if no setter was found, or reflection is not required.
     */
    protected Method locateSetter(Class<?> mappedClass, PropertyDescriptor property) {
        Method setter = property.getWriteMethod();
        if (setter == null) {
            // JAVA-984: look for a "relaxed" setter, ie. a setter whose return type may be anything
            String propertyName = property.getName();
            String setterName = "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            try {
                Method m = mappedClass.getMethod(setterName, property.getPropertyType());
                if (!Modifier.isStatic(m.getModifiers()))
                    setter = m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return setter;
    }

    private static Map<String, Field> scanFields(List<Class<?>> classHierarchy) {
        HashMap<String, Field> fields = new HashMap<String, Field>();
        for (Class<?> clazz : classHierarchy) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()))
                    continue;
                // never override a more specific field masking another one declared in a superclass
                if (!fields.containsKey(field.getName()))
                    fields.put(field.getName(), field);
            }
        }
        return fields;
    }

    private static Map<String, PropertyDescriptor> scanProperties(List<Class<?>> classHierarchy) {
        Map<String, PropertyDescriptor> properties = new HashMap<String, PropertyDescriptor>();
        for (Class<?> clazz : classHierarchy) {
            // each time extract only current class properties
            BeanInfo beanInfo;
            try {
                beanInfo = Introspector.getBeanInfo(clazz, clazz.getSuperclass());
            } catch (IntrospectionException e) {
                throw Throwables.propagate(e);
            }
            for (PropertyDescriptor property : beanInfo.getPropertyDescriptors()) {
                if (!properties.containsKey(property.getName())) {
                    properties.put(property.getName(), property);
                }
            }
        }
        return properties;
    }

    private static Map<Class<? extends Annotation>, Annotation> scanPropertyAnnotations(Field field, Method getter) {
        Map<Class<? extends Annotation>, Annotation> annotations = new HashMap<Class<? extends Annotation>, Annotation>();
        // annotations on getters should have precedence over annotations on fields
        if (field != null)
            scanFieldAnnotations(field, annotations);
        if (getter != null)
            scanMethodAnnotations(getter, annotations);
        return annotations;
    }

    private static Map<Class<? extends Annotation>, Annotation> scanFieldAnnotations(Field field, Map<Class<? extends Annotation>, Annotation> annotations) {
        for (Annotation annotation : field.getAnnotations()) {
            annotations.put(annotation.annotationType(), annotation);
        }
        return annotations;
    }

    private static Map<Class<? extends Annotation>, Annotation> scanMethodAnnotations(Method method, Map<Class<? extends Annotation>, Annotation> annotations) {
        // 1. direct method annotations
        for (Annotation annotation : method.getAnnotations()) {
            annotations.put(annotation.annotationType(), annotation);
        }
        // 2. Class hierarchy: check for annotations in overridden methods in superclasses
        Class<?> getterClass = method.getDeclaringClass();
        for (Class<?> clazz = getterClass.getSuperclass(); clazz != null && !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            maybeAddOverriddenMethodAnnotations(annotations, method, clazz);
        }
        // 3. Interfaces: check for annotations in implemented interfaces
        for (Class<?> clazz = getterClass; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            for (Class<?> itf : clazz.getInterfaces()) {
                maybeAddOverriddenMethodAnnotations(annotations, method, itf);
            }
        }
        return annotations;
    }

    private static void maybeAddOverriddenMethodAnnotations(Map<Class<? extends Annotation>, Annotation> annotations, Method getter, Class<?> clazz) {
        try {
            Method overriddenGetter = clazz.getDeclaredMethod(getter.getName(), (Class<?>[]) getter.getParameterTypes());
            for (Annotation annotation : overriddenGetter.getAnnotations()) {
                // do not override a more specific version of the annotation type being scanned
                if (!annotations.containsKey(annotation.annotationType()))
                    annotations.put(annotation.annotationType(), annotation);
            }
        } catch (NoSuchMethodException e) {
            //ok
        }
    }

    private static <T extends AccessibleObject> T tryMakeAccessible(T object) {
        if (object != null && !object.isAccessible()) {
            try {
                object.setAccessible(true);
            } catch (SecurityException e) {
                // ok
            }
        }
        return object;
    }


}
