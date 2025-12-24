package com.mars.common;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    // 成功返回数据
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.data = data;
        r.msg = "success";
        return r;
    }

    // 成功但不带数据（方便仅返回消息的情况）
    public static <T> Result<T> success(String msg) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.msg = msg;
        return r;
    }

    // 失败（兼容 fail 命名）
    public static <T> Result<T> fail(String msg) {
        Result<T> r = new Result<>();
        r.code = 500;
        r.msg = msg;
        return r;
    }

    // 失败（兼容 error 命名，让之前的代码能跑通）
    public static <T> Result<T> error(String msg) {
        return fail(msg);
    }

    // 自定义错误码
    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}