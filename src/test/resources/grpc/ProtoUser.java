package com.itangcent.grpc;

import com.google.protobuf.GeneratedMessageV3;

public class ProtoUser extends GeneratedMessageV3 {

    private Long id;
    private String name;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
