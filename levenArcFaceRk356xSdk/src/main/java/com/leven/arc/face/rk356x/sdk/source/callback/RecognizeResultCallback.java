package com.leven.arc.face.rk356x.sdk.source.callback;


import com.leven.arc.face.rk356x.sdk.source.ui.model.CompareResult;

public interface RecognizeResultCallback {
    //人脸识别结果
    void onRecognizeResult(boolean status, CompareResult compareResult, Integer live, int code, String message);
}
