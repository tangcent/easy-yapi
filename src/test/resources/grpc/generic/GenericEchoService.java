package com.itangcent.grpc.generic;

import com.itangcent.grpc.EchoResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

/**
 * Generic gRPC service base. The streaming method returns
 * `StreamObserver<T>`, so a subclass binding `T` exercises generic
 * substitution in the `export.after` lifecycle event.
 */
public abstract class GenericEchoService<T> implements BindableService {

    /**
     * stream the generic items
     */
    public StreamObserver<T> echoStream(StreamObserver<EchoResponse> responseObserver) {
        return null;
    }
}
