package com.wenqin.inputmethod.macos;

import java.util.Locale;

/**
 * 表示 macOS 支持的 CPU 架构及其 JNA 原生库资源路径。
 */
public enum MacArchitecture {
    /** Intel Mac 架构。 */
    X86_64("x86_64", "darwin-x86-64"),

    /** Apple Silicon 架构。 */
    AARCH64("aarch64", "darwin-aarch64");

    private static final String JNA_LIBRARY_FILE_NAME = "libjnidispatch.jnilib";

    private final String systemArchitecture;
    private final String jnaResourceDirectory;

    /**
     * 创建 macOS 架构描述。
     *
     * @param systemArchitecture Java 报告的架构名称
     * @param jnaResourceDirectory JNA 原生库所在资源目录
     */
    MacArchitecture(String systemArchitecture, String jnaResourceDirectory) {
        this.systemArchitecture = systemArchitecture;
        this.jnaResourceDirectory = jnaResourceDirectory;
    }

    /**
     * 根据当前 JVM 架构选择对应的 macOS 架构。
     *
     * @return 当前 macOS 架构
     * @throws IllegalStateException 当前架构不受支持时抛出
     */
    public static MacArchitecture current() {
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if ("aarch64".equals(architecture) || "arm64".equals(architecture)) {
            return AARCH64;
        }
        if ("x86_64".equals(architecture) || "amd64".equals(architecture)) {
            return X86_64;
        }
        throw new IllegalStateException("不支持的 macOS CPU 架构：" + architecture);
    }

    /**
     * 返回 Java 架构名称。
     *
     * @return 架构名称
     */
    public String getSystemArchitecture() {
        return systemArchitecture;
    }

    /**
     * 返回插件包内的 JNA 原生库资源路径。
     *
     * @return 资源路径
     */
    public String getJnaResourcePath() {
        return "/com/sun/jna/" + jnaResourceDirectory + "/" + JNA_LIBRARY_FILE_NAME;
    }

    /**
     * 返回 JNA 原生库文件名。
     *
     * @return 原生库文件名
     */
    public String getJnaLibraryFileName() {
        return JNA_LIBRARY_FILE_NAME;
    }
}
