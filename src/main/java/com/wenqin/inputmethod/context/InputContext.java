package com.wenqin.inputmethod.context;

/**
 * 表示当前编辑器所在的文本语境及其目标输入法。
 */
public enum InputContext {
    /** 普通代码或配置内容。 */
    CODE(InputMode.ENGLISH),

    /** 行注释、块注释和文档注释。 */
    COMMENT(InputMode.CHINESE),

    /** IDEA 的 Git 提交信息输入框。 */
    COMMIT_MESSAGE(InputMode.CHINESE),

    /** Markdown、TXT 等纯文本文件。 */
    UNSUPPORTED(null),

    /** 无法归类或不属于受管理编辑区域的内容。 */
    UNMANAGED(null);

    private final InputMode targetMode;

    /**
     * 创建一个文本语境。
     *
     * @param targetMode 该语境对应的输入法
     */
    InputContext(InputMode targetMode) {
        this.targetMode = targetMode;
    }

    /**
     * 返回该语境要求的输入法。
     *
     * @return 输入法，未管理语境返回 null
     */
    public InputMode getTargetMode() {
        return targetMode;
    }
}
