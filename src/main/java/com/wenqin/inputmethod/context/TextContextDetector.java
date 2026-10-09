package com.wenqin.inputmethod.context;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * 根据编辑器、Psi 语法树和 VCS 文档标记识别当前输入语境。
 */
public final class TextContextDetector {
    private final CodeFileClassifier codeFileClassifier;

    /**
     * 创建语境检测器。
     */
    public TextContextDetector() {
        this.codeFileClassifier = new CodeFileClassifier();
    }

    /**
     * 识别光标当前所在的输入语境。
     *
     * @param editor 当前编辑器
     * @return 当前输入语境
     */
    public InputContext detect(Editor editor) {
        Document document = editor.getDocument();
        if (CommitMessage.isCommitMessage(document)) {
            return InputContext.COMMIT_MESSAGE;
        }

        Project project = editor.getProject();
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (project == null || !codeFileClassifier.isSupported(file)) {
            return InputContext.UNSUPPORTED;
        }

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        psiDocumentManager.commitDocument(document);
        PsiFile psiFile = psiDocumentManager.getPsiFile(document);
        if (psiFile == null) {
            return InputContext.CODE;
        }

        int caretOffset = editor.getCaretModel().getOffset();
        PsiElement element = findElementAtCaret(psiFile, caretOffset, document.getTextLength());
        return isComment(element) ? InputContext.COMMENT : InputContext.CODE;
    }

    /**
     * 查找光标位置的语法元素；光标位于文件末尾时同时检查前一个字符。
     *
     * @param psiFile PSI 文件
     * @param caretOffset 光标偏移量
     * @param documentLength 文档长度
     * @return 语法元素，找不到时返回 null
     */
    private PsiElement findElementAtCaret(PsiFile psiFile, int caretOffset, int documentLength) {
        // 先检查光标位置；行注释末尾常落在注释节点边界，当前位置可能是换行空白。
        PsiElement elementAtCaret = psiFile.findElementAt(Math.min(caretOffset, documentLength));
        if (isComment(elementAtCaret)) {
            return elementAtCaret;
        }

        // 再检查前一个字符，覆盖刚输入 //、# 或文档注释起始符的场景。
        if (caretOffset > 0) {
            PsiElement elementBeforeCaret = psiFile.findElementAt(caretOffset - 1);
            if (isComment(elementBeforeCaret)) {
                return elementBeforeCaret;
            }
        }
        return elementAtCaret;
    }

    /**
     * 判断语法元素是否属于注释节点。
     *
     * @param element 待判断元素
     * @return 注释节点返回 true
     */
    private boolean isComment(PsiElement element) {
        return element instanceof PsiComment || PsiTreeUtil.getParentOfType(element, PsiComment.class) != null;
    }
}
