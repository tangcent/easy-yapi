package org.springframework.web.context.request.async;

import java.util.concurrent.CompletableFuture;

public class DeferredResult<T> {
    public DeferredResult() {}
    public DeferredResult(Long timeout) {}
    public DeferredResult(Long timeout, Object timeoutResult) {}
    
    public boolean setResult(T result) { return true; }
    public boolean setErrorResult(Object result) { return true; }
}
