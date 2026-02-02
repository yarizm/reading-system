package com.example.reading.common;

import lombok.Data;

@Data
public class Result<T> {
    private String code; // 200:成功, 500:系统错误, 401:未登录
    private String msg;  // 提示信息
    private T data;      // 返回的数据

    // 成功时的构造方法
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode("200");
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    // 成功但不需要返回数据
    public static Result success() {
        return success(null);
    }

    // 失败时的构造方法
    public static Result error(String code, String msg) {
        Result result = new Result();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }
}