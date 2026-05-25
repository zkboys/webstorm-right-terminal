package com.github.rightterminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.toolWindow.ToolWindowSplitContentProvider;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

public final class RightTerminalSplitContentProvider implements ToolWindowSplitContentProvider {
  @Override
  public @NotNull Content createContentCopy(@NotNull Project project, @NotNull Content content) {
    Disposable splitDisposable = Disposer.newDisposable("Right Terminal Split Tab");
    return RightTerminalContent.create(project, splitDisposable, content.getDisplayName());
  }
}
