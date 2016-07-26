/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.segment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Supplier;
import org.apache.jackrabbit.oak.segment.WriterCacheManager.Default;
import org.apache.jackrabbit.oak.segment.WriterCacheManager.Empty;
import org.junit.Test;

public class WriteCacheManagerTest {

    @Test
    public void emptyGenerations() {
        WriterCacheManager cache = Empty.INSTANCE;
        assertEquals(cache.getTemplateCache(0), cache.getTemplateCache(1));
        assertEquals(cache.getNodeCache(0), cache.getNodeCache(1));
        assertEquals(cache.getStringCache(0), cache.getStringCache(1));
    }

    @Test
    public void nonEmptyGenerations() {
        WriterCacheManager cache = new Default();
        assertNotEquals(cache.getTemplateCache(0), cache.getTemplateCache(1));
        assertNotEquals(cache.getNodeCache(0), cache.getNodeCache(1));
        assertNotEquals(cache.getStringCache(0), cache.getStringCache(1));
    }

    @Test
    public void factory() {
        WriterCacheManager cache = new Default(new Supplier<RecordCache<String>>() {
            int accessCount = 2;
            @Override
            public RecordCache<String> get() {
                assertTrue("Factory should only be invoked once per generation", --accessCount >= 0);
                return RecordCache.<String>factory(42).get();
            }
        }, RecordCache.<Template>factory(42), NodeCache.factory(42, 2));

        cache.getStringCache(0);
        cache.getStringCache(0);
        cache.getStringCache(1);
        cache.getStringCache(1);
        cache.getStringCache(1);
        cache.getStringCache(0);
    }
}
