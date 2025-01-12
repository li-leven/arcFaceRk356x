package com.leven.arc.face.rk356x.sdk.source.faceserver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.FaceFeatureInfo;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.MaskInfo;
import com.arcsoft.face.SearchResult;
import com.arcsoft.face.enums.DetectFaceOrientPriority;
import com.arcsoft.face.enums.DetectMode;
import com.arcsoft.face.enums.ExtractType;
import com.arcsoft.imageutil.ArcSoftImageFormat;
import com.arcsoft.imageutil.ArcSoftImageUtil;
import com.arcsoft.imageutil.ArcSoftImageUtilError;
import com.arcsoft.imageutil.ArcSoftRotateDegree;
import com.leven.arc.face.rk356x.sdk.ConfigUtils;
import com.leven.arc.face.rk356x.sdk.exception.ExceptionEnum;
import com.leven.arc.face.rk356x.sdk.exception.SdkException;
import com.leven.arc.face.rk356x.sdk.source.facedb.FaceDatabase;
import com.leven.arc.face.rk356x.sdk.source.facedb.entity.FaceEntity;
import com.leven.arc.face.rk356x.sdk.source.ui.model.CompareResult;
import com.leven.arc.face.rk356x.sdk.source.util.ConfigUtil;
import com.leven.arc.face.rk356x.sdk.source.util.ErrorCodeUtil;
import com.leven.arc.face.rk356x.sdk.source.util.ImageUtil;
import com.leven.arc.face.rk356x.sdk.source.util.face.model.FacePreviewInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * 人脸库操作类，包含注册和搜索
 */
public class FaceServer {
    private static final String TAG = "FaceServer";
    private static FaceEngine faceEngine = null;
    private static volatile FaceServer faceServer = null;
    private List<FaceEntity> faceRegisterInfoList;
    private String imageRootPath;
    /**
     * 最大注册人脸数
     */
    private static final int MAX_REGISTER_FACE_COUNT = 30000;
    private Context context;

    /**
     * 用于特征提取的引擎
     */
    private FaceEngine frEngine;

    /**
     * 用于获取人脸信息的引擎
     */
    private FaceEngine faceInfoEngine;

    private FaceServer() {
        faceRegisterInfoList = new ArrayList<>();
    }

    public static FaceServer getInstance() {
        if (faceServer == null) {
            synchronized (FaceServer.class) {
                if (faceServer == null) {
                    faceServer = new FaceServer();
                }
            }
        }
        return faceServer;
    }

    public interface OnInitFinishedCallback {
        void onFinished(int faceCount, boolean status);
    }

    public void init(Context context) throws SdkException {
        init(context, null);
    }

    public synchronized void init(Context context, OnInitFinishedCallback onInitFinishedCallback) throws SdkException {
        if (faceEngine == null && context != null) {
            this.context = context;
            faceEngine = new FaceEngine();
            int engineCode = faceEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_ALL_OUT,
                    1, FaceEngine.ASF_FACE_RECOGNITION | FaceEngine.ASF_FACE_DETECT | FaceEngine.ASF_MASK_DETECT);
            if (engineCode == ErrorInfo.MOK) {
                initFaceList(context,null, onInitFinishedCallback, false);
            } else {
                faceEngine = null;
                Log.e(TAG, "init: failed! code = " + engineCode);
            }
        }
        if(frEngine == null && context != null){
            frEngine = new FaceEngine();
            boolean enableFaceQualityDetect = ConfigUtil.isEnableImageQualityDetect(context);
            int frEngineMask = FaceEngine.ASF_FACE_RECOGNITION;
            if (enableFaceQualityDetect) {
                frEngineMask |= FaceEngine.ASF_IMAGEQUALITY;
            }
            int frCode = frEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_0_ONLY,
                    10, frEngineMask);
            if(frCode != ErrorInfo.MOK){
                throw new SdkException(frCode, "初始化失败");
            }
            initFaceList(context, frEngine, (faceCount, status) -> {
                System.out.println("faceCount:" + faceCount);
                System.out.println("status:" + status);
            }, true);
        }

        if(faceInfoEngine == null && context != null){
            int processMask = FaceEngine.ASF_AGE | FaceEngine.ASF_GENDER | FaceEngine.ASF_LIVENESS | FaceEngine.ASF_MASK_DETECT;
            faceInfoEngine = new FaceEngine();
            int faceInfoCode = faceInfoEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_ALL_OUT,
                    5, FaceEngine.ASF_FACE_RECOGNITION | FaceEngine.ASF_FACE_DETECT | processMask);
            if(faceInfoCode != ErrorInfo.MOK){
                throw new SdkException(faceInfoCode, "初始化失败");
            }
        }
        if (faceRegisterInfoList != null && onInitFinishedCallback != null) {
            onInitFinishedCallback.onFinished(faceRegisterInfoList.size(), faceEngine != null);
        }
    }

    /**
     * 销毁
     */
    public synchronized void release() {
        if (faceRegisterInfoList != null) {
            faceRegisterInfoList.clear();
            faceRegisterInfoList = null;
        }
        if (faceEngine != null) {
            synchronized (faceEngine) {
                faceEngine.unInit();
            }
            faceEngine = null;
        }
        faceServer = null;
    }

    /**
     * 初始化人脸特征数据以及人脸特征数据对应的注册图
     *
     * @param faceEngine             指定FaceEngine
     * @param onInitFinishedCallback 加载完成的回调
     * @param recognize              是否处于人脸识别流程
     */
    public void initFaceList(Context context, FaceEngine faceEngine, final OnInitFinishedCallback onInitFinishedCallback, boolean recognize) {
        Disposable disposable = Observable.create((ObservableOnSubscribe<Integer>) emitter -> {
            if (recognize) {
                List<FaceEntity> faceEntityList = FaceDatabase.getInstance(context).faceDao().getAllFaces();
                registerFaceFeatureInfoListFromDb(faceEngine, faceEntityList);
                emitter.onNext(faceEntityList.size());
            } else {
                faceRegisterInfoList = FaceDatabase.getInstance(context).faceDao().getAllFaces();
                emitter.onNext(faceRegisterInfoList == null ? 0 : faceRegisterInfoList.size());
            }
            emitter.onComplete();
        }).subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(size -> {
                    if (onInitFinishedCallback != null) {
                        onInitFinishedCallback.onFinished(size, true);
                    }
                });
    }

    public synchronized void removeOneFace(FaceEntity faceEntity) {
        if (faceRegisterInfoList != null) {
            faceRegisterInfoList.remove(faceEntity);
        }
    }

    @SuppressLint("CheckResult")
    public synchronized int clearAllFaces() {
        if (faceRegisterInfoList != null) {
            faceRegisterInfoList.clear();
        }
        if (faceEngine != null) {
            faceEngine.removeFaceFeature(-1);
        }
//        Context applicationContext = ArcFaceApplication.getApplication().getApplicationContext();
        int deleteSize = FaceDatabase.getInstance(context).faceDao().deleteAll();
        File imgDir = new File(getImageDir());
        File[] files = imgDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        return deleteSize;
    }

    /**
     * 用于预览时注册人脸
     *
     * @param nv21               NV21数据
     * @param width              NV21宽度
     * @param height             NV21高度
     * @param faceInfo           {@link FaceEngine#detectFaces(byte[], int, int, int, List)}获取的人脸信息
     * @param name               保存的名字，若为空则使用时间戳
     * @param frEngine           添加人脸数据，用于后续{@link FaceEngine#searchFaceFeature(FaceFeature)}
     * @param registerFaceEngine 用于{@link FaceEngine#extractFaceFeature(byte[], int, int, int, FaceInfo, ExtractType, int, FaceFeature)}注册人脸到本地数据库
     * @return 是否注册成功
     */
    public boolean registerNv21(Context context, byte[] nv21, int width, int height, FacePreviewInfo faceInfo, String userId, String name,
                                boolean registerMultiple, Float similar, FaceEngine frEngine, FaceEngine registerFaceEngine) throws SdkException, IOException {
        if (registerFaceEngine == null) {
            throw new SdkException("registerFaceEngine is null");
        }
        if(context == null){
            throw new SdkException("context is null");
        }
        if(width % 4 != 0){
            throw new SdkException("width % 4 != 0");
        }
        if(nv21.length != width * height * 3 / 2){
            throw new SdkException("nv21.length != width * height * 3 / 2");
        }
        FaceFeature faceFeature = new FaceFeature();
        int code;
        /*
         * 特征提取，注册人脸时extractType值为ExtractType.REGISTER，mask的值为MaskInfo.NOT_WORN
         */
        synchronized (registerFaceEngine) {
            code = registerFaceEngine.extractFaceFeature(nv21, width, height, FaceEngine.CP_PAF_NV21, faceInfo.getFaceInfoRgb(),
                    ExtractType.REGISTER, MaskInfo.NOT_WORN, faceFeature);
        }
        if (code != ErrorInfo.MOK) {
            throw new SdkException(code, "extractFaceFeature failed");
        } else {
            if(!registerMultiple){
                //相同人脸是否可以多次注册
                CompareResult compareResult = searchFaceFeature(faceFeature, frEngine);
                if(compareResult != null && compareResult.getSimilar() > similar){
                    throw new SdkException(ExceptionEnum.SIMILAR_FACE_EXISTS, "存在相同人脸");
                }
            }
            /*
             * 1.保存注册结果（注册图、特征数据）
             * 2.为了美观，扩大rect截取注册图
             */
            Rect cropRect = getBestRect(width, height, faceInfo.getFaceInfoRgb().getRect());
            if (cropRect == null) {
                throw new SdkException("cropRect is null");
            }

            cropRect.left &= ~3;
            cropRect.top &= ~3;
            cropRect.right &= ~3;
            cropRect.bottom &= ~3;

            // 创建一个头像的Bitmap，存放旋转结果图
            Bitmap headBmp = getHeadImage(nv21, width, height, faceInfo.getFaceInfoRgb().getOrient(), cropRect, ArcSoftImageFormat.NV21);
            String imgPath = getImagePath(userId, name);
            FileOutputStream fos = new FileOutputStream(imgPath);
            headBmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
            FaceEntity faceEntity = new FaceEntity(userId, name, imgPath, faceFeature.getFeatureData());
            long faceId = FaceDatabase.getInstance(context).faceDao().insert(faceEntity);
            faceEntity.setFaceId(faceId);
            //新加内容
            faceInfo.setFaceEntity(faceEntity);
            registerFaceFeatureInfoFromDb(faceEntity, frEngine);
            //内存中数据同步
            if (faceRegisterInfoList == null) {
                faceRegisterInfoList = new ArrayList<>();
            }
            faceRegisterInfoList.add(faceEntity);
            return true;
        }
    }

    /**
     * 通过FaceEngine注册多个人脸数据
     * @param faceEngine    指定FaceEngine
     * @param faceEntityList 人脸数据集
     */
    private void registerFaceFeatureInfoListFromDb(FaceEngine faceEngine, List<FaceEntity> faceEntityList) {
        List<FaceFeatureInfo> faceFeatureInfoList = new ArrayList<>();
        for (FaceEntity faceEntity : faceEntityList) {
            FaceFeatureInfo faceFeatureInfo = new FaceFeatureInfo((int) faceEntity.getFaceId(), faceEntity.getFeatureData());
            faceFeatureInfoList.add(faceFeatureInfo);
        }
        if (faceEngine != null) {
            //首先清除FaceEngine中所有人脸数据，再添加新的人脸数据
            faceEngine.removeFaceFeature(-1);
            int res = faceEngine.registerFaceFeature(faceFeatureInfoList);
            Log.i(TAG, "registerFaceFeature:" + res);
        }
    }

    /**
     * 通过FaceEngine注册单个人脸数据
     *
     * @param faceEngine 指定FaceEngine
     * @param faceEntity 指定人脸数据
     */
    private void registerFaceFeatureInfoFromDb(FaceEntity faceEntity, FaceEngine faceEngine) {
        if (faceEntity != null && faceEngine != null) {
            FaceFeatureInfo faceFeatureInfo = new FaceFeatureInfo((int) faceEntity.getFaceId(), faceEntity.getFeatureData());
            int res = faceEngine.registerFaceFeature(faceFeatureInfo);
            Log.i(TAG, "registerFaceFeature:" + res);
        }
    }

    /**
     * 获取存放注册照的文件夹路径
     *
     * @return 存放注册照的文件夹路径
     */
    private String getImageDir() {
        return ConfigUtils.REGISTER_DIR + File.separator + "faceDB" + File.separator + "registerFaces";
    }

    /**
     * 根据用户名获取注册图保存路径
     *
     * @param name 用户名
     * @return 图片保存地址
     */
    private String getImagePath(String id, String name) {
        if (imageRootPath == null) {
            imageRootPath = getImageDir();
            File dir = new File(imageRootPath);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
        }
        return imageRootPath + File.separator + id + ConfigUtils.IMAGE_SPLIT_STR + name + ".jpg";
    }

    /**
     * 注册一个jpg数据
     *
     * @param jpeg
     * @param name
     * @return
     */
    public FaceEntity registerJpeg(byte[] jpeg, String id, String name, boolean registerMultiple, float similar) throws RegisterFailedException, SdkException {
        if (faceRegisterInfoList != null && faceRegisterInfoList.size() >= MAX_REGISTER_FACE_COUNT) {
            Log.e(TAG, "registerJpeg: registered face count limited " + faceRegisterInfoList.size());
            // 已达注册上限，超过该值会影响识别率
            throw new RegisterFailedException("registered face count limited");
        }
        Bitmap bitmap = ImageUtil.jpegToScaledBitmap(jpeg, ImageUtil.DEFAULT_MAX_WIDTH, ImageUtil.DEFAULT_MAX_HEIGHT);
        bitmap = ArcSoftImageUtil.getAlignedBitmap(bitmap, true);
        byte[] imageData = ArcSoftImageUtil.createImageData(bitmap.getWidth(), bitmap.getHeight(), ArcSoftImageFormat.BGR24);
        int code = ArcSoftImageUtil.bitmapToImageData(bitmap, imageData, ArcSoftImageFormat.BGR24);
        if (code != ArcSoftImageUtilError.CODE_SUCCESS) {
            throw new RuntimeException("bitmapToImageData failed, code is " + code);
        }
        return registerBgr24(imageData, bitmap.getWidth(), bitmap.getHeight(), id, name, registerMultiple, similar);
    }

    /**
     * 用于注册照片人脸
     *
     * @param bgr24   bgr24数据
     * @param width   bgr24宽度
     * @param height  bgr24高度
     * @param name    保存的名字，若为空则使用时间戳
     * @return 注册成功后的人脸信息
     */
    public FaceEntity registerBgr24(byte[] bgr24, int width, int height, String id, String name, boolean registerMultiple, float similar) throws SdkException {
//        if (faceEngine == null || context == null || bgr24 == null || width % 4 != 0 || bgr24.length != width * height * 3) {
//            Log.e(TAG, "registerBgr24:  invalid params");
//            return null;
//        }
        if(faceEngine == null){
            throw new SdkException("faceEngine is null");
        }
        if(context == null){
            throw new SdkException("context is null");
        }
        if(bgr24 == null){
            throw new SdkException("bgr24 is null");
        }
        if(width % 4 != 0){
            throw new SdkException("bgr24 width is error");
        }
        if(bgr24.length != width * height * 3){
            throw new SdkException("bgr24 length is error");
        }
        //人脸检测
        List<FaceInfo> faceInfoList = new ArrayList<>();
        int code;
        synchronized (faceEngine) {
            code = faceEngine.detectFaces(bgr24, width, height, FaceEngine.CP_PAF_BGR24, faceInfoList);
        }
        if (code == ErrorInfo.MOK && !faceInfoList.isEmpty()) {
            code = faceEngine.process(bgr24, width, height, FaceEngine.CP_PAF_BGR24, faceInfoList,
                    FaceEngine.ASF_MASK_DETECT);
            if (code == ErrorInfo.MOK) {
                List<MaskInfo> maskInfoList = new ArrayList<>();
                faceEngine.getMask(maskInfoList);
                if (!maskInfoList.isEmpty()) {
                    int isMask = maskInfoList.get(0).getMask();
                    if (isMask == MaskInfo.WORN) {
                        /*
                         * 注册照要求不戴口罩
                         */
                        throw new SdkException(ExceptionEnum.REGISTER_MASK_ERROR, "maskInfo is worn");
                    }
                }
            }

            FaceFeature faceFeature = new FaceFeature();
            /*
             * 特征提取，注册人脸时参数extractType值为ExtractType.REGISTER，参数mask的值为MaskInfo.NOT_WORN
             */
            synchronized (faceEngine) {
                code = faceEngine.extractFaceFeature(bgr24, width, height, FaceEngine.CP_PAF_BGR24, faceInfoList.get(0),
                        ExtractType.REGISTER, MaskInfo.NOT_WORN, faceFeature);
            }
            if(!registerMultiple){
                CompareResult compareResult = searchFaceFeature(faceFeature, frEngine);
                if(compareResult != null && compareResult.getSimilar() > similar){
                    throw new SdkException(ExceptionEnum.SIMILAR_FACE_EXISTS, "存在相同人脸");
                }
            }
            String userId = id == null ? String.valueOf(System.currentTimeMillis()) : id;
            String userName = name == null ? String.valueOf(System.currentTimeMillis()) : name;

            //保存注册结果（注册图、特征数据）
            if (code == ErrorInfo.MOK) {
                //为了美观，扩大rect截取注册图
                Rect cropRect = getBestRect(width, height, faceInfoList.get(0).getRect());
                if (cropRect == null) {
//                    Log.e(TAG, "registerBgr24: cropRect is null");
//                    return null;
                    throw new SdkException(ExceptionEnum.CROP_RECT_ERROR, "cropRect is null");
                }

                cropRect.left &= ~3;
                cropRect.top &= ~3;
                cropRect.right &= ~3;
                cropRect.bottom &= ~3;

                String imgPath = getImagePath(userId, userName);

                // 创建一个头像的Bitmap，存放旋转结果图
                Bitmap headBmp = getHeadImage(bgr24, width, height, faceInfoList.get(0).getOrient(), cropRect, ArcSoftImageFormat.BGR24);

                try {
                    FileOutputStream fos = new FileOutputStream(imgPath);
                    headBmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.close();
                } catch (IOException e) {
//                    e.printStackTrace();
//                    return null;
                    throw new SdkException(ExceptionEnum.DEFAULT_ERROR, e.getMessage());
                }

                // 内存中的数据同步
                if (faceRegisterInfoList == null) {
                    faceRegisterInfoList = new ArrayList<>();
                }
                FaceEntity faceEntity = new FaceEntity(userId, name, imgPath, faceFeature.getFeatureData());
                long faceId = FaceDatabase.getInstance(context).faceDao().insert(faceEntity);
                faceEntity.setFaceId(faceId);
                faceRegisterInfoList.add(faceEntity);
                return faceEntity;
            } else {
//                Log.e(TAG, "registerBgr24: extract face feature failed, code is " + code);
                throw new SdkException(code, "extract face feature failed");
            }
        } else {
//            Log.e(TAG, "registerBgr24: no face detected, code is " + code);
            throw new SdkException(ExceptionEnum.NO_FACE_ERROR, "no face detected");
        }
    }

    /**
     * 截取合适的头像并旋转，保存为注册头像
     *
     * @param originImageData 原始的BGR24数据
     * @param width           BGR24图像宽度
     * @param height          BGR24图像高度
     * @param orient          人脸角度
     * @param cropRect        裁剪的位置
     * @param imageFormat     图像格式
     * @return 头像的图像数据
     */
    public Bitmap getHeadImage(byte[] originImageData, int width, int height, int orient, Rect cropRect, ArcSoftImageFormat imageFormat) {
        byte[] headImageData = ArcSoftImageUtil.createImageData(cropRect.width(), cropRect.height(), imageFormat);
        int cropCode = ArcSoftImageUtil.cropImage(originImageData, headImageData, width, height, cropRect, imageFormat);
        if (cropCode != ArcSoftImageUtilError.CODE_SUCCESS) {
            throw new RuntimeException("crop image failed, code is " + cropCode);
        }

        //判断人脸旋转角度，若不为0度则旋转注册图
        byte[] rotateHeadImageData = null;
        int cropImageWidth;
        int cropImageHeight;
        // 90度或270度的情况，需要宽高互换
        if (orient == FaceEngine.ASF_OC_90 || orient == FaceEngine.ASF_OC_270) {
            cropImageWidth = cropRect.height();
            cropImageHeight = cropRect.width();
        } else {
            cropImageWidth = cropRect.width();
            cropImageHeight = cropRect.height();
        }
        ArcSoftRotateDegree rotateDegree = null;
        switch (orient) {
            case FaceEngine.ASF_OC_90:
                rotateDegree = ArcSoftRotateDegree.DEGREE_270;
                break;
            case FaceEngine.ASF_OC_180:
                rotateDegree = ArcSoftRotateDegree.DEGREE_180;
                break;
            case FaceEngine.ASF_OC_270:
                rotateDegree = ArcSoftRotateDegree.DEGREE_90;
                break;
            case FaceEngine.ASF_OC_0:
            default:
                rotateHeadImageData = headImageData;
                break;
        }
        // 非0度的情况，旋转图像
        if (rotateDegree != null) {
            rotateHeadImageData = new byte[headImageData.length];
            int rotateCode = ArcSoftImageUtil.rotateImage(headImageData, rotateHeadImageData, cropRect.width(), cropRect.height(), rotateDegree, imageFormat);
            if (rotateCode != ArcSoftImageUtilError.CODE_SUCCESS) {
                throw new RuntimeException("rotate image failed, code is : " + rotateCode + ", code description is : " + ErrorCodeUtil.imageUtilErrorCodeToFieldName(rotateCode));
            }
        }
        // 将创建一个Bitmap，并将图像数据存放到Bitmap中
        Bitmap headBmp = Bitmap.createBitmap(cropImageWidth, cropImageHeight, Bitmap.Config.RGB_565);
        int imageDataToBitmapCode = ArcSoftImageUtil.imageDataToBitmap(rotateHeadImageData, headBmp, imageFormat);
        if (imageDataToBitmapCode != ArcSoftImageUtilError.CODE_SUCCESS) {
            throw new RuntimeException("failed to transform image data to bitmap, code is : " + imageDataToBitmapCode
                    + ", code description is : " + ErrorCodeUtil.imageUtilErrorCodeToFieldName(imageDataToBitmapCode));
        }
        return headBmp;
    }

    /**
     * 在特征库中搜索
     *
     * @param faceFeature 传入特征数据
     * @param faceEngine 指定FaceEngine
     * @return 比对结果
     */
    public CompareResult searchFaceFeature(FaceFeature faceFeature, FaceEngine faceEngine) {
        if (faceEngine == null || faceFeature == null) {
            return null;
        }
        long start = System.currentTimeMillis();
        SearchResult searchResult;
        try {
            long searchStart = System.currentTimeMillis();
            searchResult = faceEngine.searchFaceFeature(faceFeature);
            Log.i(TAG, "searchCost:" + (System.currentTimeMillis() - searchStart) + "ms");
            if (searchResult != null) {
                FaceFeatureInfo faceFeatureInfo = searchResult.getFaceFeatureInfo();
                FaceEntity faceEntity = FaceDatabase.getInstance(context).faceDao().queryByFaceId(faceFeatureInfo.getSearchId());
                if (faceEntity != null) {
                    return new CompareResult(faceEntity, searchResult.getMaxSimilar(), ErrorInfo.MOK, System.currentTimeMillis() - start);
                }
            }
        } catch (IllegalArgumentException exception) {
            Log.i(TAG, "exception:" + exception.getMessage());
        }
        return null;
    }

    /**
     * 将图像中需要截取的Rect向外扩张一倍，若扩张一倍会溢出，则扩张到边界，若Rect已溢出，则收缩到边界
     *
     * @param width   图像宽度
     * @param height  图像高度
     * @param srcRect 原Rect
     * @return 调整后的Rect
     */
    public Rect getBestRect(int width, int height, Rect srcRect) {
        if (srcRect == null) {
            return null;
        }
        Rect rect = new Rect(srcRect);

        // 原rect边界已溢出宽高的情况
        int maxOverFlow = Math.max(-rect.left, Math.max(-rect.top, Math.max(rect.right - width, rect.bottom - height)));
        if (maxOverFlow >= 0) {
            rect.inset(maxOverFlow, maxOverFlow);
            return rect;
        }

        // 原rect边界未溢出宽高的情况
        int padding = rect.height() / 2;

        // 若以此padding扩张rect会溢出，取最大padding为四个边距的最小值
        if (!(rect.left - padding > 0 && rect.right + padding < width && rect.top - padding > 0 && rect.bottom + padding < height)) {
            padding = Math.min(Math.min(Math.min(rect.left, width - rect.right), height - rect.bottom), rect.top);
        }
        rect.inset(-padding, -padding);
        return rect;
    }

    public FaceEngine getFaceInfoEngine() {
        return faceInfoEngine;
    }

    public FaceEngine getFaceEngine(){
        return faceEngine;
    }

    public List<FaceEntity> getFaceRegisterInfoList() {
        return faceRegisterInfoList;
    }

    public FaceEngine getFrEngine(){
        return frEngine;
    }

    public void reInit(OnInitFinishedCallback onInitFinishedCallback) throws SdkException {
        if (context != null) {
            faceEngine = new FaceEngine();
            int engineCode = faceEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_ALL_OUT,
                    1, FaceEngine.ASF_FACE_RECOGNITION | FaceEngine.ASF_FACE_DETECT | FaceEngine.ASF_MASK_DETECT);
            if (engineCode == ErrorInfo.MOK) {
                initFaceList(context,null, onInitFinishedCallback, false);
            } else {
                faceEngine = null;
                Log.e(TAG, "init: failed! code = " + engineCode);
            }
        }
        if(context != null){
            frEngine = new FaceEngine();
            boolean enableFaceQualityDetect = ConfigUtil.isEnableImageQualityDetect(context);
            int frEngineMask = FaceEngine.ASF_FACE_RECOGNITION;
            if (enableFaceQualityDetect) {
                frEngineMask |= FaceEngine.ASF_IMAGEQUALITY;
            }
            int frCode = frEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_0_ONLY,
                    10, frEngineMask);
            if(frCode != ErrorInfo.MOK){
                throw new SdkException(frCode, "初始化失败");
            }
            initFaceList(context, frEngine, (faceCount, status) -> {
                System.out.println("faceCount:" + faceCount);
                System.out.println("status:" + status);
            }, true);
        }

        if(context != null){
            int processMask = FaceEngine.ASF_AGE | FaceEngine.ASF_GENDER | FaceEngine.ASF_LIVENESS | FaceEngine.ASF_MASK_DETECT;
            faceInfoEngine = new FaceEngine();
            int faceInfoCode = faceInfoEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_ALL_OUT,
                    5, FaceEngine.ASF_FACE_RECOGNITION | FaceEngine.ASF_FACE_DETECT | processMask);
            if(faceInfoCode != ErrorInfo.MOK){
                throw new SdkException(faceInfoCode, "初始化失败");
            }
        }
        if (faceRegisterInfoList != null && onInitFinishedCallback != null) {
            onInitFinishedCallback.onFinished(faceRegisterInfoList.size(), faceEngine != null);
        }
    }
}
