# QPO生产部署说明

> QPO 是产品名称；现有部署文件仍沿用 `erp.env` / `ERP_*` 技术前缀以保持兼容。不要在本次品牌改名里把这些配置名改成 `qpo`，除非后续单独做技术前缀迁移。

## 本地Docker验证
```powershell
cd D:\Code
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
