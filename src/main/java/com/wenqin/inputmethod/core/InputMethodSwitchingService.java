package com.wenqin.inputmethod.core;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.wenqin.inputmethod.context.InputContext;
import com.wenqin.inputmethod.context.InputMode;
import com.wenqin.inputmethod.context.TextContextDetector;
import com.wenqin.inputmethod.macos.InputSourceController;
import com.wenqin.inputmethod.macos.MacInputSourceController;
import org.jetbrains.annotations.NotNull;

import java.awt.event.FocusEvent;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 监听 IDEA 编辑器语境变化，并协调语境检测与 macOS 输入源切换。
 */
public final class InputMethodSwitchingService implements Disposable, CaretListener, FocusChangeListener,
        EditorFactoryListener, DocumentListener {
    private static final Logger LOG = Logger.getInstance(InputMethodSwitchingService.class);
    private static final String NOTIFICATION_GROUP_ID = "问琴爱切输入法";

    private final Project project;
    private final TextContextDetector contextDetector;
    private final Map<Editor, InputMode> lastAppliedModes = new WeakHashMap<>();
    private final Map<Editor, InputMode> pendingModes = new WeakHashMap<>();
    private final AtomicBoolean configurationProblemShown = new AtomicBoolean();
    private InputSourceController inputSourceController;
    private boolean started;

    /**
     * 创建项目级输入法切换服务。
     *
     * @param project 当前项目
     */
    public InputMethodSwitchingService(@NotNull Project project) {
        this.project = project;
        this.contextDetector = new TextContextDetector();
    }

    /**
     * 注册编辑器监听器并初始化 macOS 输入源控制器。
     */
    public void start() {
        if (started || project.isDisposed()) {
            return;
        }
        started = true;

        if (!SystemInfo.isMac) {
            LOG.warn("问琴爱切输入法仅支持 macOS，当前平台不启用自动切换");
            return;
        }

        try {
            inputSourceController = new MacInputSourceController();
        } catch (Throwable error) {
            LOG.warn("初始化 macOS 输入源控制器失败，自动切换已停用", error);
            showConfigurationProblemOnce("无法初始化 macOS 输入法切换，请检查系统权限。", error);
            return;
        }

        EditorFactory.getInstance().getEventMulticaster().addCaretListener(this, this);
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(this, this);
        EditorFactory.getInstance().addEditorFactoryListener(this, this);

        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            if (editor.getProject() == project) {
                registerFocusListener(editor);
                scheduleApply(editor);
            }
        }
        LOG.info("问琴爱切输入法已启动，项目=" + project.getName());
    }

    /**
     * 处理光标移动事件。
     *
     * @param event 光标事件
     */
    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
        scheduleApply(event.getEditor());
    }

    /**
     * 处理文档变更，保证刚输入注释起始符时不依赖光标事件时序。
     *
     * @param event 文档变更事件
     */
    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        for (Editor editor : EditorFactory.getInstance().getEditors(event.getDocument())) {
            if (editor.getProject() == project) {
                scheduleApply(editor);
            }
        }
    }

    /**
     * 处理编辑器获得焦点事件。
     *
     * @param editor 获得焦点的编辑器
     */
    @Override
    public void focusGained(@NotNull Editor editor) {
        scheduleApply(editor);
    }

    /**
     * 处理编辑器失去焦点事件，清理缓存以便下次重新校准输入源。
     *
     * @param editor 失去焦点的编辑器
     */
    @Override
    public void focusLost(@NotNull Editor editor) {
        if (editor.getProject() == project) {
            lastAppliedModes.remove(editor);
            pendingModes.remove(editor);
        }
    }

    /**
     * 处理带原生 FocusEvent 的编辑器获得焦点事件。
     *
     * @param editor 获得焦点的编辑器
     * @param event 原生焦点事件
     */
    @Override
    public void focusGained(@NotNull Editor editor, @NotNull FocusEvent event) {
        focusGained(editor);
    }

    /**
     * 处理带原生 FocusEvent 的编辑器失去焦点事件。
     *
     * @param editor 失去焦点的编辑器
     * @param event 原生焦点事件
     */
    @Override
    public void focusLost(@NotNull Editor editor, @NotNull FocusEvent event) {
        focusLost(editor);
    }

    /**
     * 处理新建编辑器事件，覆盖 Git 提交信息等动态创建的编辑器。
     *
     * @param event 编辑器创建事件
     */
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        if (event.getEditor().getProject() == project) {
            registerFocusListener(event.getEditor());
            scheduleApply(event.getEditor());
        }
    }

    /**
     * 处理编辑器释放事件。
     *
     * @param event 编辑器释放事件
     */
    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        lastAppliedModes.remove(event.getEditor());
        pendingModes.remove(event.getEditor());
    }

    /**
     * 为编辑器注册焦点监听器，覆盖普通代码编辑器和 Commit Message 编辑器。
     *
     * @param editor 待注册编辑器
     */
    private void registerFocusListener(Editor editor) {
        if (editor instanceof EditorEx editorEx) {
            editorEx.addFocusListener(this, this);
        }
    }

    /**
     * 将语境判断延迟到 IDEA 事件队列，避免在 PSI 尚未提交时读取旧语法树。
     *
     * @param editor 待处理编辑器
     */
    private void scheduleApply(Editor editor) {
        if (editor.getProject() != project || project.isDisposed() || inputSourceController == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> applyContext(editor), project.getDisposed());
    }

    /**
     * 检测编辑器语境并在目标输入源变化时执行切换。
     *
     * @param editor 当前编辑器
     */
    private void applyContext(Editor editor) {
        if (project.isDisposed() || editor.isDisposed() || editor.getProject() != project) {
            return;
        }

        InputContext context = contextDetector.detect(editor);
        InputMode targetMode = context.getTargetMode();
        InputMode lastAppliedMode = lastAppliedModes.get(editor);
        InputMode pendingMode = pendingModes.get(editor);
        if (targetMode == null || targetMode == lastAppliedMode || targetMode == pendingMode) {
            return;
        }

        InputSourceController controller = inputSourceController;
        if (controller == null) {
            return;
        }

        // 记录进行中的目标，合并光标事件，避免同一个编辑器重复发起原生调用。
        pendingModes.put(editor, targetMode);

        // macOS 输入源服务必须在主队列调用，但不能阻塞 IDEA 的 AWT 事件线程。
        ApplicationManager.getApplication().executeOnPooledThread(
                () -> switchInBackground(editor, context, targetMode, controller)
        );
    }

    /**
     * 在线程池中执行可能阻塞的 macOS 输入源切换。
     *
     * @param editor 当前编辑器
     * @param context 已检测出的文本语境
     * @param targetMode 目标输入模式
     * @param controller 输入源控制器
     */
    private void switchInBackground(Editor editor, InputContext context, InputMode targetMode,
                                    InputSourceController controller) {
        boolean switched = false;
        Throwable error = null;
        try {
            switched = controller.switchTo(targetMode);
        } catch (Throwable switchError) {
            error = switchError;
            LOG.warn("后台切换 macOS 输入源失败，context=" + context + ", mode=" + targetMode, switchError);
        }

        boolean switchResult = switched;
        Throwable switchError = error;
        ApplicationManager.getApplication().invokeLater(
                () -> finishSwitchOnEdt(editor, context, targetMode, switchResult, switchError),
                project.getDisposed()
        );
    }

    /**
     * 在 IDEA 事件线程提交切换结果并更新编辑器状态。
     *
     * @param editor 当前编辑器
     * @param context 已检测出的文本语境
     * @param targetMode 目标输入模式
     * @param switched 是否切换成功
     * @param error 切换异常
     */
    private void finishSwitchOnEdt(Editor editor, InputContext context, InputMode targetMode,
                                   boolean switched, Throwable error) {
        pendingModes.remove(editor);
        if (project.isDisposed() || editor.isDisposed() || editor.getProject() != project) {
            return;
        }

        if (switched) {
            lastAppliedModes.put(editor, targetMode);
            LOG.debug("已应用输入法语境，context=" + context + ", mode=" + targetMode);
            return;
        }

        showConfigurationProblemOnce(
                "无法切换到“" + targetMode.getDisplayName() + "”，请确认该输入法已启用并允许 IDEA 使用辅助功能。",
                error
        );
    }

    /**
     * 只向用户展示一次配置问题，详细信息同时写入 IDEA 日志。
     *
     * @param message 用户可读提示
     * @param error 失败异常
     */
    private void showConfigurationProblemOnce(String message, Throwable error) {
        if (error != null) {
            LOG.warn(message, error);
        } else {
            LOG.warn(message);
        }
        if (!configurationProblemShown.compareAndSet(false, true)) {
            return;
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(message, NotificationType.WARNING)
                .notify(project);
    }

    /**
     * 释放服务缓存；编辑器监听器由项目 Disposable 自动注销。
     */
    @Override
    public void dispose() {
        lastAppliedModes.clear();
        pendingModes.clear();
        inputSourceController = null;
        LOG.info("问琴爱切输入法已停止，项目=" + project.getName());
    }
}
