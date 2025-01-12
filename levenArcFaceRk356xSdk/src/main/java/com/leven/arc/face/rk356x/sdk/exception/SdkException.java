package com.leven.arc.face.rk356x.sdk.exception;

public class SdkException extends Exception{
    private int code;

    public SdkException(){
        super();
    }

    public SdkException(String message){
        super(message);
        this.code = ExceptionEnum.DEFAULT_ERROR;
    }
    public SdkException(int code, String message){
        super(message);
        this.code = code;
    }

    /*获取code*/
    public int getCode(){
        return this.code;
    }
}
