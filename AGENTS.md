# AGENTS.md

## 项目与提交约定

- 后端工程位于 `backend/`，Maven 命令默认在该目录执行。
- 每次修改完成后，先检查 `.gitignore` 是否需要新增规则，再提交 Git；提交信息要说明本次改动目的。
- 当前仓库使用学号分支 `220250946451`。除非用户明确要求，不要新建其他分支，也不要执行 `git push`。
- 只暂存当前任务涉及的文件，不要把工作区中无关的已有改动一起提交。

## Windows 与 PowerShell

- 本项目运行在 Windows 上，所有自动化命令必须显式使用 **PowerShell**，不要使用 `bash`。
- 部分环境会把 `bash` 转发到 WSL，结果不仅无法执行 `Get-Content` 等 PowerShell 命令，还可能出现 `wsl: 检测到 localhost ...` 报错；在 WSL 中执行 `git status` 也可能因换行符规则误报大量文件被修改。
- 检查 Git 状态、读取文件和执行 Maven 都应使用 PowerShell，避免跨 WSL/Windows 文件系统操作。

## Java 与 Maven

- 不要依赖系统默认 Java。执行构建前显式设置 JDK 19：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-19'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

- Maven 依赖仓库使用固定路径，避免在受限环境中发生仓库访问或权限问题：

```powershell
& mvn '-Dmaven.repo.local=C:\Users\18537\.m2\repository' clean package
```

- 如果 `javac` 在读取 `C:\Users\18537\.m2\repository` 下的 Jackson 等依赖时抛出 `AccessDeniedException`，优先判断为命令执行环境的文件访问限制，而不是依赖损坏；应改用完全访问权限执行，不要通过删除 Maven 仓库来规避。
- Maven 输出可能很长。需要提取结果时，可将完整日志重定向到临时文件，再筛选 `Tests run:`、`BUILD SUCCESS`、`BUILD FAILURE` 和 `[ERROR]`。

## Spring Boot 与 H2 测试

- `application.yml` 使用 `jdbc:h2:mem:marketplace;DB_CLOSE_DELAY=-1`。同一 JVM 中如果有多个不同的 Spring 测试上下文，它们会共享这个数据库，导致 `schema.sql` 再次初始化时报 `Table "PRODUCTS" already exists`。
- 新增使用 `@MockBean` 或其他上下文配置的 `@SpringBootTest` 测试类时，应为该测试上下文配置独立的数据库，例如：

```java
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:unexpected-exception-test;DB_CLOSE_DELAY=-1")
```

- 不要只运行单个测试就认为完成。新增测试上下文后必须再运行完整 `mvn clean package`，以发现测试上下文缓存和共享 H2 数据库之间的相互影响。

## 提交前检查

- 确保文件以换行符结尾，并执行：

```powershell
git diff --check
git status --short
```

- Git 提示 `LF will be replaced by CRLF` 通常只是 Windows 换行符转换警告，不代表提交失败。
- 当前 `.gitignore` 已覆盖 `target/`、`*.log`、数据库文件、`.env` 和编辑器产物；若产生新的构建或本地运行产物，应先补充忽略规则。