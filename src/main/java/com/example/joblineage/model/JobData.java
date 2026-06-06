package com.example.joblineage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobData {
    private String jobGroup;      // 作业组
    private String jobName;       // 作业名
    private String depGroup;      // 依赖作业组
    private String depName;       // 依赖作业名
    private String isValid;       // 是否有效
    
    // 组合键
    private String aB;            // 作业组_作业名
    private String cD;            // 依赖作业组_依赖作业名
    
    // 下游查询的下一个键
    private String nextKeyDown;
    
    // 上游查询的下一个键
    private String nextKeyUp;
}