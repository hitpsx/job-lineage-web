package com.example.joblineage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {
    private String inputJob;          // 输入作业
    private int maxLevel;             // 最大层级
    private List<JobNode> upstream;   // 上游依赖
    private List<JobNode> downstream; // 下游影响
}