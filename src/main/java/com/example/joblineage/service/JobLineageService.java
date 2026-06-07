package com.example.joblineage.service;

import com.example.joblineage.entity.JobLineage;
import com.example.joblineage.model.JobNode;
import com.example.joblineage.model.QueryResult;
import com.example.joblineage.repository.JobLineageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobLineageService {
    
    private static final Logger logger = LoggerFactory.getLogger(JobLineageService.class);
    
    @Autowired
    private JobLineageRepository jobLineageRepository;
    
    // 数据索引
    private Map<String, List<JobNode>> downIndex = new ConcurrentHashMap<>();
    private Map<String, List<JobNode>> upIndex = new ConcurrentHashMap<>();
    
    // 数据是否已加载
    private boolean dataLoaded = false;
    
    /**
     * 应用启动时自动加载数据
     */
    @PostConstruct
    public void init() {
        loadDataFromDatabase();
    }
    
    /**
     * 从数据库加载数据
     */
    public String loadDataFromDatabase() {
        logger.info("开始从数据库加载作业血缘数据...");
        
        try {
            // 清空索引
            downIndex.clear();
            upIndex.clear();
            
            // 查询所有有效数据
            List<String> validValues = Arrays.asList("有效", "是", "true", "1");
            List<JobLineage> jobLineages = jobLineageRepository.findByIsValidIn(validValues);
            
            if (jobLineages.isEmpty()) {
                logger.warn("数据库中没有有效数据");
                dataLoaded = false;
                return "数据库中没有有效数据";
            }
            
            // 构建索引
            for (JobLineage lineage : jobLineages) {
                String aB = lineage.getJobGroup() + "_" + lineage.getJobName();
                String cD = lineage.getDepGroup() + "_" + lineage.getDepName();
                
                // 构建下游索引：通过依赖名查找谁依赖了它（下游影响）
                // 即：输入 dep_name，返回依赖它的 job_name
                // 同时支持完整key（group_name）和纯作业名（name）查询
                String keyDown = cD;
                downIndex.computeIfAbsent(keyDown, k -> new ArrayList<>())
                        .add(new JobNode(1, aB, cD));
                // 添加纯作业名索引
                if (lineage.getDepName() != null && !lineage.getDepName().isEmpty()) {
                    downIndex.computeIfAbsent(lineage.getDepName(), k -> new ArrayList<>())
                            .add(new JobNode(1, aB, cD));
                }
                
                // 构建上游索引：通过作业名查找它依赖了谁（上游依赖）
                // 即：输入 job_name，返回它依赖的 dep_name
                // 同时支持完整key（group_name）和纯作业名（name）查询
                String keyUp = aB;
                upIndex.computeIfAbsent(keyUp, k -> new ArrayList<>())
                        .add(new JobNode(1, cD, aB));
                // 添加纯作业名索引
                upIndex.computeIfAbsent(lineage.getJobName(), k -> new ArrayList<>())
                        .add(new JobNode(1, cD, aB));
            }
            
            dataLoaded = true;
            String message = String.format("成功加载 %d 条数据，下游索引 %d 条，上游索引 %d 条",
                    jobLineages.size(), downIndex.size(), upIndex.size());
            logger.info(message);
            
            return message;
            
        } catch (Exception e) {
            logger.error("从数据库加载数据失败", e);
            dataLoaded = false;
            return "加载失败: " + e.getMessage();
        }
    }
    
    /**
     * 查询下游依赖（BFS）
     */
    public List<JobNode> queryDownstream(String jobName, int maxLevel) {
        List<JobNode> result = new ArrayList<>();
        Queue<JobNode> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(new JobNode(0, jobName, jobName));
        visited.add(jobName);
        
        while (!queue.isEmpty()) {
            JobNode current = queue.poll();
            
            if (current.getLevel() >= maxLevel) {
                continue;
            }
            
            List<JobNode> children = downIndex.get(current.getJob());
            if (children != null) {
                for (JobNode child : children) {
                    if (!visited.contains(child.getJob())) {
                        visited.add(child.getJob());
                        result.add(new JobNode(current.getLevel() + 1, child.getJob(), current.getJob()));
                        queue.offer(new JobNode(current.getLevel() + 1, child.getJob(), child.getJob()));
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 查询上游依赖（BFS）
     */
    public List<JobNode> queryUpstream(String jobName, int maxLevel) {
        List<JobNode> result = new ArrayList<>();
        Queue<JobNode> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(new JobNode(0, jobName, jobName));
        visited.add(jobName);
        
        while (!queue.isEmpty()) {
            JobNode current = queue.poll();
            
            if (current.getLevel() >= maxLevel) {
                continue;
            }
            
            List<JobNode> parents = upIndex.get(current.getJob());
            if (parents != null) {
                for (JobNode parent : parents) {
                    if (!visited.contains(parent.getJob())) {
                        visited.add(parent.getJob());
                        result.add(new JobNode(current.getLevel() + 1, parent.getJob(), current.getJob()));
                        queue.offer(new JobNode(current.getLevel() + 1, parent.getParent(), parent.getParent()));
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 查询作业血缘关系
     */
    public QueryResult query(String jobName, int maxLevel) {
        if (!dataLoaded) {
            throw new RuntimeException("数据尚未加载，请检查数据库连接");
        }
        
        jobName = jobName.trim();
        List<JobNode> upstream = queryUpstream(jobName, maxLevel);
        List<JobNode> downstream = queryDownstream(jobName, maxLevel);
        
        return new QueryResult(jobName, maxLevel, upstream, downstream);
    }
    
    /**
     * 检查数据是否已加载
     */
    public boolean isDataLoaded() {
        return dataLoaded;
    }
    
    /**
     * 获取数据统计信息
     */
    public Map<String, Object> getDataStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            List<String> validValues = Arrays.asList("有效", "是", "true", "1");
            long totalJobs = jobLineageRepository.countByIsValidIn(validValues);
            stats.put("totalJobs", totalJobs);
        } catch (Exception e) {
            stats.put("totalJobs", 0);
        }

        stats.put("downIndexSize", downIndex.size());
        stats.put("upIndexSize", upIndex.size());
        stats.put("dataLoaded", dataLoaded);
        return stats;
    }

    /**
     * 获取所有作业列表
     */
    public List<Map<String, String>> getAllJobs() {
        List<Map<String, String>> jobs = new ArrayList<>();
        Set<String> uniqueJobs = new HashSet<>();

        // 从upIndex中提取所有唯一的作业
        for (Map.Entry<String, List<JobNode>> entry : upIndex.entrySet()) {
            for (JobNode node : entry.getValue()) {
                String job = node.getJob();
                if (job != null && !uniqueJobs.contains(job)) {
                    uniqueJobs.add(job);
                    Map<String, String> jobMap = new HashMap<>();
                    jobMap.put("job", job);
                    // 从作业名中提取作业组（格式：group_name）
                    int underscoreIndex = job.indexOf('_');
                    if (underscoreIndex > 0) {
                        jobMap.put("group", job.substring(0, underscoreIndex));
                        jobMap.put("name", job.substring(underscoreIndex + 1));
                    } else {
                        jobMap.put("group", "");
                        jobMap.put("name", job);
                    }
                    jobs.add(jobMap);
                }
            }
        }

        // 按作业名排序
        jobs.sort((a, b) -> a.get("job").compareTo(b.get("job")));
        return jobs;
    }

    /**
     * 搜索作业（支持模糊和精准搜索）
     */
    public List<Map<String, String>> searchJobs(String keyword, boolean exactMatch) {
        List<Map<String, String>> allJobs = getAllJobs();
        List<Map<String, String>> results = new ArrayList<>();

        keyword = keyword.toLowerCase();

        for (Map<String, String> job : allJobs) {
            String jobName = job.get("name");
            String jobGroup = job.get("group");
            boolean match = false;

            if (exactMatch) {
                // 精准匹配：作业名或作业组完全相等
                match = jobName.equalsIgnoreCase(keyword) ||
                        jobGroup.equalsIgnoreCase(keyword) ||
                        job.get("job").equalsIgnoreCase(keyword);
            } else {
                // 模糊匹配：包含关键词
                match = jobName.toLowerCase().contains(keyword) ||
                        jobGroup.toLowerCase().contains(keyword) ||
                        job.get("job").toLowerCase().contains(keyword);
            }

            if (match) {
                results.add(job);
            }
        }

        return results;
    }
}
