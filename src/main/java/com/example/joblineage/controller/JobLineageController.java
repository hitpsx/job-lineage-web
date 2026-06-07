package com.example.joblineage.controller;

import com.example.joblineage.model.QueryResult;
import com.example.joblineage.service.JobLineageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class JobLineageController {
    
    private static final Logger logger = LoggerFactory.getLogger(JobLineageController.class);
    
    @Autowired
    private JobLineageService jobLineageService;
    
    /**
     * 刷新数据库数据
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String result = jobLineageService.loadDataFromDatabase();
            response.put("success", true);
            response.put("message", result);
            response.put("stats", jobLineageService.getDataStats());
            
            logger.info("数据库数据刷新成功");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("数据库数据刷新失败", e);
            response.put("success", false);
            response.put("message", "数据刷新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 查询作业血缘关系
     */
    @GetMapping("/query")
    public ResponseEntity<Map<String, Object>> queryJobLineage(
            @RequestParam String jobName,
            @RequestParam(defaultValue = "4") int maxLevel) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!jobLineageService.isDataLoaded()) {
                response.put("success", false);
                response.put("message", "数据尚未加载，请检查数据库连接");
                return ResponseEntity.badRequest().body(response);
            }
            
            QueryResult result = jobLineageService.query(jobName, maxLevel);
            response.put("success", true);
            response.put("data", result);
            
            logger.info("查询作业血缘关系: {}, maxLevel: {}", jobName, maxLevel);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("查询失败", e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取数据统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("stats", jobLineageService.getDataStats());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("dataLoaded", jobLineageService.isDataLoaded());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有作业列表
     */
    @GetMapping("/all-jobs")
    public ResponseEntity<Map<String, Object>> getAllJobs() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("jobs", jobLineageService.getAllJobs());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取作业列表失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 搜索作业（支持模糊和精准搜索）
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchJobs(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "fuzzy") String mode) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("jobs", jobLineageService.searchJobs(keyword, "exact".equals(mode)));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("搜索失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
