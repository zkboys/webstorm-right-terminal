package com.github.rightterminal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class RightTerminalSendToTerminalAction extends DumbAwareAction {
  public RightTerminalSendToTerminalAction() {
    super("Send to Right Terminal");
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    VirtualFile file = file(event, editor);
    event.getPresentation().setEnabledAndVisible(project != null && editor != null && file != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    VirtualFile file = file(event, editor);
    if (project == null || editor == null || file == null) {
      return;
    }

    RightTerminalToolWindowFactory.sendTextToRightTerminal(project, targetText(project, editor, file));
  }

  private static VirtualFile file(AnActionEvent event, Editor editor) {
    VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file != null || editor == null) {
      return file;
    }
    return editor.getVirtualFile();
  }

  private static String targetText(Project project, Editor editor, VirtualFile file) {
    String path = projectRelativePath(project, file);
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      return path;
    }

    LogicalPosition start = editor.offsetToLogicalPosition(selectionModel.getSelectionStart());
    LogicalPosition end = editor.offsetToLogicalPosition(selectionModel.getSelectionEnd());
    return path + ":" + position(start) + "-" + position(end);
  }

  private static String position(LogicalPosition position) {
    return (position.line + 1) + ":" + (position.column + 1);
  }

  private static String projectRelativePath(Project project, VirtualFile file) {
    String basePath = project.getBasePath();
    if (basePath == null || basePath.isBlank()) {
      return file.getPath();
    }

    try {
      Path base = Paths.get(basePath).toAbsolutePath().normalize();
      Path target = Paths.get(file.getPath()).toAbsolutePath().normalize();
      return base.relativize(target).toString().replace('\\', '/');
    }
    catch (IllegalArgumentException exception) {
      return file.getPath();
    }
  }
}
