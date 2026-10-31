package com.itangcent.grpc.generic;

import com.itangcent.grpc.EchoRequest;
import com.itangcent.grpc.EchoResponse;
import com.itangcent.grpc.generic.GenericEchoService;
import io.grpc.stub.StreamObserver;

/**
 * Concrete gRPC service binding the inherited `T` to [EchoRequest].
 *
 * The declared RPC method is the one the resolver can see: `GrpcMethodResolver`
 * iterates `PsiClass#getMethods`, which does not include inherited members.
 */
public class GenericEchoServiceImpl extends GenericEchoService<EchoRequest> {

    /**
     * stream echo requests
     */
    public StreamObserver<EchoRequest> clientStream(StreamObserver<EchoResponse> responseObserver) {
        return null;
    }
}
