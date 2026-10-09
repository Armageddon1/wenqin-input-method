package com.wenqin.inputmethod.context;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Locale;
import java.util.Set;

/**
 * 判断当前文件是否属于需要自动切换输入法的代码或配置文件。
 */
public final class CodeFileClassifier {
    private static final Set<String> UNSUPPORTED_EXTENSIONS = Set.of(
            "txt", "text", "md", "markdown", "rst", "adoc", "doc", "docx", "rtf", "pdf"
    );

    private static final Set<String> CODE_LIKE_FILENAMES = Set.of(
            ".env", ".env.local", ".env.production", ".gitignore", ".gitattributes", ".editorconfig",
            "dockerfile", "makefile", "jenkinsfile", "vagrantfile", "procfile"
    );

    /**
     * 判断文件是否应该交给语境检测器处理。
     *
     * @param file 当前文件
     * @return 代码或配置文件返回 true，否则返回 false
     */
    public boolean isSupported(VirtualFile file) {
        if (file == null || file.isDirectory()) {
            return false;
        }

        String fileName = file.getName().toLowerCase(Locale.ROOT);
        String extension = file.getExtension();
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (UNSUPPORTED_EXTENSIONS.contains(normalizedExtension)) {
            return false;
        }
        if (CODE_LIKE_FILENAMES.contains(fileName)) {
            return true;
        }

        FileType fileType = file.getFileType();
        if (fileType == FileTypes.PLAIN_TEXT || fileType == UnknownFileType.INSTANCE) {
            return false;
        }
        return !fileType.isBinary();
    }
}
