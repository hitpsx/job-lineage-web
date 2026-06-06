package com.example.joblineage.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "job_lineage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobLineage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "job_group", nullable = false)
    private String jobGroup;      // 作业组
    
    @Column(name = "job_name", nullable = false)
    private String jobName;       // 作业名
    
    @Column(name = "dep_group")
    private String depGroup;      // 依赖作业组
    
    @Column(name = "dep_name")
    private String depName;       // 依赖作业名
    
    @Column(name = "is_valid")
    private String isValid;       // 是否有效
    
    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;      // 创建时间
}
