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

import com.datastax.driver.mapping.MappedProperty;

/**
 * A strategy to determine which properties are transient, and which aren't.
 * <p/>
 * Transient properties will be ignored, whereas non-transient
 * ones will be mapped.
 * <p/>
 * The mapper will call {@link #isTransient(MappedProperty)}
 * for every {@link MappedProperty property}
 * discovered by the {@link PropertyAccessStrategy} in use,
 * thus allowing user code to fine-tune the mapping by forcibly
 * excluding properties that have been discovered but should not
 * be mapped.
 */
public interface PropertyTransienceStrategy {

    /**
     * Returns {@code true} if the given {@link MappedProperty property} is transient,
     * {@code false} otherwise.
     *
     * @param mappedProperty  The mapped property.
     * @return {@code true} if the given property is transient (i.e., non-mapped), {@code false} otherwise.
     */
    boolean isTransient(MappedProperty<?> mappedProperty);

}
