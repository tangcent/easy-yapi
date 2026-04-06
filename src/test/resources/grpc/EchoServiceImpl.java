package com.itangcent.grpc.service;

import com.itangcent.grpc.EchoRequest;
import com.itangcent.grpc.EchoResponse;
import com.itangcent.grpc.EchoServiceGrpc;
import io.grpc.stub.StreamObserver;

/**
 * Echo service implementation.
 * Provides unary and streaming echo operations.
 */
public class EchoServiceImpl extends EchoServiceGrpc.EchoServiceImplBase {

    @Override
    public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
        EchoResponse response = new EchoResponse();
        response.setEchoed(request.getMessage());
        response.setCount(1);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<EchoRequest> echoStream(StreamObserver<EchoResponse> responseObserver) {
        return new StreamObserver<EchoRequest>() {
            private int count = 0;
            private StringBuilder sb = new StringBuilder();

            @Override
            public void onNext(EchoRequest value) {
                sb.append(value.getMessage());
                count++;
            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                EchoResponse response = new EchoResponse();
                response.setEchoed(sb.toString());
                response.setCount(count);
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }
}
