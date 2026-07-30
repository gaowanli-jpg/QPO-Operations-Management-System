# QPO生产部署说明
## Workspace Boundary

This document only covers the QPO quotation system. APS may appear only as historical compatibility context; QPO deployment work must not recreate or maintain APS project files.

> QPO 是产品名称；现有部署文件仍沿用 `erp.env` / `ERP_*` 技术前缀以保持兼容。不要在本次品牌改名里把这些配置名改成 `qpo`，除非后续单独做技术前缀迁移。

## 本地Docker验证
```powershell
cd D:\QPO-Operations-Management-System
docker compose --env-file erp.env up -d --build
```

默认本地访问地址：
- 前端：`http://localhost:8088`
- 后端健康检查：`http://localhost:8080/api/health`

## 环境变量文件
在部署机器上把 `erp.env.example` 复制成 `erp.env`，然后修改生产配置：

```powershell
Copy-Item erp.env.example erp.env
notepad erp.env
```

重要配置：
- `POSTGRES_PASSWORD`：启动前必须填写，使用长随机密码。
- `ERP_JWT_SECRET`：启动前必须填写，至少32位强随机值；后端会拒绝使用旧默认值或过短密钥启动。
- `ERP_CORS_ALLOWED_ORIGINS`：设置为真实前端访问地址，例如 `https://qpo.example.com`。
- `POSTGRES_BIND`：通常保持 `127.0.0.1`，除非使用独立数据库服务器或可信内网。
- `BACKEND_BIND`：通常保持 `127.0.0.1`，前端容器通过Docker内部网络访问后端。
- `FRONTEND_BIND`：同机外层Nginx代理HTTPS时用 `127.0.0.1`；只有直接暴露前端容器时才用 `0.0.0.0`。
- `FRONTEND_PORT`：外层Nginx后面建议用 `8088`；没有外层反向代理时才考虑 `80`。
- `DB_POOL_MAX_SIZE`：后端数据库连接池最大连接数。单后端容器默认 `15` 即可，不要在未评估PostgreSQL连接上限和服务器容量前调太高。
- `DB_POOL_MIN_IDLE`：保留的空闲连接数。普通内网使用默认 `3` 即可。
- `ERP_TASK_CORE_POOL_SIZE` / `ERP_TASK_MAX_POOL_SIZE`：报价刷新、大文件导出等后台任务线程池。默认 `2` / `4` 即可，调大前要评估CPU和数据库能力。
- `ERP_FILE_STORAGE_TYPE`：本地开发默认 LOCAL，现有上传目录继续使用 `ERP_UPLOAD_DIR`。
- `ERP_MINIO_*`：仅在初始化、测试或连接 MinIO 文件存储策略时使用；真实密钥只写 `erp.env`，不要写入代码、示例文件或文档。

连接池经验公式：

```text
backend container count * DB_POOL_MAX_SIZE + other database clients < PostgreSQL max_connections
```

Windows PowerShell生成密钥：

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

Linux生成密钥：

```bash
openssl rand -base64 48
```

Compose文件已经配置容器健康检查、日志轮转、保守的CPU/内存限制，以及应用容器的 `no-new-privileges`。

## 文件存储与 MinIO
本地开发默认 LOCAL，不需要安装或启动 MinIO。默认配置：

```env
ERP_FILE_STORAGE_TYPE=LOCAL
```

这个模式下，图纸仍写入后端容器的 `/data/uploads/`，并由 Compose 的 `uploads_data` 卷持久化；系统日志、错误日志、数据库备份不放入 MinIO。

需要验证 MinIO 存储逻辑时，先在 `erp.env` 填写本机 MinIO 管理员密码：

```env
MINIO_ROOT_USER=qpo_minio_admin
MINIO_ROOT_PASSWORD=change-to-a-local-strong-password
MINIO_BIND=127.0.0.1
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
```

如 Docker Hub 拉取受限，可把 `MINIO_IMAGE` 改成公司或国内镜像源中的 MinIO 镜像地址。只启动 MinIO 容器：

```powershell
docker compose --env-file erp.env --profile minio up -d minio
```

连同 QPO 一起验证：

```powershell
docker compose --env-file erp.env --profile minio up -d --build
```

MinIO 控制台默认地址为 `http://localhost:9001`。启动后进入 QPO 的“系统配置 > 文件存储”，新增 MINIO 配置，填入 endpoint、bucket、access key、secret，先点击测试连接，再激活；激活只影响之后上传的新图纸，历史图纸仍按原存储位置读取。

测试/生产如果已有公司统一 MinIO，不需要启动 QPO 自带的 MinIO profile。由管理员在系统配置页填入公司统一 MinIO 的 endpoint、bucket 和账号，并确认 QPO 后端、MES 所在网络均可访问该地址。

## HTTPS反向代理
可参考 `deploy/nginx/quote-backend.https.example.conf` 作为服务器级Nginx配置示例。

生产前需要：
1. 把 `qpo.example.com` 替换成真实域名。
2. 把证书路径替换成真实证书和私钥。
3. Docker Compose前端保持暴露在 `127.0.0.1:8088`，或由服务器防火墙限制访问。
4. 设置 `erp.env`：

```env
ERP_CORS_ALLOWED_ORIGINS=https://qpo.example.com
POSTGRES_BIND=127.0.0.1
BACKEND_BIND=127.0.0.1
FRONTEND_BIND=127.0.0.1
FRONTEND_PORT=8088
```

修改 `erp.env` 后重建容器：

```powershell
docker compose --env-file erp.env up -d --build
```

启动后检查健康状态：

```powershell
docker compose --env-file erp.env ps
curl http://localhost:8080/api/health
```
