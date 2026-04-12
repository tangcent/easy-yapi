package org.springframework.http;

import java.util.*;

public class HttpHeaders implements Map<String, List<String>> {
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String AUTHORIZATION = "Authorization";
    
    @Override
    public int size() { return 0; }
    @Override
    public boolean isEmpty() { return false; }
    @Override
    public boolean containsKey(Object key) { return false; }
    @Override
    public boolean containsValue(Object value) { return false; }
    @Override
    public List<String> get(Object key) { return null; }
    @Override
    public List<String> put(String key, List<String> value) { return null; }
    @Override
    public List<String> remove(Object key) { return null; }
    @Override
    public void putAll(Map<? extends String, ? extends List<String>> m) {}
    @Override
    public void clear() {}
    @Override
    public Set<String> keySet() { return null; }
    @Override
    public Collection<List<String>> values() { return null; }
    @Override
    public Set<Entry<String, List<String>>> entrySet() { return null; }
}
