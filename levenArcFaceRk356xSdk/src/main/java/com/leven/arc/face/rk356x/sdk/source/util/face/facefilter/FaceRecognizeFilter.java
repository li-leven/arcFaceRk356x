package com.leven.arc.face.rk356x.sdk.source.util.face.facefilter;


import com.leven.arc.face.rk356x.sdk.source.util.face.model.FacePreviewInfo;

import java.util.List;

/**
 * 人脸识别过滤器，仅保留满足条件的人脸，（只有满足条件的人脸才进行后续的活体检测、人脸识别操作）
 */
public interface FaceRecognizeFilter {
    void filter(List<FacePreviewInfo> facePreviewInfoList);
}
