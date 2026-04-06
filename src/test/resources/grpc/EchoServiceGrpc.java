package com.itangcent.grpc;

import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

public class EchoServiceGrpc {

    public static abstract class EchoServiceImplBase implements BindableService {

        public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
        }

        public StreamObserver<EchoRequest> echoStream(StreamObserver<EchoResponse> responseObserver) {
            return new StreamObserver<EchoRequest>() {
                @Override
                public void onNext(EchoRequest value) {
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                }
            };
        }
    }
}
