-- 作业血缘关系测试数据
USE psx;

-- 清空现有数据
TRUNCATE TABLE job_lineage;

-- 插入测试数据
INSERT INTO job_lineage (job_group, job_name, dep_group, dep_name, is_valid) VALUES
-- 第一层依赖关系
('GROUP_A', 'DCM_ISP', 'GROUP_B', 'DCM_ODS', '有效'),
('GROUP_A', 'DCM_ISP', 'GROUP_C', 'DCM_DWD', '有效'),

-- 第二层依赖关系
('GROUP_B', 'DCM_ODS', 'GROUP_D', 'DCM_SRC', '有效'),
('GROUP_C', 'DCM_DWD', 'GROUP_D', 'DCM_SRC', '有效'),
('GROUP_C', 'DCM_DWD', 'GROUP_E', 'DCM_TMP', '有效'),

-- 第三层依赖关系
('GROUP_D', 'DCM_SRC', 'GROUP_F', 'DCM_RAW', '有效'),
('GROUP_E', 'DCM_TMP', 'GROUP_F', 'DCM_RAW', '有效'),

-- 其他作业关系
('GROUP_G', 'JOB_ANALYSIS', 'GROUP_A', 'DCM_ISP', '有效'),
('GROUP_G', 'JOB_ANALYSIS', 'GROUP_H', 'JOB_REPORT', '有效'),
('GROUP_H', 'JOB_REPORT', 'GROUP_I', 'JOB_EXPORT', '有效');

-- 查询插入的数据
SELECT * FROM job_lineage;

-- 统计信息
SELECT 
    COUNT(*) as total_records,
    COUNT(DISTINCT job_name) as unique_jobs,
    COUNT(DISTINCT dep_name) as unique_deps
FROM job_lineage
WHERE is_valid IN ('有效', '是');
