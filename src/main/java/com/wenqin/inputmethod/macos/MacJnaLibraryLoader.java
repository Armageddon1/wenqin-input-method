package com.wenqin.inputmethod.macos;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 将 JNA 的 macOS 原生库解压到临时目录，并配置 JNA 的启动加载路径。
 *
 * <p>IDEA 插件使用独立的插件类加载器时，JNA 默认的资源提取逻辑可能找不到
 * 位于插件依赖 JAR 中的 jnidispatch，因此在首次调用 JNA 前显式准备原生库。</p>
 */
public final class MacJnaLibraryLoader {
    private static final Logger LOG = Logger.getInstance(MacJnaLibraryLoader.class);
    private static final String JNA_BOOT_LIBRARY_PATH = "jna.boot.library.path";
    private static final String TEMP_DIRECTORY_NAME = "wenqin-inputmethod";

    private MacJnaLibraryLoader() {
    }

    /**
     * 准备当前 macOS 架构对应的 JNA 原生库。
     *
     * @throws IOException 原生库无法读取或写入时抛出
     */
    public static void prepare() throws IOException {
        MacArchitecture architecture = MacArchitecture.current();
        Path libraryDirectory = Path.of(
                System.getProperty("java.io.tmpdir"),
                TEMP_DIRECTORY_NAME,
                architecture.getSystemArchitecture()
        );
        Files.createDirectories(libraryDirectory);

        Path libraryFile = libraryDirectory.resolve(architecture.getJnaLibraryFileName());
        if (!Files.exists(libraryFile) || Files.size(libraryFile) == 0) {
            extractLibrary(architecture, libraryFile);
        }

        System.setProperty(JNA_BOOT_LIBRARY_PATH, libraryDirectory.toString());
        LOG.info("JNA macOS 原生库已准备，架构=" + architecture.getSystemArchitecture()
                + ", 路径=" + libraryFile);
    }

    /**
     * 从插件类路径提取 JNA 原生库文件。
     *
     * @param architecture 当前 macOS 架构
     * @param targetFile 目标文件
     * @throws IOException 资源不存在或写入失败时抛出
     */
    private static void extractLibrary(MacArchitecture architecture, Path targetFile) throws IOException {
        LOG.info("开始提取 JNA macOS 原生库，资源=" + architecture.getJnaResourcePath());
        try (InputStream input = MacJnaLibraryLoader.class.getResourceAsStream(architecture.getJnaResourcePath())) {
            if (input == null) {
                throw new IOException("找不到 JNA 原生库资源：" + architecture.getJnaResourcePath());
            }
            Path temporaryFile = targetFile.resolveSibling(targetFile.getFileName() + ".tmp");
            Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
