package org.apache.fop.render.pdf.pdfbox;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

abstract class Cache<K,V> {

    public enum Type {
        WEAK, SOFT, STRONG;
    }

    public abstract V getValue(K key, ValueMaker<V> valueMaker) throws Exception;

    public static <K,V> Cache<K,V> createCache(Type cacheType) {
        switch (cacheType) {
            case WEAK: return new WeakDocumentCache<K,V>();
            case SOFT: return new SoftDocumentCache<K,V>();
            case STRONG: return new StrongDocumentCache<K,V>();
            default: return createDefaultCache();
        }
    }

    private static <K,V> Cache<K,V> createDefaultCache() {
        return new WeakDocumentCache<K,V>();
    }

    private static class StrongDocumentCache<K,V> extends Cache<K,V> {

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

    private static class SoftDocumentCache<K,V> extends Cache<K,V> {

        private final Map<K, SoftReference<Object>> softKeys
                = new HashMap<K, SoftReference<Object>>();

        private final Map<Object, V> cache = new WeakHashMap<Object, V>();

        private Object currentKey;

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
            currentKey = softKey;
            V value = cache.get(softKey);
            if (value == null) {
                value = valueMaker.make();
                cache.put(softKey, value);
            }
            return value;
        }
    }

    private static class WeakDocumentCache<K,V> extends Cache<K,V> {

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

    public static interface ValueMaker<V> {
        V make() throws Exception;
    }
}
