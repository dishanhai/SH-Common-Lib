# 构建经验

- Maven 发布：`MavenArtifact.builtBy` 需要任务或任务提供器。ForgeGradle 较晚注册 `reobfJar`，用闭包延迟查询任务提供器，确保发布时先重映射 JAR。
