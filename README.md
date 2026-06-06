# 作业血缘关系查询系统

基于Spring Boot的作业血缘关系查询系统，从MySQL数据库读取数据，提供可视化界面进行作业依赖关系查询。

## 功能特性

- 从MySQL数据库自动加载作业血缘数据
- 作业血缘关系查询（上游依赖和下游影响）
- 可视化Web界面
- 支持多层级查询
- 显示每个作业的直接依赖关系
- 数据刷新功能

## 技术栈

- Spring Boot 2.7.18
- Spring Data JPA
- MySQL 8.0
- Lombok
- 纯前端HTML/CSS/JavaScript（无需额外前端框架）

## 数据库配置

### 数据库信息
- 数据库：MySQL 8.0
- 数据库名：psx
- 用户名：root
- 密码：root

### 数据表结构

系统会自动创建 `job_lineage` 表，结构如下：

```sql
CREATE TABLE job_lineage (
    id INT AUTO_INCREMENT PRIMARY KEY,
    job_group VARCHAR(255) NOT NULL COMMENT '作业组',
    job_name VARCHAR(255) NOT NULL COMMENT '作业名',
    dep_group VARCHAR(255) COMMENT '依赖作业组',
    dep_name VARCHAR(255) COMMENT '依赖作业名',
    is_valid VARCHAR(10) DEFAULT '有效' COMMENT '是否有效',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_job_name (job_name),
    INDEX idx_dep_name (dep_name),
    INDEX idx_job_group (job_group)
);
```

### 数据格式说明

| 字段名 | 说明 | 示例 |
|--------|------|------|
| job_group | 作业所属组 | GROUP_A |
| job_name | 作业名称 | DCM_ISP |
| dep_group | 依赖的作业组 | GROUP_B |
| dep_name | 依赖的作业名 | DCM_ODS |
| is_valid | 数据有效性标识 | 有效/是 |

## 快速开始

### 1. 准备数据库

确保MySQL服务已启动，并创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS psx;
```

### 2. 导入数据

向 `job_lineage` 表中插入作业血缘关系数据：

```sql
USE psx;

INSERT INTO job_lineage (job_group, job_name, dep_group, dep_name, is_valid) VALUES
('GROUP_A', 'JOB_1', 'GROUP_B', 'JOB_2', '有效'),
('GROUP_A', 'JOB_1', 'GROUP_C', 'JOB_3', '有效'),
('GROUP_B', 'JOB_2', 'GROUP_D', 'JOB_4', '有效');
```

### 3. 编译运行

```bash
# 编译项目
mvn clean package

# 运行项目
java -jar target/job-lineage-web-1.0.0.jar

# 或使用Maven直接运行
mvn spring-boot:run
```

### 4. 访问系统

打开浏览器访问：http://localhost:8080

## 使用说明

### 刷新数据

1. 系统启动时会自动从数据库加载数据
2. 如需刷新数据，点击"刷新数据库数据"按钮
3. 系统会重新从数据库读取最新数据并构建索引

### 查询作业血缘关系

1. 在"作业名称"框中输入作业名称（如：DCM_ISP）
2. 设置查询层级（默认4层）
3. 点击"查询血缘关系"按钮
4. 查看上游依赖和下游影响结果

## API接口

### 刷新数据库数据

```
POST /api/refresh
返回: {"success": true, "message": "...", "stats": {...}}
```

### 查询作业血缘关系

```
GET /api/query
参数: jobName (String), maxLevel (int)
返回: {"success": true, "data": {...}}
```

### 获取数据统计

```
GET /api/stats
返回: {"success": true, "stats": {...}}
```

### 健康检查

```
GET /api/health
返回: {"status": "UP", "dataLoaded": true/false}
```

## 性能优化

- 使用索引加速查询（下游索引和上游索引）
- 使用BFS算法进行层级查询
- 使用并发安全的Map存储索引数据
- 数据库字段建立索引加速查询
- 应用启动时自动加载数据

## 项目结构

```
src/main/java/com/example/joblineage/
├── JobLineageApplication.java          # 主应用入口
├── controller/
│   └── JobLineageController.java       # REST API控制器
├── service/
│   └── JobLineageService.java          # 业务逻辑服务
├── repository/
│   └── JobLineageRepository.java       # 数据访问层
├── entity/
│   └── JobLineage.java                 # 数据库实体
└── model/
    ├── JobNode.java                    # 作业节点模型
    └── QueryResult.java                # 查询结果模型
```

## 扩展建议

1. 添加数据导入功能（从Excel导入到数据库）
2. 添加用户认证和权限管理
3. 添加查询历史记录
4. 添加可视化图表展示（如D3.js）
5. 添加导出功能（导出查询结果为Excel）
6. 添加数据版本管理
7. 添加定时自动刷新数据功能

## 许可证

MIT License
