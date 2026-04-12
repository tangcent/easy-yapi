package reactor.core.publisher;

import org.reactivestreams.Publisher;

public abstract class Mono<T> implements Publisher<T> {
    public static <T> Mono<T> just(T data) { return null; }
    public static <T> Mono<T> empty() { return null; }
    public static <T> Mono<T> error(Throwable error) { return null; }
    public static <T> Mono<T> from(Publisher<? extends T> source) { return null; }
}
