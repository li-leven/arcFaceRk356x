package com.leven.arc.face.rk356x.sdk.source.util.face;

import androidx.annotation.Nullable;

import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.LivenessInfo;

/**
 * 人脸处理回调
 */
public interface FaceListener {
    /**
     * 当出现异常时执行
     *
     * @param e 异常信息
     */
    void onFail(Exception e);


    /**
     * 请求人脸特征后的回调
     *
     * @param faceFeature 人脸特征数据
     * @param trackId     人脸Id（相当于请求码）
     * @param errorCode   错误码
     */
    void onFaceFeatureInfoGet(@Nullable FaceFeature faceFeature, Integer trackId, Integer errorCode, byte[] nv21, FaceInfo faceInfo, int width, int height);

    /**
     * 请求活体检测后的回调
     *
     * @param livenessInfo 活体检测结果
     * @param trackId      人脸Id（相当于请求码）
     * @param errorCode    错误码
     */
    void onFaceLivenessInfoGet(@Nullable LivenessInfo livenessInfo, Integer trackId, Integer errorCode);
}
