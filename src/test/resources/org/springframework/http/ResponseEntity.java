package org.springframework.http;

public class ResponseEntity<T> extends HttpEntity<T> {
    public ResponseEntity(T body, HttpStatus status) {}
    public ResponseEntity(T body, HttpHeaders headers, HttpStatus status) {}
    public static <T> ResponseEntity<T> ok(T body) { return null; }
    public static <T> ResponseEntity<T> status(HttpStatus status) { return null; }
}
