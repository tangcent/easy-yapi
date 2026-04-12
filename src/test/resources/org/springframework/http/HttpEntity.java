package org.springframework.http;

public class HttpEntity<T> {
    public static final HttpEntity<?> EMPTY = new HttpEntity<>();
    
    public HttpEntity() {}
    public HttpEntity(T body) {}
    public HttpEntity(T body, HttpHeaders headers) {}
}
