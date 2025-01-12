package com.leven.arc.face.rk356x.sdk;

import android.os.Environment;

import java.io.File;

public class ConfigUtils {
    //注册保存地址
    public static String REGISTER_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "levenArcFaceRk356x";
    //人脸注册的图片id和用户名中间间隔符
    public static final String IMAGE_SPLIT_STR = "###";
}
