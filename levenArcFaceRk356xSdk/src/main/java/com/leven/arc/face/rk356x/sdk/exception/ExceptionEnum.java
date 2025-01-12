package com.leven.arc.face.rk356x.sdk.exception;

public class ExceptionEnum {
    //默认失败
    public static int DEFAULT_ERROR = -1;
    //注册时带口罩
    public static int REGISTER_MASK_ERROR = -100;
    //裁剪图片错误
    public static int CROP_RECT_ERROR = -101;
    //未发现人脸
    public static int NO_FACE_ERROR = -102;
    //注册失败
    public static int REGISTER_FAILED = -103;
    //已注册
    public static int REGISTER_USER_EXISTS = -104;
    //存在相同人脸
    public static int SIMILAR_FACE_EXISTS = -105;
    //人脸未注册
    public static int FACE_NOT_REGISTER = -106;
    //未初始化
    public static int NOT_INIT_ENGINE = -107;
    //缺少必要的权限
    public static int LACK_PERMISSIONS = -10001;
    //文件不存在
    public static int FILE_NOT_EXISTS = -10002;
    //url地址有误
    public static int URL_ERROR = -10003;
}
