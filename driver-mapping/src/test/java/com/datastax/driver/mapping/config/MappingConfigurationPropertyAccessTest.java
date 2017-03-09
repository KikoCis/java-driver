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

import com.datastax.driver.core.CCMTestsSupport;
import com.datastax.driver.mapping.DefaultMappedProperty;
import com.datastax.driver.mapping.MappedProperty;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import com.datastax.driver.mapping.annotations.Transient;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for JAVA-1310 - validate ability configure property scope - getters vs. fields
 */
public class MappingConfigurationPropertyAccessTest extends CCMTestsSupport {

    @Override
    public void onTestContextInitialized() {
        execute("CREATE TABLE foo (k int primary key, v int)");
        execute("INSERT INTO foo (k, v) VALUES (1, 1)");
    }

    @Test(groups = "short")
    public void should_ignore_fields() {
        MappingConfiguration conf = MappingConfiguration.builder()
                .withPropertyAccessStrategy(new GetterSetterOnlyPropertyAccessStrategy())
                .build();
        MappingManager mappingManager = new MappingManager(session(), conf);
        mappingManager.mapper(Foo1.class);
    }

    @Table(name = "foo")
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Foo1 {
        private int k;

        private int notAColumn;

        @PartitionKey
        public int getK() {
            return k;
        }

        public void setK(int k) {
            this.k = k;
        }
    }

    @Test(groups = "short")
    public void should_ignore_getters() {
        MappingConfiguration conf = MappingConfiguration.builder()
                .withPropertyAccessStrategy(new FieldOnlyPropertyAccessStrategy())
                .build();
        MappingManager mappingManager = new MappingManager(session(), conf);
        mappingManager.mapper(Foo2.class);
    }

    @Table(name = "foo")
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Foo2 {
        @PartitionKey
        private int k;

        public int getNotAColumn() {
            return 1;
        }

        public boolean isNotAColumn2() {
            return true;
        }

    }

    @Test(groups = "short")
    public void should_map_fields_and_getters() {
        MappingConfiguration conf = MappingConfiguration.builder()
                .withPropertyAccessStrategy(new DefaultPropertyAccessStrategy())
                .build();
        MappingManager mappingManager = new MappingManager(session(), conf);
        Mapper<Foo3> mapper = mappingManager.mapper(Foo3.class);
        Foo3 foo = mapper.get(1);
        assertThat(foo.getV()).isEqualTo(1);
    }

    @Table(name = "foo")
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Foo3 {
        @PartitionKey
        private int k;

        @Transient
        private int storeVValueButNotMapped;

        @SuppressWarnings({"unused", "WeakerAccess"})
        public int getK() {
            return k;
        }

        public void setK(int k) {
            this.k = k;
        }

        public int getV() {
            return storeVValueButNotMapped;
        }

        public void setV(int v) {
            this.storeVValueButNotMapped = v;
        }
    }

    @Test(groups = "short")
    public void should_be_able_to_avoid_reflection() {
        MappingConfiguration conf = MappingConfiguration.builder()
                .withPropertyAccessStrategy(new NoReflectionPropertyAccessStrategy())
                .build();
        MappingManager mappingManager = new MappingManager(session(), conf);
        Mapper<Foo4> mapper = mappingManager.mapper(Foo4.class);
        Foo4 foo = mapper.get(1);
        assertThat(foo.getV()).isEqualTo(1);
    }

    @Table(name = "foo")
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Foo4 {

        @PartitionKey
        private int k;

        private int v;

        public int getK() {
            return k;
        }

        public void setK(int k) {
            this.k = k;
        }

        public int getV() {
            return v;
        }

        // fluent setter
        public Foo4 setV(int v) {
            this.v = v;
            return this;
        }
    }

    public static class NoReflectionPropertyAccessStrategy implements PropertyAccessStrategy {

        @Override
        public Set<MappedProperty<?>> mapProperties(List<Class<?>> classHierarchy) {

            DefaultMappedProperty<Integer> k = new DefaultMappedProperty<Integer>(
                    "k", "k", TypeToken.of(Integer.class),
                    true, false, false, 0, null) {
                @Override
                public Integer getValue(Object entity) {
                    return ((Foo4) entity).getK();
                }

                @Override
                public void setValue(Object entity, Integer value) {
                    ((Foo4) entity).setK(value);
                }
            };

            DefaultMappedProperty<Integer> v = new DefaultMappedProperty<Integer>(
                    "v", "v", TypeToken.of(Integer.class),
                    false, false, false, 0, null) {
                @Override
                public Integer getValue(Object entity) {
                    return ((Foo4) entity).getV();
                }

                @Override
                public void setValue(Object entity, Integer value) {
                    ((Foo4) entity).setV(value);
                }
            };

            return Sets.<MappedProperty<?>>newHashSet(k, v);
        }
    }

}
