package com.github.rightterminal;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RightTerminalToolWindowFactory implements ToolWindowFactory, DumbAware {
  private static final String TOOL_WINDOW_TITLE = "Right Terminal";
  private static final String DEFAULT_TAB_NAME = "Local";

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    RightTerminalTabs tabs = new RightTerminalTabs(project, toolWindow);

    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.TABBED);
    ToolWindowContentUi.setAllowTabsReordering(toolWindow, true);
    toolWindow.setTitle(TOOL_WINDOW_TITLE);
    toolWindow.setStripeTitle(TOOL_WINDOW_TITLE);
    toolWindow.setTitleActions(List.of(new NewTerminalTabAction(tabs)));
    toolWindow.setAdditionalGearActions(new DefaultActionGroup(List.of(
        new NewTerminalTabAction(tabs),
        new RenameTerminalTabAction(tabs),
        new CloseTerminalTabAction(tabs)
    )));

    tabs.addTab();
  }

  private static final class RightTerminalTabs {
    private final Project project;
    private final ToolWindow toolWindow;

    private RightTerminalTabs(Project project, ToolWindow toolWindow) {
      this.project = project;
      this.toolWindow = toolWindow;
    }

    private void addTab() {
      Disposable tabDisposable = Disposer.newDisposable("Right Terminal Tab");
      Disposer.register(toolWindow.getDisposable(), tabDisposable);

      Content content = RightTerminalContent.create(project, tabDisposable, nextTabName());

      ContentManager contentManager = toolWindow.getContentManager();
      contentManager.addContent(content);
      contentManager.setSelectedContent(content);
      RightTerminalContent.requestFocus(content);
    }

    private void renameSelectedTab() {
      Content selectedContent = selectedContent();
      if (selectedContent == null) {
        return;
      }

      String currentName = selectedContent.getDisplayName();
      String newName = Messages.showInputDialog(
          project,
          "Enter a new terminal tab name:",
          "Rename Terminal Tab",
          Messages.getQuestionIcon(),
          currentName,
          null
      );
      if (newName == null) {
        return;
      }

      String trimmedName = newName.trim();
      if (trimmedName.isEmpty()) {
        return;
      }

      selectedContent.setDisplayName(trimmedName);
      selectedContent.setTabName(trimmedName);
      selectedContent.setToolwindowTitle(trimmedName);
    }

    private void closeSelectedTab() {
      Content selectedContent = selectedContent();
      if (selectedContent != null) {
        toolWindow.getContentManager().removeContent(selectedContent, true);
      }
    }

    private boolean hasSelectedTab() {
      return selectedContent() != null;
    }

    private Content selectedContent() {
      return toolWindow.getContentManager().getSelectedContent();
    }

    private String nextTabName() {
      ContentManager contentManager = toolWindow.getContentManager();
      if (contentManager.findContent(DEFAULT_TAB_NAME) == null) {
        return DEFAULT_TAB_NAME;
      }

      int index = 2;
      while (contentManager.findContent(DEFAULT_TAB_NAME + " (" + index + ")") != null) {
        index++;
      }
      return DEFAULT_TAB_NAME + " (" + index + ")";
    }
  }

  private static final class NewTerminalTabAction extends DumbAwareAction {
    private final RightTerminalTabs tabs;

    private NewTerminalTabAction(RightTerminalTabs tabs) {
      super("New Terminal Tab", "Open a new right terminal tab", AllIcons.General.Add);
      this.tabs = tabs;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      tabs.addTab();
    }
  }

  private static final class RenameTerminalTabAction extends DumbAwareAction {
    private final RightTerminalTabs tabs;

    private RenameTerminalTabAction(RightTerminalTabs tabs) {
      super("Rename Terminal Tab", "Rename the selected right terminal tab", AllIcons.Actions.Edit);
      this.tabs = tabs;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(tabs.hasSelectedTab());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      tabs.renameSelectedTab();
    }
  }

  private static final class CloseTerminalTabAction extends DumbAwareAction {
    private final RightTerminalTabs tabs;

    private CloseTerminalTabAction(RightTerminalTabs tabs) {
      super("Close Terminal Tab", "Close the selected right terminal tab", AllIcons.Actions.Close);
      this.tabs = tabs;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(tabs.hasSelectedTab());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      tabs.closeSelectedTab();
    }
  }
}
