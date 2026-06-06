package com.example.joblineage.repository;

import com.example.joblineage.entity.JobLineage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobLineageRepository extends JpaRepository<JobLineage, Long> {
    
    // 根据作业名查询
    List<JobLineage> findByJobName(String jobName);
    
    // 根据依赖作业名查询
    List<JobLineage> findByDepName(String depName);
    
    // 查询所有有效数据
    List<JobLineage> findByIsValidIn(List<String> validValues);
    
    // 统计有效数据数量
    long countByIsValidIn(List<String> validValues);
}
