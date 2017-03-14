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
 * Test for JAVA-1310 - validate ability configure property mapping strategy - whitelist vs. blacklist
 */
public class MappingConfigurationTransienceStrategyTest extends CCMTestsSupport {

    @Override
    public void onTestContextInitialized() {
        execute("CREATE TABLE foo (k int primary key, v int)");
        execute("INSERT INTO foo (k, v) VALUES (1, 1)");
    }

    @Test(groups = "short")
    public void should_map_only_non_transient() {
        MappingConfiguration conf = MappingConfiguration.builder()
                .withPropertyMapper(new DefaultPropertyMapper()
                        .setPropertyTransienceStrategy(PropertyTransienceStrategy.OPT_OUT))
                .build();
        MappingManager mappingManager = new MappingManager(session(), conf);
        Mapper<Foo1> mapper = mappingManager.mapper(Foo1.class);
        assertThat(mapper.get(1).getV()).isEqualTo(1);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    @Table(name = "foo")
    public static class Foo1 {

        @PartitionKey
        private int k;

        private int v;

        @Transient
        private int notAColumn;

        public int getK() {
            return k;
        }

        public void setK(int k) {
            this.k = k;
        }

        public int getV() {
            return v;
        }

        public void setV(int v) {
            this.v = v;
        }
    }

    @Test(groups = "short")
    public void should_map_only_annotated() {
        MappingConfiguration conf = MappingConfiguration.builder()
                .withPropertyMapper(new DefaultPropertyMapper()
                        .setPropertyTransienceStrategy(PropertyTransienceStrategy.OPT_IN))
                .build();
        MappingManager mappingManager = new MappingManager(session(), conf);
        mappingManager.mapper(Foo2.class);
    }

    @Table(name = "foo")
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Foo2 {
        @PartitionKey
        private int k;

        private int notAColumn;

        public int getK() {
            return k;
        }

        public void setK(int k) {
            this.k = k;
        }

        public int getNotAColumn() {
            return notAColumn;
        }

        public void setNotAColumn(int notAColumn) {
            this.notAColumn = notAColumn;
        }
    }

}
