package io.grpc.stub;

public interface StreamObserver<T> {
    void onNext(T value);
    void onError(Throwable t);
    void onCompleted();
}
