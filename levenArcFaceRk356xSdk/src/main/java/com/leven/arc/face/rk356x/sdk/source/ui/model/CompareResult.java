package com.leven.arc.face.rk356x.sdk.source.ui.model;


import android.graphics.Bitmap;

import com.leven.arc.face.rk356x.sdk.source.facedb.entity.FaceEntity;


public class CompareResult {
    private FaceEntity faceEntity;
    private float similar;
    private int trackId;
    private int compareCode;
    private long cost;
    //识别的人脸图片
    private Bitmap compareImage;
    //当前识别的人脸框大小
    private int compareSize;

    public CompareResult(FaceEntity faceEntity, float similar) {
        this.faceEntity = faceEntity;
        this.similar = similar;
    }

    public CompareResult(FaceEntity faceEntity, float similar, int compareCode, long cost) {
        this.faceEntity = faceEntity;
        this.similar = similar;
        this.compareCode = compareCode;
        this.cost = cost;
    }

    public FaceEntity getFaceEntity() {
        return faceEntity;
    }

    public void setFaceEntity(FaceEntity faceEntity) {
        this.faceEntity = faceEntity;
    }

    public float getSimilar() {
        return similar;
    }

    public void setSimilar(float similar) {
        this.similar = similar;
    }

    public int getTrackId() {
        return trackId;
    }

    public void setTrackId(int trackId) {
        this.trackId = trackId;
    }

    public int getCompareCode() {
        return compareCode;
    }

    public void setCompareCode(int compareCode) {
        this.compareCode = compareCode;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public Bitmap getCompareImage() {
        return compareImage;
    }

    public void setCompareImage(Bitmap compareImage) {
        this.compareImage = compareImage;
    }

    public int getCompareSize() {
        return compareSize;
    }

    public void setCompareSize(int compareSize) {
        this.compareSize = compareSize;
    }
}
