package com.example.reading.dto;

import lombok.Getter;
import lombok.Setter;

/** 管理员审核操作请求体 */
@Getter
@Setter
public class ReviewActionDTO {
    private String action;       // approve / reject
    private String rejectReason; // 驳回原因（reject 时必填）
}
