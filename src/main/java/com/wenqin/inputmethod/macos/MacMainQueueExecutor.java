package com.wenqin.inputmethod.macos;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 将 macOS 原生输入源操作同步转发到 AppKit 主队列。
 *
 * <p>macOS 15 的 Text Input Source Services 要求部分 API 在主队列执行，
 * 否则系统会触发原生线程断言并直接终止 IDEA 进程。</p>
 */
public final class MacMainQueueExecutor {
    private static final Logger LOG = Logger.getInstance(MacMainQueueExecutor.class);
    private static final String LIB_SYSTEM = "/usr/lib/libSystem.B.dylib";

    private final LibSystem libSystem;
    private final Pointer mainQueue;

    /**
     * 初始化 macOS 主队列执行器。
     */
    public MacMainQueueExecutor() {
        libSystem = Native.load(LIB_SYSTEM, LibSystem.class);
        mainQueue = NativeLibrary.getInstance(LIB_SYSTEM)
                .getGlobalVariableAddress("_dispatch_main_q");
        if (mainQueue == null) {
            throw new IllegalStateException("无法获取 macOS 主队列");
        }
    }

    /**
     * 在 macOS 主队列同步执行任务。
     *
     * @param task 待执行的原生任务
     * @param <T> 返回值类型
     * @return 原生任务返回值
     */
    public <T> T execute(Supplier<T> task) {
        if (libSystem.pthread_main_np() != 0) {
            return task.get();
        }

        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        DispatchWork work = ignored -> {
            try {
                result.set(task.get());
            } catch (Throwable error) {
                failure.set(error);
            }
        };

        LOG.debug("将 macOS 输入源原生调用转发到主队列");
        libSystem.dispatch_sync_f(mainQueue, null, work);
        Throwable error = failure.get();
        if (error instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (error instanceof Error fatalError) {
            throw fatalError;
        }
        if (error != null) {
            throw new IllegalStateException("macOS 主队列任务执行失败", error);
        }
        return result.get();
    }

    /**
     * libSystem 中用于访问 macOS 主队列的函数映射。
     */
    private interface LibSystem extends Library {
        /**
         * 判断当前线程是否为进程主线程。
         *
         * @return 主线程返回非零值
         */
        int pthread_main_np();

        /**
         * 在目标队列同步执行回调。
         *
         * @param queue 目标队列
         * @param context 回调上下文
         * @param work 待执行回调
         */
        void dispatch_sync_f(Pointer queue, Pointer context, DispatchWork work);
    }

    /**
     * libSystem 的 dispatch_sync_f 回调映射。
     */
    private interface DispatchWork extends Callback {
        /**
         * 执行主队列任务。
         *
         * @param context 原生上下文
         */
        void invoke(Pointer context);
    }
}
