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
import com.datastax.driver.mapping.*;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import com.datastax.driver.mapping.annotations.Transient;
import org.testng.annotations.Test;

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
                .withPropertyMapper(new DefaultPropertyMapper()
                        .setPropertyAccessStrategy(PropertyAccessStrategy.GETTERS_AND_SETTERS))
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
                .withPropertyMapper(new DefaultPropertyMapper()
                        .setPropertyAccessStrategy(PropertyAccessStrategy.FIELDS))
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
                .withPropertyMapper(new DefaultPropertyMapper()
                        .setPropertyAccessStrategy(PropertyAccessStrategy.BOTH))
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

}
