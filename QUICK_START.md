# 快速启动指南

## 1. 数据库准备

### 确保MySQL服务运行
```bash
# Windows
net start mysql

# Linux
systemctl start mysql
```

### 执行测试数据脚本
```bash
mysql -uroot -proot < init_data.sql
```

或者手动执行：
```sql
-- 连接数据库
mysql -uroot -proot

-- 执行SQL脚本
source init_data.sql;
```

## 2. 编译和运行项目

### 方式一：使用Maven
```bash
# 清理并编译
mvn clean package

# 运行项目
java -jar target/job-lineage-web-1.0.0.jar
```

### 方式二：直接运行
```bash
mvn spring-boot:run
```

### 方式三：在IDE中运行
- 使用IDEA或Eclipse打开项目
- 运行 `JobLineageApplication.java` 主类

## 3. 访问系统

打开浏览器访问：http://localhost:8080

## 4. 使用步骤

1. 系统启动后会自动从数据库加载数据
2. 在"作业名称"输入框输入：`GROUP_A_DCM_ISP` 或 `DCM_ISP`
3. 点击"查询血缘关系"按钮
4. 查看上游依赖和下游影响结果

## 5. 测试数据说明

测试数据包含以下作业关系：
- DCM_ISP 依赖 DCM_ODS 和 DCM_DWD
- DCM_ODS 依赖 DCM_SRC
- DCM_DWD 依赖 DCM_SRC 和 DCM_TMP
- DCM_SRC 依赖 DCM_RAW
- JOB_ANALYSIS 依赖 DCM_ISP 和 JOB_REPORT

## 6. 常见问题

### Q: 启动报错找不到数据库？
A: 确保MySQL服务已启动，数据库psx已创建

### Q: 页面显示"数据尚未加载"？
A: 点击"刷新数据库数据"按钮，或检查数据库连接

### Q: 查询结果为空？
A: 确保数据库中有对应的数据，检查作业名称是否正确

## 7. 停止服务

按 `Ctrl + C` 停止运行的服务
