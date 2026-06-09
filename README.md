# 作业血缘关系查询系统

基于 Spring Boot 的作业血缘关系查询系统，从 MySQL 数据库读取数据，提供可视化界面进行作业依赖关系查询，支持按目标应用过滤和 Excel 导出。

## 功能特性

- 从 MySQL 数据库自动加载作业血缘数据
- 作业血缘关系查询（上游依赖和下游影响）
- **按目标应用过滤**：从输入作业出发，只展示最终能到达目标应用的完整链路
- **Excel 导出**：支持将血缘查询结果和目标应用过滤结果导出为 Excel
- 可视化 Web 界面（抽屉式多级链路展示）
- 支持模糊/精准搜索作业
- 支持多层级查询
- 数据刷新功能

## 技术栈

- Spring Boot 2.7.18
- Spring Data JPA
- MySQL 8.0
- Apache POI（Excel 导出）
- Lombok
- 纯前端 HTML/CSS/JavaScript（无需额外前端框架）

## 数据库配置

### 数据库要求

- 数据库：MySQL 8.0+

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

| 字段名    | 说明           | 示例      |
|-----------|----------------|-----------|
| job_group | 作业所属组     | GROUP_A |
| job_name  | 作业名称       | DCM_ISP   |
| dep_group | 依赖的作业组   | GROUP_B   |
| dep_name  | 依赖的作业名   | DCM_ODS   |
| is_valid  | 数据有效性标识 | 有效/是   |

### 命名规则

作业组名称格式：`应用名_作业组名`，以下划线 `_` 分隔，第一个字段为应用名。

例如：`C_Y` 表示应用 `C` 下的作业组 `Y`。

### 安全配置（重要）

**请勿在配置文件中明文存储数据库密码！**

推荐通过以下方式配置数据库连接：

#### 方式一：环境变量（推荐）

在启动前设置环境变量：

```bash
# Linux/Mac
export DB_URL=jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai
export DB_USERNAME=your_username
export DB_PASSWORD=your_password

# Windows (PowerShell)
$env:DB_URL="jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="your_username"
$env:DB_PASSWORD="your_password"
```

#### 方式二：外部配置文件

创建 `application-local.yml` 文件（添加到 .gitignore）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

#### 方式三：命令行参数

```bash
java -jar target/job-lineage-web-1.0.0.jar \
  --spring.datasource.url=jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai \
  --spring.datasource.username=your_username \
  --spring.datasource.password=your_password
```

### 安全建议

1. **生产环境**：使用数据库专用账号，最小权限原则
2. **密码管理**：使用密钥管理服务（如 Vault）或环境变量
3. **网络安全**：限制数据库端口只允许内网访问
4. **加密传输**：生产环境启用 SSL 连接（`useSSL=true`）
5. **定期轮换**：定期更换数据库密码

## 快速开始

### 1. 准备数据库

确保 MySQL 服务已启动，并创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS your_database;
```

### 2. 配置数据库连接

**推荐使用环境变量配置数据库连接（避免明文密码）：**

```bash
# Linux/Mac
export DB_URL=jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai
export DB_USERNAME=your_username
export DB_PASSWORD=your_password

# Windows (PowerShell)
$env:DB_URL="jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="your_username"
$env:DB_PASSWORD="your_password"
```

### 3. 导入数据

向 `job_lineage` 表中插入作业血缘关系数据：

```sql
USE your_database;

INSERT INTO job_lineage (job_group, job_name, dep_group, dep_name, is_valid) VALUES
('GROUP_A', 'JOB_1', 'GROUP_B', 'JOB_2', '有效'),
('GROUP_A', 'JOB_1', 'GROUP_C', 'JOB_3', '有效'),
('GROUP_B', 'JOB_2', 'GROUP_D', 'JOB_4', '有效');
```

### 4. 编译运行

```bash
# 编译项目
mvn clean package

# 运行项目
java -jar target/job-lineage-web-1.0.0.jar

# 或使用 Maven 直接运行
mvn spring-boot:run
```

**注意**：首次启动时，如果没有配置环境变量，系统会使用默认配置。生产环境请务必通过环境变量配置数据库密码！

### 5. 访问系统

打开浏览器访问：http://localhost:8080

## 使用说明

### 刷新数据

1. 系统启动时会自动从数据库加载数据
2. 如需刷新数据，点击"刷新数据库数据"按钮
3. 系统会重新从数据库读取最新数据并构建索引

### 查询作业血缘关系

1. 在"作业名称"框中输入作业名称（如：`GROUP_A_JOB_1`）
2. 设置查询层级（默认 4 层）
3. 点击"查询血缘关系"按钮
4. 查看上游依赖和下游影响结果
5. 点击"导出 Excel"按钮可将结果导出为 Excel 文件

### 按目标应用过滤查询

1. 在"起始作业名"框中输入起始作业（如：`A_X`）
2. 在"目标应用名"框中输入目标应用（如：`C`，即作业组名中 `_` 前的部分）
3. 设置最大层级（默认 10 层）
4. 点击"查询目标应用链路"按钮
5. 系统展示所有最终能到达目标应用的完整链路
6. 点击"导出 Excel"按钮可将结果导出为 Excel 文件

**过滤规则**：
- 路径中间节点可以是任意应用
- 末端（最深层级）作业组的应用名必须等于目标应用名
- 不到达目标应用的分支一律过滤

### 搜索作业

1. 在"作业名称"框中输入关键词
2. 选择搜索模式（模糊搜索/精准搜索）
3. 点击"搜索作业"按钮或按回车键
4. 从搜索结果中点击选择作业

## API 接口

### 刷新数据库数据

```
POST /api/refresh
返回: {"success": true, "message": "...", "stats": {...}}
```

### 查询作业血缘关系

```
GET /api/query?jobName={jobName}&maxLevel={maxLevel}
返回: {"success": true, "data": {...}}
```

### 按目标应用过滤查询

```
GET /api/query-by-target-app?jobName={jobName}&targetApp={targetApp}&maxLevel={maxLevel}
返回: {"success": true, "startJob": "...", "targetApp": "...", "pathCount": N, "paths": [[...], ...]}
```

### 搜索作业

```
GET /api/search?keyword={keyword}&mode={fuzzy|exact}
返回: {"success": true, "jobs": [...]}
```

### 获取所有作业列表

```
GET /api/all-jobs
返回: {"success": true, "jobs": [...]}
```

### 导出血缘关系 Excel

```
GET /api/export/lineage?jobName={jobName}&maxLevel={maxLevel}
返回: Excel 文件下载
```

### 导出目标应用过滤 Excel

```
GET /api/export/target-app?jobName={jobName}&targetApp={targetApp}&maxLevel={maxLevel}
返回: Excel 文件下载
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
- 使用 BFS 算法进行层级查询
- 使用 DFS 算法进行目标应用路径过滤
- 使用并发安全的 Map 存储索引数据
- 数据库字段建立索引加速查询
- 应用启动时自动加载数据

## 项目结构

```
src/main/java/com/example/joblineage/
├── JobLineageApplication.java          # 主应用入口
├── controller/
│   └── JobLineageController.java       # REST API 控制器
├── service/
│   ├── JobLineageService.java          # 业务逻辑服务
│   └── DataGenerator.java              # 测试数据生成器
├── repository/
│   └── JobLineageRepository.java       # 数据访问层
├── entity/
│   └── JobLineage.java                 # 数据库实体
└── model/
    ├── JobNode.java                    # 作业节点模型
    └── QueryResult.java                # 查询结果模型

src/main/resources/
├── application.yml                     # 应用配置
├── static/
│   └── index.html                      # 前端页面
└── templates/                          # 模板文件
```

## 扩展建议

1. 添加数据导入功能（从 Excel 导入到数据库）
2. 添加用户认证和权限管理
3. 添加查询历史记录
4. 添加可视化图表展示（如 D3.js）
5. 添加数据版本管理
6. 添加定时自动刷新数据功能

## 许可证

MIT License
