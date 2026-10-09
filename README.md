# 问琴爱切输入法

面向 IntelliJ IDEA 2026.2.1、macOS 的输入法自动切换插件。

- 代码、配置、字符串：自动切换到 `ABC`。
- 行注释、块注释、文档注释：自动切换到“简体拼音”。
- IDEA Git Commit Message：自动切换到“简体拼音”。
- Markdown、TXT、Word 等纯文本或文档文件不处理。
- macOS 输入源缺失、权限不足或切换失败时只提示一次，并写入 IDEA 日志。

## 构建

使用 IntelliJ IDEA 自带的 JBR 25 和 Gradle 9：

```bash
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
./gradlew buildPlugin
```

生成的插件包位于 `build/distributions/`。
