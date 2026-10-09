package com.wenqin.inputmethod.context;

/**
 * 表示插件支持的 macOS 输入源目标。
 */
public enum InputMode {
    /** 代码区域使用的英文输入源。 */
    ENGLISH("ABC", "com.apple.keylayout.ABC"),

    /** 注释和提交信息使用的简体拼音输入源。 */
    CHINESE("简体拼音", "com.apple.inputmethod.SCIM.ITABC");

    private final String displayName;
    private final String inputSourceId;

    /**
     * 创建一个输入源目标。
     *
     * @param displayName 输入源显示名称
     * @param inputSourceId macOS 输入源 ID
     */
    InputMode(String displayName, String inputSourceId) {
        this.displayName = displayName;
        this.inputSourceId = inputSourceId;
    }

    /**
     * 返回输入源显示名称。
     *
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 返回 macOS 输入源 ID。
     *
     * @return 输入源 ID
     */
    public String getInputSourceId() {
        return inputSourceId;
    }
}
