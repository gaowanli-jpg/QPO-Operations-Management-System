# ERP运维说明

## 容量报告

生成一次容量报告：

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\capacity-report.ps1
```

输出文件：
- `capacity-reports/<timestamp>/capacity-report.txt`
- `capacity-reports/<timestamp>/capacity-report.json`

报告包含：
- 宿主机磁盘使用情况。
- 数据库大小。
- PostgreSQL最大数据表。
- 本地备份和归档目录大小。
- PostgreSQL和上传文件相关Docker卷使用情况。

## 月度归档

手动执行一次月度归档：

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\archive-monthly.ps1
```

默认行为：
- 把180天以前的 `audit_log` 导出为CSV，然后从在线数据库删除。
- 把730天以前的旧订单候选记录只导出为CSV。
- 不会自动删除历史订单业务数据。

输出文件：
- `archives/<timestamp>/db/audit_log_before_180_days.csv`
- `archives/<timestamp>/db/old_order_candidates_before_730_days.csv`
- `archives/<timestamp>/meta/archive.json`

试运行，不删除数据：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\archive-monthly.ps1 -DryRun
```

## 安装月度归档任务

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\install-archive-task.ps1 -MonthlyAt "03:00" -DayOfMonth 1
```

这会创建Windows计划任务 `QuoteSystemMonthlyArchive`。

禁用任务：

```powershell
Unregister-ScheduledTask -TaskName QuoteSystemMonthlyArchive -Confirm:$false
```

查看任务：

```powershell
Get-ScheduledTask -TaskName QuoteSystemMonthlyArchive
schtasks /Query /TN QuoteSystemMonthlyArchive
```
