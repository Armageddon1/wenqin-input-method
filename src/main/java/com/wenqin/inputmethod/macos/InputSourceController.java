package com.wenqin.inputmethod.macos;

import com.wenqin.inputmethod.context.InputMode;

/**
 * 抽象输入源切换能力，隔离 IntelliJ 语境逻辑和 macOS 原生实现。
 */
public interface InputSourceController {
    /**
     * 将当前 macOS 输入源切换到目标模式。
     *
     * @param targetMode 目标输入法
     * @return 切换成功或已经处于目标输入法时返回 true
     */
    boolean switchTo(InputMode targetMode);
}
