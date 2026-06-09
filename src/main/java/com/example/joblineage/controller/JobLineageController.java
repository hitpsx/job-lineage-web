package com.example.joblineage.controller;

import com.example.joblineage.model.QueryResult;
import com.example.joblineage.service.DataGenerator;
import com.example.joblineage.service.JobLineageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class JobLineageController {
    
    private static final Logger logger = LoggerFactory.getLogger(JobLineageController.class);
    
    @Autowired
    private JobLineageService jobLineageService;

    @Autowired
    private DataGenerator dataGenerator;
    
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

    /**
     * 生成40万条测试数据
     */
    @PostMapping("/generate-test-data")
    public ResponseEntity<Map<String, Object>> generateTestData() {
        Map<String, Object> response = new HashMap<>();
        try {
            logger.info("开始生成测试数据...");
            dataGenerator.generateAll();
            
            response.put("success", true);
            response.put("message", "测试数据生成成功");
            response.put("stats", dataGenerator.getStats());
            
            // 刷新索引
            jobLineageService.loadDataFromDatabase();
            
            logger.info("测试数据生成完毕，索引已刷新");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("测试数据生成失败", e);
            response.put("success", false);
            response.put("message", "生成失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 按目标应用过滤查询下游血缘路径
     * 从输入作业出发，只返回最终能到达目标应用所属作业组的完整路径
     */
    @GetMapping("/query-by-target-app")
    public ResponseEntity<Map<String, Object>> queryByTargetApp(
            @RequestParam String jobName,
            @RequestParam String targetApp,
            @RequestParam(defaultValue = "10") int maxLevel) {

        Map<String, Object> response = new HashMap<>();

        try {
            if (!jobLineageService.isDataLoaded()) {
                response.put("success", false);
                response.put("message", "数据尚未加载，请检查数据库连接");
                return ResponseEntity.badRequest().body(response);
            }

            List<List<String>> paths = jobLineageService.queryDownstreamByTargetApp(jobName, targetApp, maxLevel);
            response.put("success", true);
            response.put("startJob", jobName);
            response.put("targetApp", targetApp);
            response.put("pathCount", paths.size());
            response.put("paths", paths);

            logger.info("按目标应用查询血缘路径: startJob={}, targetApp={}, 找到 {} 条路径", jobName, targetApp, paths.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("按目标应用查询失败", e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 导出血缘查询结果为 Excel
     */
    @GetMapping("/export/lineage")
    public ResponseEntity<byte[]> exportLineage(
            @RequestParam String jobName,
            @RequestParam(defaultValue = "4") int maxLevel) {
        try {
            if (!jobLineageService.isDataLoaded()) {
                return ResponseEntity.badRequest().body(null);
            }

            QueryResult result = jobLineageService.query(jobName, maxLevel);
            byte[] excelBytes = jobLineageService.exportLineageToExcel(result);

            String filename = URLEncoder.encode("血缘关系_" + jobName + ".xlsx", StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelBytes);

        } catch (Exception e) {
            logger.error("导出血缘 Excel 失败", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 导出目标应用过滤结果为 Excel
     */
    @GetMapping("/export/target-app")
    public ResponseEntity<byte[]> exportTargetApp(
            @RequestParam String jobName,
            @RequestParam String targetApp,
            @RequestParam(defaultValue = "10") int maxLevel) {
        try {
            if (!jobLineageService.isDataLoaded()) {
                return ResponseEntity.badRequest().body(null);
            }

            List<List<String>> paths = jobLineageService.queryDownstreamByTargetApp(jobName, targetApp, maxLevel);
            byte[] excelBytes = jobLineageService.exportTargetAppToExcel(jobName, targetApp, paths);

            String filename = URLEncoder.encode("目标应用过滤_" + jobName + "_到_" + targetApp + ".xlsx", StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelBytes);

        } catch (Exception e) {
            logger.error("导出目标应用过滤 Excel 失败", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 获取测试数据示例（用于验证多级血缘）
     */
    @GetMapping("/sample-data")
    public ResponseEntity<Map<String, Object>> getSampleData() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("samples", jobLineageService.getSampleData());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取示例数据失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
