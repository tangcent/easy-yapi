package org.springframework.http;

import java.net.URI;

public class RequestEntity<T> extends HttpEntity<T> {
    public RequestEntity(T body, HttpHeaders headers, HttpMethod method, URI url) {}
    public RequestEntity(T body, HttpMethod method, URI url) {}
}
