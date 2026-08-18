# QPO生产部署说明
## Workspace Boundary

This document only covers the QPO quotation system. APS may appear only as historical compatibility context; QPO deployment work must not recreate or maintain APS project files.

> QPO 是产品名称；现有部署文件仍沿用 `erp.env` / `ERP_*` 技术前缀以保持兼容。不要在本次品牌改名里把这些配置名改成 `qpo`，除非后续单独做技术前缀迁移。

## 本地Docker验证
```powershell
cd D:\QPO-Operations-Management-System
docker compose --env-file erp.env up -d --build
```

开发环境和测试服务器部署默认使用原生 Compose 命令。不要直接手工选择 Flyway 迁移编号；新增迁移文件用：

```powershell
.\mig.ps1 your_migration_name
```

如果 Compose 因 Flyway 校验失败而中断，再运行 `scripts/compose-up.ps1` 做更早一步的诊断检查。该脚本会先检查 active 迁移目录，再启动 PostgreSQL 读取 `flyway_schema_history`，确认没有复用压缩历史版本 `V3/V4` 或“数据库版本高于仓库版本”后，才会 build/start 后端和前端。

详细规则见 `docs/ops/flyway-migration-policy.md`。

默认本地访问地址：
- 前端：`http://localhost:8088`
- 后端健康检查：`http://localhost:8080/api/health`

## 离线Docker镜像包部署
QPO 支持把前端、后端和基础依赖镜像打成一个离线 `tar` 包，用于内网隔离环境部署。这个方案不依赖部署服务器联网；部署服务器只需要 Docker、Docker Compose、`erp.env` 和离线镜像包。

### 镜像标签规则
- `master` 分支打包时使用镜像标签 `master`，代表当前主干最新版。
- 给 `master` 上的正式发布 commit 打 Git tag 时，使用 tag 名称作为镜像标签，便于 QA / 正式环境回滚。
- 手动打包可通过参数指定镜像标签；未指定时脚本会按“当前 Git tag > master/main 分支名 > 短 commit sha”的顺序自动推断。

### Forgejo Actions打包
仓库内置 `.forgejo/workflows/package-images.yml`。Linux + Docker runner 可在以下场景自动生成离线包附件：

- push 到 `master`。
- push `v*`、`V*`、`release-*`、`qpo-*` tag。
- 在 Actions 页面手动触发 workflow，并可指定 `image_tag`。

Actions runner 需要满足：

- 能执行 Docker CLI，并能访问 Docker daemon。
- 当前 Forgejo runner 会在 `node:20-bookworm` job 容器中执行步骤；workflow 会自动安装 Docker CLI，但 runner 主机仍必须把 Docker daemon/socket 暴露给 job 容器。
- 能拉取构建基础镜像和依赖镜像，例如 `maven:3.9-eclipse-temurin-17`、`eclipse-temurin:17-jre`、`node:22-alpine`、`nginx:1.27-alpine`、`postgres:15`。
- 能下载或已缓存 `actions/checkout`、`actions/upload-artifact`。如果公司 Forgejo 无法访问外部 Actions，需要平台管理员配置镜像源或本地 Action 缓存。

workflow 产物目录包含：

- `qpo-offline-images-<tag>.tar`：Docker 离线镜像包。
- `docker-compose.yml`：离线启动使用的 Compose 文件。
- `erp.env.example`：环境变量模板，不包含真实密钥。
- `qpo-image.env`：本次包的镜像标签摘要。
- `qpo-offline-package-manifest.json`：镜像清单。
- `scripts/load-offline-images.ps1` / `scripts/load-offline-images.sh`：离线加载和启动脚本。

### 本机打包兜底
如果 Forgejo Actions 暂时走不通，可在本机或任意可联网的 Docker 构建机直接打包，生成的产物与 Actions 产物一致。

Windows PowerShell：

```powershell
cd D:\QPO-Operations-Management-System
powershell -ExecutionPolicy Bypass -File .\scripts\package-offline-images.ps1 -ImageTag master
```

Linux：

```bash
cd /path/to/QPO-Operations-Management-System
bash scripts/package-offline-images.sh --image-tag master
```

如需把可选 MinIO 镜像也放入离线包：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-offline-images.ps1 -ImageTag master -IncludeMinio
```

生成目录默认在 `dist/qpo-offline-package-<tag>/`，该目录已加入 `.gitignore`，不要提交离线镜像包。

### 内网服务器加载和启动
把 `dist/qpo-offline-package-<tag>/` 整个目录复制到内网服务器。首次部署时，在该目录下根据 `erp.env.example` 准备真实 `erp.env`，填写数据库密码、JWT 密钥、MES/WMS/U9 等真实配置。不要把真实 `erp.env` 回传或提交到仓库。

Linux 部署服务器：

```bash
cd /opt/qpo/qpo-offline-package-master
bash scripts/load-offline-images.sh
```

Windows 部署服务器：

```powershell
cd D:\qpo\qpo-offline-package-master
powershell -ExecutionPolicy Bypass -File .\scripts\load-offline-images.ps1
```

脚本会先执行 `docker load -i qpo-offline-images-<tag>.tar`，再设置 `QPO_IMAGE_TAG=<tag>` 并执行：

```bash
docker compose --env-file erp.env up -d --no-build
```

如果只想加载镜像、不启动服务：

```bash
bash scripts/load-offline-images.sh --no-start
```

如需同时启动 Compose 的 MinIO profile：

```bash
bash scripts/load-offline-images.sh --include-minio
```

### 回滚
正式环境建议使用 Git tag 产物部署。需要回滚时，把目标版本的离线包上传到服务器，重新执行加载脚本即可。Compose 会按该包内 `qpo-image.env` 的 `QPO_IMAGE_TAG` 启动对应版本镜像；数据库迁移仍由后端 Flyway 管理，涉及数据库结构降级时需先做备份和人工评估。

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
.\scripts\compose-up.ps1
```

启动后检查健康状态：

```powershell
docker compose --env-file erp.env ps
curl http://localhost:8080/api/health
```
