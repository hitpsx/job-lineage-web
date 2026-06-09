package com.example.joblineage.service;

import com.example.joblineage.entity.JobLineage;
import com.example.joblineage.model.JobNode;
import com.example.joblineage.model.QueryResult;
import com.example.joblineage.repository.JobLineageRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
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

    /**
     * 按目标应用过滤查询下游血缘路径
     * 从输入作业出发，只返回最终能到达目标应用所属作业组的完整路径
     *
     * @param startJob   起始作业名
     * @param targetApp  目标应用名（作业组名中 "_" 前的部分）
     * @param maxLevel   最大层级
     * @return 符合条件的完整路径列表，每条路径为从起点到终点的作业名列表
     */
    public List<List<String>> queryDownstreamByTargetApp(String startJob, String targetApp, int maxLevel) {
        if (!dataLoaded) {
            throw new RuntimeException("数据尚未加载，请检查数据库连接");
        }

        startJob = startJob.trim();
        targetApp = targetApp.trim();

        List<List<String>> validPaths = new ArrayList<>();

        // DFS 遍历所有下游路径，同时收集完整路径
        // path: 当前路径上的节点列表（包含起点）
        dfsFindPaths(startJob, targetApp, maxLevel, new ArrayList<>(Collections.singletonList(startJob)),
                new HashSet<>(), validPaths);

        return validPaths;
    }

    /**
     * 深度优先搜索，查找所有到达目标应用的路径
     */
    private void dfsFindPaths(String currentJob, String targetApp, int maxLevel,
                               List<String> currentPath, Set<String> visitedInPath,
                               List<List<String>> validPaths) {
        // 超过最大层级则停止
        if (currentPath.size() > maxLevel + 1) {
            return;
        }

        // 获取当前节点的下游子节点
        List<JobNode> children = downIndex.get(currentJob);

        // 当前节点是叶子节点（无下游）
        if (children == null || children.isEmpty()) {
            // 检查当前节点是否属于目标应用
            String appName = extractAppName(currentJob);
            if (targetApp.equals(appName)) {
                validPaths.add(new ArrayList<>(currentPath));
            }
            return;
        }

        boolean hasValidChild = false;

        for (JobNode child : children) {
            String childJob = child.getJob();

            // 避免环
            if (visitedInPath.contains(childJob)) {
                continue;
            }

            hasValidChild = true;

            // 继续DFS
            currentPath.add(childJob);
            visitedInPath.add(childJob);

            dfsFindPaths(childJob, targetApp, maxLevel, currentPath, visitedInPath, validPaths);

            currentPath.remove(currentPath.size() - 1);
            visitedInPath.remove(childJob);
        }

        // 如果所有子节点都被环跳过（当前分支实际为死胡同），检查当前节点本身
        if (!hasValidChild) {
            String appName = extractAppName(currentJob);
            if (targetApp.equals(appName)) {
                validPaths.add(new ArrayList<>(currentPath));
            }
        }
    }

    /**
     * 从作业全名中提取应用名（"_" 前的部分）
     */
    private String extractAppName(String jobName) {
        if (jobName == null || jobName.isEmpty()) {
            return "";
        }
        int underscoreIndex = jobName.indexOf('_');
        if (underscoreIndex > 0) {
            return jobName.substring(0, underscoreIndex);
        }
        return jobName;
    }

    /**
     * 导出血缘查询结果为 Excel
     *
     * @param result 查询结果
     * @return Excel 文件字节数组
     */
    public byte[] exportLineageToExcel(QueryResult result) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 上游依赖 Sheet
            Sheet upstreamSheet = workbook.createSheet("上游依赖");
            createLineageSheet(upstreamSheet, result.getUpstream(), "上游");

            // 下游影响 Sheet
            Sheet downstreamSheet = workbook.createSheet("下游影响");
            createLineageSheet(downstreamSheet, result.getDownstream(), "下游");

            // 写入输出流
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            logger.error("导出 Excel 失败", e);
            throw new RuntimeException("导出 Excel 失败: " + e.getMessage());
        }
    }

    /**
     * 导出目标应用过滤结果为 Excel
     *
     * @param startJob   起始作业
     * @param targetApp  目标应用
     * @param paths      路径列表
     * @return Excel 文件字节数组
     */
    public byte[] exportTargetAppToExcel(String startJob, String targetApp, List<List<String>> paths) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("目标应用过滤链路");

            // 标题行
            Row titleRow = sheet.createRow(0);
            CellStyle titleStyle = createTitleStyle(workbook);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("目标应用过滤血缘链路");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 4));

            // 查询信息行
            Row infoRow = sheet.createRow(1);
            infoRow.createCell(0).setCellValue("起始作业:");
            infoRow.createCell(1).setCellValue(startJob);
            infoRow.createCell(2).setCellValue("目标应用:");
            infoRow.createCell(3).setCellValue(targetApp);
            infoRow.createCell(4).setCellValue("路径总数:" + paths.size());

            // 空行
            sheet.createRow(2);

            // 表头
            Row headerRow = sheet.createRow(3);
            CellStyle headerStyle = createHeaderStyle(workbook);
            String[] headers = {"路径编号", "层级", "作业组_作业名", "应用名", "节点类型"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 数据行
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle targetStyle = createTargetStyle(workbook);
            int rowNum = 4;

            for (int pathIdx = 0; pathIdx < paths.size(); pathIdx++) {
                List<String> path = paths.get(pathIdx);
                for (int nodeIdx = 0; nodeIdx < path.size(); nodeIdx++) {
                    Row row = sheet.createRow(rowNum++);
                    String job = path.get(nodeIdx);
                    String appName = extractAppName(job);
                    String nodeType;
                    if (nodeIdx == 0) {
                        nodeType = "起点";
                    } else if (nodeIdx == path.size() - 1) {
                        nodeType = "目标";
                    } else {
                        nodeType = "中间";
                    }

                    boolean isTarget = nodeType.equals("目标");

                    row.createCell(0).setCellValue(pathIdx + 1);
                    row.createCell(1).setCellValue(nodeIdx);
                    row.createCell(2).setCellValue(job);
                    row.createCell(3).setCellValue(appName);
                    Cell typeCell = row.createCell(4);
                    typeCell.setCellValue(nodeType);

                    // 应用样式
                    for (int c = 0; c < 5; c++) {
                        row.getCell(c).setCellStyle(isTarget ? targetStyle : dataStyle);
                    }
                }

                // 路径间空行
                if (pathIdx < paths.size() - 1) {
                    rowNum++;
                }
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            logger.error("导出目标应用过滤 Excel 失败", e);
            throw new RuntimeException("导出 Excel 失败: " + e.getMessage());
        }
    }

    private void createLineageSheet(Sheet sheet, List<JobNode> nodes, String direction) {
        // 标题行
        Row titleRow = sheet.createRow(0);
        CellStyle titleStyle = createTitleStyle(sheet.getWorkbook());
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(direction + "依赖链路");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

        // 表头
        Row headerRow = sheet.createRow(1);
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        String[] headers = {"层级", "作业组_作业名", "直接父节点", "方向"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 数据行
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());
        int rowNum = 2;
        for (JobNode node : nodes) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(node.getLevel());
            row.createCell(1).setCellValue(node.getJob());
            row.createCell(2).setCellValue(node.getParent());
            row.createCell(3).setCellValue(direction);
            for (int c = 0; c < 4; c++) {
                row.getCell(c).setCellStyle(dataStyle);
            }
        }

        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTargetStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * 获取测试数据示例（用于验证多级血缘）
     */
    public List<Map<String, Object>> getSampleData() {
        List<Map<String, Object>> samples = new ArrayList<>();

        // 场景1: 深层链示例 - chain_0 的完整10级链
        Map<String, Object> chainSample = new HashMap<>();
        chainSample.put("scene", "深层链(10级)");
        chainSample.put("description", "chain_0_job_l0 → chain_0_job_l1 → ... → chain_0_job_l10");
        chainSample.put("testQuery", "chain_0_job_l0");
        chainSample.put("expectedLevel", 10);
        samples.add(chainSample);

        // 场景2: 扇出示例
        Map<String, Object> fanoutSample = new HashMap<>();
        fanoutSample.put("scene", "扇出");
        fanoutSample.put("description", "fanout_root_root_0 依赖 500个叶子节点");
        fanoutSample.put("testQuery", "fanout_root_root_0");
        fanoutSample.put("expectedDownstream", "约500个叶子节点");
        samples.add(fanoutSample);

        // 场景3: 扇入示例
        Map<String, Object> faninSample = new HashMap<>();
        faninSample.put("scene", "扇入");
        faninSample.put("description", "500个叶子节点都依赖 fanin_root_root_0");
        faninSample.put("testQuery", "fanin_root_root_0");
        faninSample.put("expectedUpstream", "约500个叶子节点");
        samples.add(faninSample);

        // 场景4: 分层流水线示例
        Map<String, Object> layerSample = new HashMap<>();
        layerSample.put("scene", "分层流水线");
        layerSample.put("description", "layer_9_node_0 依赖 layer_8 的随机节点");
        layerSample.put("testQuery", "layer_9_node_0");
        layerSample.put("expectedLevel", 9);
        samples.add(layerSample);

        // 场景5: 菱形依赖示例
        Map<String, Object> diamondSample = new HashMap<>();
        diamondSample.put("scene", "菱形依赖");
        diamondSample.put("description", "diamond_0_job_A → diamond_0_job_B → diamond_0_job_D");
        diamondSample.put("description2", "diamond_0_job_A → diamond_0_job_C → diamond_0_job_D");
        diamondSample.put("testQuery", "diamond_0_job_A");
        samples.add(diamondSample);

        // 场景6: 随机DAG示例
        Map<String, Object> randomSample = new HashMap<>();
        randomSample.put("scene", "随机DAG");
        randomSample.put("description", "random_g0_job_0 与其他随机节点有依赖关系");
        randomSample.put("testQuery", "random_g0_job_0");
        samples.add(randomSample);

        return samples;
    }
}
