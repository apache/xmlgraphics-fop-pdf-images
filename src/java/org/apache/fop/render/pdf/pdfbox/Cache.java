/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fop.render.pdf.pdfbox;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

abstract class Cache<K, V> {

    public enum Type {
        WEAK, SOFT, STRONG;
    }

    public abstract V getValue(K key, ValueMaker<V> valueMaker) throws Exception;

    public static <K, V> Cache<K, V> createCache(Type cacheType) {
        switch (cacheType) {
            case WEAK:
                return new WeakDocumentCache<K, V>();
            case SOFT:
                return new SoftDocumentCache<K, V>();
            case STRONG:
                return new StrongDocumentCache<K, V>();
            default:
                return createDefaultCache();
        }
    }

    private static <K, V> Cache<K, V> createDefaultCache() {
        return new WeakDocumentCache<K, V>();
    }

    private static class StrongDocumentCache<K, V> extends Cache<K, V> {

        private final Map<K, V> cache = new HashMap<K, V>();

        @Override
        public V getValue(K key, ValueMaker<V> valueMaker) throws Exception {
            V value = cache.get(key);
            if (value == null) {
                value = valueMaker.make();
                cache.put(key, value);
            }
            return value;
        }
    }

    private static class SoftDocumentCache<K, V> extends Cache<K, V> {

        private final Map<K, SoftReference<Object>> softKeys = new HashMap<K, SoftReference<Object>>();

        private final Map<Object, V> cache = new WeakHashMap<Object, V>();

        @Override
        public V getValue(K key, ValueMaker<V> valueMaker) throws Exception {
            SoftReference<Object> reference = softKeys.get(key);
            Object softKey;
            if (reference == null || reference.get() == null) {
                softKey = new Object();
                reference = new SoftReference<Object>(softKey);
                softKeys.put(key, reference);
            } else {
                softKey = reference.get();
            }
            V value = cache.get(softKey);
            if (value == null) {
                value = valueMaker.make();
                cache.put(softKey, value);
            }
            return value;
        }
    }

    private static class WeakDocumentCache<K, V> extends Cache<K, V> {

        private V currentValue;

        private K currentKey;

        @Override
        public V getValue(K key, ValueMaker<V> valueMaker) throws Exception {
            if (!key.equals(currentKey)) {
                currentKey = key;
                currentValue = valueMaker.make();
            }
            return currentValue;
        }
    }

    public interface ValueMaker<V> {
        V make() throws Exception;
    }
}
