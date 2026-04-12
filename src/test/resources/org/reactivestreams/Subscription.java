package org.reactivestreams;

public interface Subscription {
    void request(long n);
    void cancel();
}
