package com.wenqin.inputmethod.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * 在 IDEA 项目启动完成后注册输入法语境监听器。
 */
public final class InputMethodSwitchingStartupActivity implements StartupActivity.DumbAware {
    /**
     * 启动当前项目的输入法自动切换服务。
     *
     * @param project 当前 IDEA 项目
     */
    @Override
    public void runActivity(@NotNull Project project) {
        project.getService(InputMethodSwitchingService.class).start();
    }
}
