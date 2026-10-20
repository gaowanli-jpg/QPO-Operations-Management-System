# QPO / APS 工作区拆分迁移开发文档

## 1. 背景与目标

当前 `D:\QPO-Operations-Management-System` 是一个 Git 根目录，里面同时包含 QPO 与 APS 两套系统：

- QPO：`quote-frontend`、`旧 QPO 后端目录`、`docker-compose.yml`、`erp.env`、QPO 文档与业务资料。
- APS：`APS 前端目录`、`APS 后端目录`、`APS Compose 文件`、`APS 环境变量文件`、APS 部署/验收/备份资料。

后续 QPO 已经转向 MES / WMS 对接，APS 不再是 QPO 新目标系统。为了降低维护混淆，工作区需要拆成两个英文主目录：

```text
D:\
  QPO-Operations-Management-System\
  APS-Operations-Management-System\
```

同时，QPO 后端目录从 `旧 QPO 后端目录` 改名为 `quote-backend`，让前后端命名更直观。

## 2. 目标目录结构

### 2.1 QPO 主目录

目标路径：

```text
D:\QPO-Operations-Management-System\
```

保留内容：

- `quote-backend\`：由当前 `旧 QPO 后端目录\` 重命名而来。
- `quote-frontend\`
- `docker-compose.yml`
- `erp.env`
- `erp.env.example`
- `docs\QPO前端优化\`
- `docs\QPO权限用户体系\`
- `docs\QPO日志模块\`
- `docs\QPO报价状态流转\`
- `docs\QPO-MES-WMS对接\`
- `scripts\` 中 QPO 专用脚本：
  - `backup-qpo.sh`
  - `restore-qpo.sh`
  - `capacity-report.ps1`
- QPO 业务资料文件夹：
  - `图纸\`
  - `材料与工艺费用表\`
  - `用例表格资料\`
- QPO 运维资料：
  - `QPO部署说明.md`
  - `QPO运维说明.md`
  - `QPO服务器备份说明.md`
  - `QPO服务器恢复说明.md`
  - `backups\` 中 QPO 相关备份
  - `logs\` 中 QPO 相关日志
  - `capacity-reports\` 中 QPO 相关容量报告

### 2.2 APS 主目录

目标路径：

```text
D:\APS-Operations-Management-System\
```

迁出内容：

- `aps-backend\` 或保留 `APS 后端目录\`：默认先保留 `APS 后端目录\`，避免一次性扩大 APS 内部重命名范围。
- `APS 前端目录\`
- `APS Compose 文件`
- `APS 环境变量文件`
- `APS 环境变量示例文件`
- `start-aps-backend.ps1`
- APS 专用脚本：
  - `backup-aps.ps1`
  - `restore-aps.ps1`
  - `restore-drill-aps.ps1`
  - `install-aps-backup-task.ps1`
  - `run-aps-backup-task.ps1`
- APS 文档：
  - `APS上线验收清单.md`
  - `APS部署说明.md`
  - `交接文档\` 中 APS 相关章节或文件
  - `docs\superpowers\` 中 APS 专项设计文档
- `backups\aps\`、APS 专属日志和 APS 容量资料。

## 3. 迁移原则

1. **先改 Git 内可追踪路径，再改本机主目录名。**
   - 先用 `git mv 旧 QPO 后端目录 quote-backend`，更新 QPO 内部引用并提交。
   - 再拆出 APS。
   - 最后从 `D:\QPO-Operations-Management-System` 改名为 `D:\QPO-Operations-Management-System`。

2. **QPO 和 APS 拆分后互不混放。**
   - QPO 目录只保留 QPO 代码、QPO 配置、QPO 文档和 QPO 业务资料。
   - APS 目录只保留 APS 代码、APS 配置和 APS 运维资料。

3. **保留 QPO 兼容标识。**
   - 不在本阶段批量替换 `erp.env`、`ERP_*`、`erp_token`、`erp_user`、`erp-page`、`--erp-*` 等兼容标识。
   - `旧 QPO 后端目录` 作为文件夹名改为 `quote-backend`；Maven artifact、Spring application name、数据库兼容名称是否改名，单独评估。

4. **资料文件夹不丢失。**
   - `图纸`、`材料与工艺费用表`、`用例表格资料` 明确保留在 QPO 主目录。
   - `backups`、`logs`、`交接文档` 等混合资料按 QPO / APS 归属拆分，不能简单整目录删除。

5. **路径默认值同步更新。**
   - QPO 脚本默认根目录从 `D:\QPO-Operations-Management-System` 改为 `D:\QPO-Operations-Management-System`。
   - APS 脚本默认根目录从 `D:\QPO-Operations-Management-System` 改为 `D:\APS-Operations-Management-System`。
   - 文档中的命令示例同步更新。

## 4. 阶段计划

### 阶段 0：迁移清单与护栏

目标：确认当前文件归属，避免误删或漏迁。

执行：

```powershell
cd D:\QPO-Operations-Management-System
git status --short
git ls-files > migration-files-before.txt
rg -n "旧 QPO 后端目录|quote-frontend|APS 后端目录|APS 前端目录|APS Compose 文件|aps\.env|旧 Code 根路径" -S .
```

验收：

- 工作树干净。
- 形成 QPO 文件清单、APS 文件清单、混合资料清单。
- 明确 `图纸`、`材料与工艺费用表`、`用例表格资料` 保留在 QPO。

### 阶段 1：QPO 后端目录重命名

目标：把 `旧 QPO 后端目录` 改为 `quote-backend`。

执行：

```powershell
cd D:\QPO-Operations-Management-System
git mv 旧 QPO 后端目录 quote-backend
```

同步修改：

- `docker-compose.yml` 的 backend build context。
- QPO 文档中的 `旧 QPO 后端目录` 路径。
- 后端测试中指向前端的相对路径，如 `../quote-frontend` 仍需保持正确。
- QPO 脚本和说明文档里的 `cd D:\QPO-Operations-Management-System\旧 QPO 后端目录`。
- `AGENTS.md` 中 QPO 系统描述。

验收：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=QuoteStatusWorkflowTest,PermissionModuleTreeTest

cd D:\QPO-Operations-Management-System\quote-frontend
npm test -- appShell.test.js permissionStage4.test.js

cd D:\QPO-Operations-Management-System
docker compose --env-file erp.env config | Select-String "quote-backend"
```

### 阶段 2：APS 内容迁出

目标：把 APS 独立到 `D:\APS-Operations-Management-System`。

建议：

- APS 先作为独立目录迁出。
- 如后续需要独立版本管理，在 APS 目录执行 `git init` 并提交初始快照。
- QPO 仓库记录删除 APS 文件，避免后续 QPO 误改 APS。

迁出对象：

```text
APS 后端目录\
APS 前端目录\
APS Compose 文件
APS 环境变量文件
APS 环境变量示例文件
APS上线验收清单.md
APS部署说明.md
start-aps-backend.ps1
scripts\*-aps.ps1
docs\superpowers\specs\*aps*.md
交接文档\ 中 APS 专属资料
backups\aps\
```

验收：

```powershell
cd D:\APS-Operations-Management-System
docker compose --env-file APS 环境变量文件 -f APS Compose 文件 config

cd D:\QPO-Operations-Management-System
rg -n "APS 后端目录|APS 前端目录|APS Compose 文件|aps\.env" -S .
```

QPO 目录中允许保留的 APS 字样仅限历史说明，例如“APS 为历史兼容出口，不是新目标系统”。

### 阶段 3：QPO 主目录改名

目标：把当前 `D:\QPO-Operations-Management-System` 改名为 `D:\QPO-Operations-Management-System`。

执行前提：

- QPO 阶段 1 已提交。
- APS 阶段 2 已完成并验证。
- 当前没有 Docker / IDE / 终端进程占用 `D:\QPO-Operations-Management-System`。

执行建议：

```powershell
cd D:\
Rename-Item -LiteralPath "D:\QPO-Operations-Management-System" -NewName "QPO-Operations-Management-System"
```

同步修改：

- QPO 脚本默认 `ProjectRoot`。
- QPO 文档中的 `cd D:\QPO-Operations-Management-System`。
- 备份、恢复、部署说明。
- Windows 计划任务需要卸载旧任务后按新路径重新安装。

验收：

```powershell
cd D:\QPO-Operations-Management-System
git status --short
docker compose --env-file erp.env config
```

### 阶段 4：文档与 Agent 记忆收口

目标：让后续 Codex 工作默认只看到 QPO 边界。

修改：

- `AGENTS.md`
  - 说明本目录为 QPO 专用工作区。
  - 移除“本仓库包含 QPO 和 APS 两套系统”的旧口径。
  - 保留“APS 历史兼容出口”说明，但不再要求在 QPO 仓库内维护 APS。
- QPO 部署与运维文档。
- APS 目录新增独立 `AGENTS.md`，说明 APS 边界。

验收：

```powershell
cd D:\QPO-Operations-Management-System
rg -n "旧 Code 根路径|旧 QPO 后端目录|APS 后端目录|APS 前端目录|APS Compose 文件|aps\.env" -S AGENTS.md docs scripts docker-compose.yml
```

预期：

- `旧 QPO 后端目录` 不再作为 QPO 当前目录名出现。
- `D:\QPO-Operations-Management-System` 不再作为 QPO 当前项目根路径出现。
- APS 当前工程路径不再出现在 QPO 操作指引中。

### 阶段 5：全量回归

QPO：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build

cd D:\QPO-Operations-Management-System
docker compose --env-file erp.env config
```

APS：

```powershell
cd D:\APS-Operations-Management-System\APS 后端目录
.\mvnw.cmd test

cd D:\APS-Operations-Management-System\APS 前端目录
npm test
npm run build

cd D:\APS-Operations-Management-System
docker compose --env-file APS 环境变量文件 -f APS Compose 文件 config
```

## 5. 风险与处理

### 5.1 Git 历史

如果 APS 迁出后直接在新目录 `git init`，APS 独立仓库不会保留完整历史。若必须保留 APS 历史，需要单独使用 Git 历史拆分方案，例如 `git filter-repo` 或 `git subtree split`，这应作为独立阶段执行。

推荐短期方案：先完成物理拆分和当前快照提交；如未来需要历史，再做历史迁移。

### 5.2 环境变量与密钥

`erp.env` 和 `APS 环境变量文件` 中包含真实密钥。迁移时只移动文件，不把密钥复制到公开文档、提交说明或截图中。

### 5.3 Docker 卷与容器名

目录改名不会自动迁移 Docker volume。Compose 项目名和卷名需要保持兼容，除非单独规划数据库迁移。

本阶段不改 QPO 数据库卷和 APS 数据库卷，只改代码目录和 build context。

### 5.4 备份定时任务

QPO 服务器使用 Linux `cron` 调用 `scripts/backup-qpo.sh`，不再保留旧的 Windows 计划任务脚本。具体命令和保留策略见 `QPO服务器备份说明.md`。

## 6. 完成定义

- QPO 主目录为 `D:\QPO-Operations-Management-System`。
- APS 主目录为 `D:\APS-Operations-Management-System`。
- QPO 后端目录为 `quote-backend`。
- QPO 主目录保留 `图纸`、`材料与工艺费用表`、`用例表格资料`。
- QPO 文档、脚本、Docker 配置不再引用当前项目路径 `D:\QPO-Operations-Management-System`。
- APS 文档、脚本、Docker 配置不再依赖 QPO 主目录。
- QPO 和 APS 各自测试、构建、Compose config 通过。
