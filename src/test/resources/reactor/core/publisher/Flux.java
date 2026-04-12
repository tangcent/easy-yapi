package reactor.core.publisher;

import org.reactivestreams.Publisher;
import java.util.Collection;

public abstract class Flux<T> implements Publisher<T> {
    public static <T> Flux<T> just(T... data) { return null; }
    public static <T> Flux<T> fromIterable(Iterable<? extends T> it) { return null; }
    public static <T> Flux<T> from(Publisher<? extends T> source) { return null; }
    public static <T> Flux<T> empty() { return null; }
}
