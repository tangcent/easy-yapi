package com.itangcent.grpc;

public class EchoResponse {
    private String echoed;
    private int count;

    public String getEchoed() {
        return echoed;
    }

    public void setEchoed(String echoed) {
        this.echoed = echoed;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
