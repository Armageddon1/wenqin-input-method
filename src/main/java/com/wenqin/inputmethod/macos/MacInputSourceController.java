package com.wenqin.inputmethod.macos;

import com.intellij.openapi.diagnostic.Logger;
import com.wenqin.inputmethod.context.InputMode;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import java.nio.charset.StandardCharsets;

/**
 * 通过 macOS Text Input Source Services 切换输入源。
 *
 * <p>该类只负责与 Carbon/CoreFoundation 原生接口交互，不包含编辑器语境判断。</p>
 */
public final class MacInputSourceController implements InputSourceController {
    private static final Logger LOG = Logger.getInstance(MacInputSourceController.class);
    private static final String HIToolbox_FRAMEWORK =
            "/System/Library/Frameworks/Carbon.framework/Frameworks/HIToolbox.framework/HIToolbox";
    private static final String CORE_FOUNDATION_FRAMEWORK =
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation";
    private static final int CF_STRING_ENCODING_UTF8 = 0x08000100;

    private final Hitoolbox hitoolbox;
    private final CoreFoundation coreFoundation;
    private final Pointer inputSourceIdProperty;
    private final MacMainQueueExecutor mainQueueExecutor;

    /**
     * 初始化 macOS 原生输入源接口。
     */
    public MacInputSourceController() {
        LOG.info("初始化 macOS Text Input Source Services");
        try {
            MacJnaLibraryLoader.prepare();
        } catch (Exception error) {
            LOG.warn("准备 JNA macOS 原生库失败", error);
            throw new IllegalStateException("无法准备 JNA macOS 原生库", error);
        }
        hitoolbox = Native.load(HIToolbox_FRAMEWORK, Hitoolbox.class);
        coreFoundation = Native.load(CORE_FOUNDATION_FRAMEWORK, CoreFoundation.class);
        Pointer propertyAddress = NativeLibrary.getInstance(HIToolbox_FRAMEWORK)
                .getGlobalVariableAddress("kTISPropertyInputSourceID");
        inputSourceIdProperty = propertyAddress == null ? null : propertyAddress.getPointer(0);
        if (inputSourceIdProperty == null) {
            throw new IllegalStateException("无法读取 macOS 输入源 ID 属性");
        }
        mainQueueExecutor = new MacMainQueueExecutor();
    }

    /**
     * 将当前输入源切换到目标输入源。
     *
     * @param targetMode 目标输入法
     * @return 切换成功或已经处于目标输入法时返回 true
     */
    @Override
    public boolean switchTo(InputMode targetMode) {
        return mainQueueExecutor.execute(() -> switchToOnMainQueue(targetMode));
    }

    /**
     * 在 macOS 主队列执行输入源切换。
     *
     * @param targetMode 目标输入法
     * @return 切换成功或已经处于目标输入法时返回 true
     */
    private boolean switchToOnMainQueue(InputMode targetMode) {
        String targetId = targetMode.getInputSourceId();
        LOG.info("调用 macOS 原生接口切换输入源，目标=" + targetMode.getDisplayName() + ", id=" + targetId);

        String currentId = currentInputSourceId();
        if (targetId.equals(currentId)) {
            LOG.debug("当前输入源已经是目标输入源，跳过切换，id=" + targetId);
            return true;
        }

        Pointer inputSources = hitoolbox.TISCreateInputSourceList(null, (byte) 1);
        if (inputSources == null) {
            LOG.warn("macOS 返回空输入源列表，无法切换，目标=" + targetId);
            return false;
        }

        try {
            long count = coreFoundation.CFArrayGetCount(inputSources);
            for (long index = 0; index < count; index++) {
                Pointer source = coreFoundation.CFArrayGetValueAtIndex(inputSources, index);
                if (source == null || !targetId.equals(inputSourceId(source))) {
                    continue;
                }

                int status = hitoolbox.TISSelectInputSource(source);
                LOG.info("macOS 输入源切换结果，目标=" + targetId + ", status=" + status);
                return status == 0;
            }

            LOG.warn("未找到目标 macOS 输入源，请确认已安装并启用，id=" + targetId);
            return false;
        } finally {
            coreFoundation.CFRelease(inputSources);
        }
    }

    /**
     * 读取当前 macOS 输入源 ID。
     *
     * @return 当前输入源 ID，读取失败时返回 null
     */
    private String currentInputSourceId() {
        LOG.debug("调用 macOS 原生接口读取当前输入源");
        Pointer source = hitoolbox.TISCopyCurrentKeyboardInputSource();
        if (source == null) {
            return null;
        }
        try {
            return inputSourceId(source);
        } finally {
            coreFoundation.CFRelease(source);
        }
    }

    /**
     * 读取指定输入源的 ID 属性。
     *
     * @param source macOS 输入源指针
     * @return 输入源 ID
     */
    private String inputSourceId(Pointer source) {
        Pointer value = hitoolbox.TISGetInputSourceProperty(source, inputSourceIdProperty);
        if (value == null) {
            return null;
        }

        byte[] buffer = new byte[256];
        byte success = coreFoundation.CFStringGetCString(
                value, buffer, buffer.length, CF_STRING_ENCODING_UTF8
        );
        if (success == 0) {
            return null;
        }

        int length = 0;
        while (length < buffer.length && buffer[length] != 0) {
            length++;
        }
        return new String(buffer, 0, length, StandardCharsets.UTF_8);
    }

    /**
     * Carbon Text Input Source Services 原生函数映射。
     */
    private interface Hitoolbox extends Library {
        /**
         * 读取当前键盘输入源。
         *
         * @return 输入源指针
         */
        Pointer TISCopyCurrentKeyboardInputSource();

        /**
         * 创建输入源列表。
         *
         * @param properties 过滤属性，允许为空
         * @param includeAllInstalled 是否包含所有已安装输入源
         * @return 输入源数组指针
         */
        Pointer TISCreateInputSourceList(Pointer properties, byte includeAllInstalled);

        /**
         * 读取输入源属性。
         *
         * @param source 输入源指针
         * @param property 属性键指针
         * @return 属性值指针
         */
        Pointer TISGetInputSourceProperty(Pointer source, Pointer property);

        /**
         * 选中输入源。
         *
         * @param source 输入源指针
         * @return OSStatus
         */
        int TISSelectInputSource(Pointer source);
    }

    /**
     * CoreFoundation 数组和字符串函数映射。
     */
    private interface CoreFoundation extends Library {
        /**
         * 返回 CFArray 元素数量。
         *
         * @param array 数组指针
         * @return 元素数量
         */
        long CFArrayGetCount(Pointer array);

        /**
         * 读取 CFArray 指定位置的元素。
         *
         * @param array 数组指针
         * @param index 元素下标
         * @return 元素指针
         */
        Pointer CFArrayGetValueAtIndex(Pointer array, long index);

        /**
         * 将 CFString 转为 UTF-8 字节数组。
         *
         * @param string CFString 指针
         * @param buffer 输出缓冲区
         * @param bufferSize 缓冲区大小
         * @param encoding 编码常量
         * @return 成功标记
         */
        byte CFStringGetCString(Pointer string, byte[] buffer, long bufferSize, int encoding);

        /**
         * 释放 CoreFoundation 对象。
         *
         * @param value 对象指针
         */
        void CFRelease(Pointer value);
    }
}
