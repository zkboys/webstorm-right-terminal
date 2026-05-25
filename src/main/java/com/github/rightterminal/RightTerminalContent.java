package com.github.rightterminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.ShellStartupOptions;

import java.nio.file.Paths;

final class RightTerminalContent {
  private static final Key<TerminalWidget> TERMINAL_WIDGET_KEY = Key.create("RightTerminal.TerminalWidget");

  private RightTerminalContent() {
  }

  static Content create(Project project, Disposable parentDisposable, String tabName) {
    TerminalWidget terminalWidget = createTerminal(project, parentDisposable);
    Content content = ContentFactory.getInstance().createContent(terminalWidget.getComponent(), tabName, false);
    content.setCloseable(true);
    content.setDisposer(parentDisposable);
    content.setPreferredFocusableComponent(terminalWidget.getPreferredFocusableComponent());
    content.putUserData(TERMINAL_WIDGET_KEY, terminalWidget);
    return content;
  }

  static void requestFocus(Content content) {
    TerminalWidget terminalWidget = content.getUserData(TERMINAL_WIDGET_KEY);
    if (terminalWidget != null) {
      terminalWidget.requestFocus();
    }
  }

  private static TerminalWidget createTerminal(Project project, Disposable parentDisposable) {
    LocalTerminalDirectRunner runner = LocalTerminalDirectRunner.createTerminalRunner(project);
    ShellStartupOptions options = new ShellStartupOptions.Builder()
        .workingDirectory(projectDirectory(project))
        .build();

    return runner.startShellTerminalWidget(parentDisposable, options, true);
  }

  private static String projectDirectory(Project project) {
    String basePath = project.getBasePath();
    if (basePath != null && !basePath.isBlank()) {
      return basePath;
    }
    return Paths.get(System.getProperty("user.home")).toString();
  }
}
