# QPO后端系统说明

## 项目用途

`quote-backend` 是QPO后端服务，负责订单、报价、材料与工艺、文件、库存和APS数据同步等业务接口。

## 技术栈

- Spring Boot
- PostgreSQL
- Flyway
- MyBatis-Plus
- Maven

## 本地运行

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd spring-boot:run
```

默认后端端口是 `8080`，可通过环境变量 `SERVER_PORT` 修改。

## 测试

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test
```

## Docker部署

QPO 的 Docker 编排文件在 `D:\QPO-Operations-Management-System\docker-compose.yml`，运行配置在 `D:\QPO-Operations-Management-System\erp.env`。

```powershell
cd D:\QPO-Operations-Management-System
docker compose --env-file erp.env up -d --build
```

## 相关文档

- [Maven官方文档](https://maven.apache.org/guides/index.html)
- [Spring Boot Maven插件](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/)
- [Spring Web文档](https://docs.spring.io/spring-boot/reference/web/servlet.html)
