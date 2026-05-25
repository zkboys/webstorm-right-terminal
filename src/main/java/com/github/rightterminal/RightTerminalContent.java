package com.github.rightterminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.ShellStartupOptions;

import java.io.IOException;
import java.nio.file.Paths;

final class RightTerminalContent {
  private static final Logger LOG = Logger.getInstance(RightTerminalContent.class);
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

  static void sendCommand(Content content, String command, boolean immediately) {
    TerminalWidget terminalWidget = content.getUserData(TERMINAL_WIDGET_KEY);
    if (terminalWidget == null || command == null || command.isBlank()) {
      return;
    }

    terminalWidget.requestFocus();
    if (immediately) {
      terminalWidget.sendCommandToExecute(command);
      return;
    }

    terminalWidget.getTtyConnectorAccessor().executeWithTtyConnector(ttyConnector -> writeCommand(ttyConnector, command));
  }

  private static void writeCommand(TtyConnector ttyConnector, String command) {
    try {
      ttyConnector.write(command);
    }
    catch (IOException exception) {
      LOG.warn("Unable to write shortcut command to right terminal", exception);
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
