# QPO服务器版本更新说明

本文只说明已经完成首次部署后的版本更新。首次部署、`erp.env` 初次填写、服务器目录创建见 `QPO部署说明.md`。

## 更新原则

QPO 离线更新只替换前端、后端镜像和应用容器，不删除 Docker 数据卷。

当前 Compose 固定项目名为 `quote-system`，数据库、上传文件、备份文件使用这些命名卷：

```text
quote-system_postgres_data
quote-system_uploads_data
quote-system_backups_data
```

只要不执行删除卷的命令，版本更新不会清空数据库已有数据，也不会清空系统配置、用户、角色、业务单据、上传图纸等数据。后端启动时可能会执行 Flyway 数据库迁移，正常迁移用于升级表结构和补充新字段，不应删除业务数据；正式更新前仍建议先做一次数据库备份，方便异常时回滚。

## 每次更新的标准流程

### 1. 在本机打新版本离线包

建议每次版本使用清晰标签，例如 `20260819`、`v1.0.1` 或 `release-20260819`。

```powershell
cd D:\QPO-Operations-Management-System
powershell -ExecutionPolicy Bypass -File .\scripts\package-offline-images.ps1 -ImageTag <新版本标签>
```

生成目录：

```text
D:\QPO-Operations-Management-System\dist\qpo-offline-package-<新版本标签>
```

### 2. 上传新离线包到服务器

```powershell
scp -r D:\QPO-Operations-Management-System\dist\qpo-offline-package-<新版本标签> user@192.168.166.15:/opt/qpo/
```

如果服务器地址或用户名不同，把 `user@192.168.166.15` 换成实际值。

### 3. 登录服务器并进入新版本目录

```bash
ssh user@192.168.166.15
cd /opt/qpo/qpo-offline-package-<新版本标签>
ls
```

目录里应能看到：

```text
qpo-offline-images-<新版本标签>.tar
docker-compose.yml
erp.env.example
qpo-image.env
scripts
```

### 4. 复制上一版本的 erp.env

不要重新手填环境变量。直接从当前正在使用的旧版本目录复制 `erp.env`。

如果你知道旧版本目录名：

```bash
cp /opt/qpo/qpo-offline-package-<旧版本标签>/erp.env ./erp.env
```

如果不确定旧版本目录名，先查看：

```bash
ls -dt /opt/qpo/qpo-offline-package-*
```

排第一的通常是最近上传或最近修改的目录。确认旧版本目录后再复制。

复制后检查关键项：

```bash
grep '^POSTGRES_PASSWORD=' erp.env
grep '^ERP_JWT_SECRET=' erp.env
grep '^ERP_CORS_ALLOWED_ORIGINS=' erp.env
```

如果服务器 IP 或访问端口没有变，这些配置不用改。

### 5. 加载镜像并启动新版本

推荐直接用包内脚本：

```bash
bash scripts/load-offline-images.sh
```

脚本会自动：

1. `docker load -i qpo-offline-images-<新版本标签>.tar`
2. 从 `qpo-image.env` 读取 `QPO_IMAGE_TAG`
3. 执行 `docker compose --env-file erp.env up -d --no-build`

这一步会重建前端、后端容器，但不会删除数据库卷。

### 6. 检查服务状态

```bash
docker compose --env-file erp.env ps
curl -i http://127.0.0.1:8088/api/health
```

也可以测试登录接口：

```bash
curl -i -X POST http://127.0.0.1:8088/api/auth/login \
  -H 'Origin: http://192.168.166.15:8088' \
  -H 'Content-Type: application/json' \
  -d '{"username":"<当前可登录用户名>","password":"<当前密码>"}'
```

如果返回 `HTTP/1.1 200`，浏览器刷新后即可使用新版本。

## 手工重启时的命令

正常更新优先使用：

```bash
bash scripts/load-offline-images.sh
```

如果只是改了 `erp.env`，想手工重启容器，必须先设置包内镜像标签：

```bash
cd /opt/qpo/qpo-offline-package-<版本标签>
export QPO_IMAGE_TAG="$(grep '^QPO_IMAGE_TAG=' qpo-image.env | cut -d= -f2-)"
docker compose --env-file erp.env up -d --no-build --force-recreate backend frontend
```

不要省略 `--no-build`。离线包目录里没有 `quote-backend`、`quote-frontend` 源码，省略后可能触发构建或拉取 `qpo-backend:latest`。

## 数据库备份建议

更新操作本身不会删库，但正式环境建议更新前备份一次。

进入当前版本目录执行：

```bash
cd /opt/qpo/qpo-offline-package-<当前版本标签>
mkdir -p backups/postgres
docker compose --env-file erp.env exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > backups/postgres/qpo-before-update-$(date +%Y%m%d%H%M%S).sql
```

备份文件会落在当前离线包目录的 `backups/postgres/` 下。这个目录是普通服务器文件，不是容器卷；需要长期留存时可以再拷到单独备份位置。

## 回滚到旧版本

如果新版本异常，回到旧版本目录重新启动即可：

```bash
cd /opt/qpo/qpo-offline-package-<旧版本标签>
bash scripts/load-offline-images.sh
```

回滚应用镜像不会自动回滚数据库结构。如果新版本已经执行了 Flyway 迁移，回滚前要确认旧后端是否兼容新表结构。普通前端显示问题或后端代码问题通常可以直接回滚；涉及数据库结构变更的大版本，应先保留备份并评估。

## 禁止命令

版本更新时不要执行这些命令：

```bash
docker compose down -v
docker volume rm quote-system_postgres_data
docker volume rm quote-system_uploads_data
docker volume rm quote-system_backups_data
docker system prune --volumes
```

这些命令会删除数据库或上传文件所在的数据卷，属于清库/清文件操作，不是版本更新。

## 常见问题

### Invalid CORS request

浏览器访问地址没有写进 `ERP_CORS_ALLOWED_ORIGINS`。

例如前端地址是 `http://192.168.166.15:8088`，`erp.env` 需要包含：

```env
ERP_CORS_ALLOWED_ORIGINS=http://192.168.166.15:8088,http://localhost:8088,http://127.0.0.1:8088
```

修改后重启后端：

```bash
export QPO_IMAGE_TAG="$(grep '^QPO_IMAGE_TAG=' qpo-image.env | cut -d= -f2-)"
docker compose --env-file erp.env up -d --no-build --force-recreate backend
```

### No such image: qpo-backend:latest

没有设置 `QPO_IMAGE_TAG`，Compose 使用了默认 `latest`。

修复：

```bash
export QPO_IMAGE_TAG="$(grep '^QPO_IMAGE_TAG=' qpo-image.env | cut -d= -f2-)"
docker images --format '{{.Repository}}:{{.Tag}}' | grep '^qpo-'
docker compose --env-file erp.env up -d --no-build --force-recreate backend frontend
```

### 需要编辑 erp.env

服务器上建议用 `nano`：

```bash
nano erp.env
```

保存退出：

- `Ctrl + O`，回车保存
- `Ctrl + X`，退出
