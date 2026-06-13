# APS Docker部署说明

## 启动APS

```powershell
cd D:\Code
docker compose --env-file aps.env -f docker-compose.aps.yml up -d --build
```

默认本地访问地址：

- APS前端：`http://127.0.0.1:8095`
- 前端反向代理健康检查：`http://127.0.0.1:8095/api/health`

## 环境变量文件

从模板复制正式运行配置：

```powershell
Copy-Item aps.env.example aps.env
notepad aps.env
```

生产环境必须配置：

- `APS_POSTGRES_PASSWORD`：PostgreSQL强随机密码。
- `APS_JWT_SECRET`：至少32位的强随机JWT密钥。
- `APS_ERP_BASE_URL`：ERP后端内网地址。同一台Docker Desktop主机可用 `http://host.docker.internal:8080`，独立服务器则填写ERP内网地址。
- `APS_ERP_AUTH_TOKEN`：必须和ERP的 `ERP_APS_SOURCE_TOKEN` 一致。
- `APS_CORS_ALLOWED_ORIGINS`：正式APS访问地址，例如 `https://aps.example.com`。

## 正式环境初始化新数据库

正式部署时，在 `aps.env` 使用新的PostgreSQL卷名：

```env
APS_POSTGRES_VOLUME_NAME=aps_postgres_data_prod
```

这样会初始化一个全新的APS数据库。后端第一次启动时，Flyway会自动创建表结构。

当前本机迁移环境为了保留旧数据，`aps.env` 可以继续使用：

```env
APS_POSTGRES_VOLUME_NAME=aps_postgres_data
```

这表示继续使用之前继承下来的APS数据库卷。

## 端口暴露策略

生产Compose只把APS前端映射到宿主机：

```text
127.0.0.1:8095 -> aps-frontend:80
```

APS后端和PostgreSQL只在Docker内部网络访问。外层HTTPS/Nginx应代理到：

```text
http://127.0.0.1:8095
```

## 备份

手动备份：

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\backup-aps.ps1
```

安装每日定时备份：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-aps-backup-task.ps1 -DailyAt "02:30"
```

备份文件保存到：

```text
D:\Code\backups\aps\<timestamp>\
```

## 恢复

恢复会覆盖目标数据库，必须显式添加 `-Force`：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\restore-aps.ps1 -BackupDir "D:\Code\backups\aps\20260612_020000" -Force
```

不影响业务库的恢复演练：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\restore-drill-aps.ps1 -BackupDir "D:\Code\backups\aps\20260612_020000"
```

## 停止APS

```powershell
docker compose --env-file aps.env -f docker-compose.aps.yml down
```
