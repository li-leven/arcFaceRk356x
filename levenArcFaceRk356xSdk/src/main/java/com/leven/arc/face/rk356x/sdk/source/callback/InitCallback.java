package com.leven.arc.face.rk356x.sdk.source.callback;

public interface InitCallback {
    /**
     * 初始化结果
     * @param status
     * @param code
     * @param message
     */
    void onInitResult(boolean status, int code, String message);
}
