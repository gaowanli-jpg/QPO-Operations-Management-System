# APS备份与恢复说明

本文档说明 APS 系统的数据库备份、定时备份、恢复演练和正式恢复方法。APS 当前没有独立上传文件目录，业务数据、用户数据、库存数据、派工数据和同步记录全部保存在 PostgreSQL 数据库中，因此备份核心对象是 APS PostgreSQL 数据库。

适用项目路径：

- 项目根目录：`D:\Code`
- APS Compose 文件：`D:\Code\docker-compose.aps.yml`
- APS 环境变量文件：`D:\Code\aps.env`
- APS 备份脚本目录：`D:\Code\scripts`
- APS 默认备份目录：`D:\Code\backups\aps`

## 1. 备份恢复目标

APS 备份恢复需要满足以下目标：

- 数据库误操作后可以恢复到指定备份时间点。
- 服务器迁移时可以用备份重建 APS 数据库。
- 上线前可以用恢复演练证明备份文件可用。
- 定时备份保留最近一段时间的可恢复数据。
- 备份文件不能泄露生产业务数据和账号数据。

APS 当前备份方案属于“整库逻辑备份”，即使用 PostgreSQL `pg_dump` 导出 APS 数据库 SQL，再通过 `psql` 导入恢复。

## 2. 相关脚本

APS 已提供以下脚本：

| 脚本 | 作用 | 典型使用场景 |
| --- | --- | --- |
| `D:\Code\scripts\backup-aps.ps1` | 立即执行一次 APS 数据库备份 | 手动备份、上线前备份、恢复前备份 |
| `D:\Code\scripts\run-aps-backup-task.ps1` | 给 Windows 定时任务调用的备份入口 | 每天自动备份 |
| `D:\Code\scripts\install-aps-backup-task.ps1` | 创建 Windows 每日备份计划任务 | 部署完成后安装定时备份 |
| `D:\Code\scripts\restore-drill-aps.ps1` | 用临时 PostgreSQL 容器做恢复演练 | 验证备份文件是否能恢复 |
| `D:\Code\scripts\restore-aps.ps1` | 把指定备份恢复到当前 APS 数据库 | 灾难恢复、服务器迁移、误操作恢复 |

其中 `restore-aps.ps1` 会覆盖当前 APS 数据库，必须谨慎使用。

## 3. 备份内容

每次备份会生成一个以时间命名的目录：

```text
D:\Code\backups\aps\
  20260613_023000\
    db\
      aps.sql
    meta\
      backup.json
```

### 3.1 db/aps.sql

`db\aps.sql` 是 APS PostgreSQL 数据库的逻辑备份文件，包含：

- Flyway 迁移记录。
- APS 管理员用户表。
- ERP 同步批次记录。
- APS 工单主数据。
- 工序明细。
- 零件库存。
- 零件锁定记录。
- 零件生产需求。
- 材料库存。
- 材料锁定记录。
- 材料缺口记录。
- 部门。
- 机台。
- 机台工艺能力。
- 部门派工记录。
- 部门任务记录。

### 3.2 meta/backup.json

`meta\backup.json` 是备份元数据，包含：

- 备份时间。
- 项目根目录。
- Compose 文件路径。
- 环境变量文件路径。
- 数据库名。
- 数据库用户。
- 数据库备份文件相对路径。
- 当前 Git 提交短哈希，如果本地仓库能读取到。

该文件用于恢复脚本识别数据库名称和数据库用户。

### 3.3 不包含的内容

APS 备份不包含以下内容：

- `aps.env`。
- `aps.env.example`。
- Docker 镜像。
- Docker volume 原始二进制文件。
- 服务器系统账号。
- HTTPS 证书。
- 外层 Nginx 配置。

`aps.env` 内包含数据库密码、JWT 密钥和 ERP 对接 Token，不能放进普通备份包一起流转。交接时应通过安全渠道单独交付生产环境变量。

## 4. 手动备份

### 4.1 前置条件

执行手动备份前确认：

- Docker Desktop 或服务器 Docker 正常运行。
- APS Compose 服务已经启动。
- `D:\Code\aps.env` 存在。
- `aps.env` 内的数据库名、用户名、密码与当前容器一致。
- 当前用户有执行 Docker 命令和写入 `D:\Code\backups\aps` 的权限。

### 4.2 执行命令

在 PowerShell 执行：

```powershell
cd D:\Code
.\scripts\backup-aps.ps1 -ProjectRoot D:\Code
```

执行成功后会输出类似：

```text
APS backup completed: D:\Code\backups\aps\20260613_023000
```

### 4.3 自动清理旧备份

如果需要备份后自动清理旧备份，可以加 `-PruneOld` 和 `-KeepDays`：

```powershell
cd D:\Code
.\scripts\backup-aps.ps1 -ProjectRoot D:\Code -PruneOld -KeepDays 14
```

含义：

- `-PruneOld`：启用旧备份清理。
- `-KeepDays 14`：保留最近 14 天的备份目录。

清理对象只匹配 `yyyyMMdd_HHmmss` 格式的备份目录，不会删除 `logs` 等非备份目录。

## 5. Windows 定时备份

### 5.1 安装每日备份任务

部署完成后，可以安装 Windows 定时任务：

```powershell
cd D:\Code
.\scripts\install-aps-backup-task.ps1 -ProjectRoot D:\Code -DailyAt 02:30 -KeepDays 14
```

默认会创建：

- 计划任务名称：`ApsSystemDailyBackup`
- 执行时间：每天 `02:30`
- 保留天数：14 天
- 日志文件：`D:\Code\backups\aps\logs\scheduled-aps-backup.log`

### 5.2 查看计划任务

```powershell
Get-ScheduledTask -TaskName ApsSystemDailyBackup
```

### 5.3 手动触发计划任务

```powershell
Start-ScheduledTask -TaskName ApsSystemDailyBackup
```

触发后查看日志：

```powershell
Get-Content D:\Code\backups\aps\logs\scheduled-aps-backup.log -Tail 100
```

### 5.4 修改备份时间

如果需要修改每日备份时间，重新执行安装脚本即可覆盖：

```powershell
cd D:\Code
.\scripts\install-aps-backup-task.ps1 -ProjectRoot D:\Code -DailyAt 03:00 -KeepDays 30
```

## 6. 备份有效性检查

备份完成后至少检查以下内容：

1. 备份目录存在。
2. `db\aps.sql` 存在且文件大小大于 0。
3. `meta\backup.json` 存在。
4. 备份日志没有错误。
5. 定期执行恢复演练。

可以用以下命令快速检查最新备份：

```powershell
$latest = Get-ChildItem D:\Code\backups\aps -Directory |
  Where-Object { $_.Name -match '^\d{8}_\d{6}$' } |
  Sort-Object Name -Descending |
  Select-Object -First 1

$latest.FullName
Get-Item "$($latest.FullName)\db\aps.sql"
Get-Content "$($latest.FullName)\meta\backup.json"
```

## 7. 恢复演练

恢复演练用于验证备份是否真实可用。它不会覆盖当前 APS 正式数据库，而是启动一个临时 PostgreSQL 容器，把备份导入临时库后检查核心表。

### 7.1 执行恢复演练

```powershell
cd D:\Code
.\scripts\restore-drill-aps.ps1 -BackupDir D:\Code\backups\aps\20260613_023000
```

其中 `20260613_023000` 替换成实际备份目录。

### 7.2 恢复演练检查的表

脚本会检查以下核心表的记录数：

- `aps_user`
- `aps_work_item`
- `aps_department`
- `aps_process`
- `aps_material_inventory`

这些表覆盖账号、工单、部门、工艺、材料库存等关键数据域。恢复演练成功说明备份 SQL 可以被 PostgreSQL 正常导入，并且基础数据表可以查询。

### 7.3 恢复演练输出

正常情况下会看到各表记录数，并输出：

```text
APS restore drill completed with temporary container: aps-restore-drill-...
```

脚本结束后会自动删除临时容器。

### 7.4 恢复演练频率

建议执行频率：

- 上线前必须执行一次。
- 每月固定执行一次。
- 每次调整数据库结构、备份脚本、恢复脚本后执行一次。
- 每次服务器迁移前后执行一次。

只做备份但从不恢复演练，不能认为备份方案可用。

## 8. 正式恢复

正式恢复会覆盖当前 APS 数据库。执行前必须确认目标环境可以被覆盖。

### 8.1 正式恢复适用场景

正式恢复适用于：

- 误删业务数据，需要恢复到某个时间点。
- 数据库损坏。
- 服务器迁移。
- 新服务器需要使用旧服务器备份初始化。
- 上线前用指定备份重建环境。

正式恢复不适合用于普通查询、对比数据或测试导入。测试导入应使用恢复演练脚本。

### 8.2 恢复前确认清单

执行正式恢复前逐项确认：

1. 已通知所有用户停止使用 APS。
2. 已确认要恢复的备份目录。
3. 已确认恢复目标是 APS 环境，不是 ERP 环境。
4. 已确认 `D:\Code\aps.env` 指向正确数据库。
5. 已对当前 APS 再做一次即时备份。
6. 已执行过恢复演练，确认备份 SQL 可导入。
7. 已记录当前容器状态和恢复操作人。

恢复前建议先执行：

```powershell
cd D:\Code
.\scripts\backup-aps.ps1 -ProjectRoot D:\Code
```

这样即使恢复错了，也可以再回到恢复前状态。

### 8.3 执行正式恢复

```powershell
cd D:\Code
.\scripts\restore-aps.ps1 -BackupDir D:\Code\backups\aps\20260613_023000 -ProjectRoot D:\Code -Force
```

其中：

- `-BackupDir`：指定要恢复的备份目录。
- `-ProjectRoot`：项目根目录。
- `-Force`：确认执行破坏性恢复。

如果不加 `-Force`，脚本会拒绝执行。

### 8.4 正式恢复过程

脚本会执行以下操作：

1. 校验备份目录存在。
2. 校验 `db\aps.sql` 存在。
3. 读取 `aps.env` 和 `meta\backup.json` 中的数据库名称、数据库用户。
4. 停止 `aps-backend` 和 `aps-frontend`。
5. 启动并等待 `aps-postgres` 就绪。
6. 把备份 SQL 拷贝进 PostgreSQL 容器。
7. 删除并重建 `public` schema。
8. 导入备份 SQL。
9. 删除容器内临时 SQL 文件。
10. 重新启动 `aps-backend` 和 `aps-frontend`。

第 7 步会清空当前数据库结构和数据，因此正式恢复前必须完成确认。

### 8.5 恢复后检查

恢复完成后执行：

```powershell
cd D:\Code
docker compose --env-file aps.env -f docker-compose.aps.yml ps
```

确认：

- `aps-postgres` 为 healthy 或 running。
- `aps-backend` 为 healthy 或 running。
- `aps-frontend` 为 healthy 或 running。

再访问：

```text
http://127.0.0.1:8095
```

并检查：

1. 能打开 APS 登录页。
2. 管理员账号能登录。
3. 用户权限页面能查询用户。
4. ERP 同步页面能看到历史同步记录。
5. 分配中心能看到工单。
6. 部门中心能查询任务。
7. 零件库存、材料库存页面数据正常。
8. 工艺管理、机台能力页面数据正常。
9. 新增一条测试数据后刷新仍存在。

如果恢复后登录失败，应先确认恢复的是正确备份，再确认 `aps.env` 中的 JWT 密钥是否变更。JWT 密钥变化会导致旧浏览器 Token 失效，重新登录即可。

## 9. 新生产库初始化与备份关系

正式上线时建议使用新 Docker volume：

```env
APS_POSTGRES_VOLUME_NAME=aps_postgres_data_prod
```

这会初始化一套干净生产库。上线前如果旧 APS 数据没有保留价值，可以直接使用新库，让 Flyway 自动创建表结构和初始管理员。

如果需要从旧环境迁移数据，则流程是：

1. 旧环境执行 `backup-aps.ps1`。
2. 新环境配置新的 `aps.env`。
3. 新环境启动 `aps-postgres`。
4. 在新环境执行 `restore-drill-aps.ps1` 验证备份。
5. 在新环境执行 `restore-aps.ps1 -Force` 正式恢复。
6. 启动并验收新环境。

## 10. 备份文件保管要求

APS 备份包含企业生产计划、库存和客户订单相关数据，应按内部敏感数据管理。

建议要求：

- 本机保留最近 14 到 30 天备份。
- 每天或每周复制一份到独立存储。
- 独立存储不能和 APS 服务在同一块单盘上。
- 至少保留一份离线或异机备份。
- 限制备份目录访问权限。
- 备份文件不要通过普通聊天工具发送。
- 离职或权限变更时同步回收备份访问权限。

## 11. 常见问题

### 11.1 提示找不到 aps.env

原因：

- `D:\Code\aps.env` 不存在。
- 当前目录不是 `D:\Code`。
- 指定了错误的 `-ProjectRoot`。

处理：

```powershell
Copy-Item D:\Code\aps.env.example D:\Code\aps.env
notepad D:\Code\aps.env
```

然后补齐生产数据库密码、JWT 密钥、ERP 地址和 ERP Token。

### 11.2 备份失败，提示容器未运行

检查：

```powershell
cd D:\Code
docker compose --env-file aps.env -f docker-compose.aps.yml ps
```

如果服务未启动，先启动：

```powershell
docker compose --env-file aps.env -f docker-compose.aps.yml up -d
```

### 11.3 恢复演练失败，提示 PostgreSQL 镜像不存在

恢复演练默认使用 `postgres:15`。如果服务器没有该镜像且不能联网，需要提前准备：

```powershell
docker pull postgres:15
```

或由部署人员导入离线镜像。

### 11.4 正式恢复后页面能打开但接口报错

处理顺序：

1. 查看后端日志：

```powershell
docker logs aps-backend --tail 200
```

2. 查看数据库容器日志：

```powershell
docker logs aps-postgres --tail 200
```

3. 确认 `aps.env` 中数据库用户、密码、库名与 Compose 一致。
4. 确认恢复 SQL 是 APS 备份，不是 ERP 备份。
5. 确认 Flyway 迁移表存在。

### 11.5 正式恢复后用户说数据又变回去了

优先检查：

- 是否恢复错备份目录。
- 是否有多个 APS 数据库 volume。
- `APS_POSTGRES_VOLUME_NAME` 是否和预期一致。
- 浏览器是否访问了另一套 APS 前端地址。
- 外层 Nginx 是否转发到正确 APS 前端。

查看当前 Compose 使用的 volume：

```powershell
cd D:\Code
docker volume ls | Select-String aps
docker inspect aps-postgres
```

## 12. 上线前必须完成的备份验收

上线前必须完成并记录：

| 验收项 | 要求 |
| --- | --- |
| 手动备份 | 能生成 `db\aps.sql` 和 `meta\backup.json` |
| 定时备份 | Windows 计划任务能按时生成备份 |
| 恢复演练 | 至少一次成功恢复到临时 PostgreSQL 容器 |
| 正式恢复预案 | 明确谁有权限执行、执行前通知谁、恢复哪个目录 |
| 备份保留 | 明确保留天数和异机保存方式 |
| 备份权限 | 明确谁能读取备份目录 |
| 恢复后验收 | 登录、用户管理、工单、库存、工艺、机台能力均可使用 |

只有备份和恢复演练都成功，才能认为 APS 数据安全具备上线条件。
