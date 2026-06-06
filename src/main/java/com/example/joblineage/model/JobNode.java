package com.example.joblineage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobNode {
    private int level;       // 层级
    private String job;      // 作业名
    private String parent;   // 直接依赖的父作业
}