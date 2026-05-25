package com.github.rightterminal;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.util.ArrayList;
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
    toolWindow.setTitleActions(List.of(
        new NewTerminalTabAction(tabs),
        new ShortcutCommandsAction(tabs)
    ));
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

    private Content addTab() {
      return addTab(DEFAULT_TAB_NAME);
    }

    private Content addTab(String preferredTabName) {
      Disposable tabDisposable = Disposer.newDisposable("Right Terminal Tab");
      Disposer.register(toolWindow.getDisposable(), tabDisposable);

      Content content = RightTerminalContent.create(project, tabDisposable, nextTabName(preferredTabName));

      ContentManager contentManager = toolWindow.getContentManager();
      contentManager.addContent(content);
      contentManager.setSelectedContent(content);
      RightTerminalContent.requestFocus(content);
      return content;
    }

    private void runShortcut(RightTerminalShortcutSettings.ShortcutCommand command) {
      if (command.command == null || command.command.isBlank()) {
        return;
      }

      Content targetContent = command.newTab ? addTab(command.displayName()) : selectedContent();
      if (targetContent == null) {
        targetContent = addTab();
      }

      Content content = targetContent;
      ApplicationManager.getApplication().invokeLater(
          () -> RightTerminalContent.sendCommand(content, command.command, command.immediately),
          ModalityState.nonModal()
      );
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

    private String nextTabName(String preferredName) {
      String baseName = preferredName == null ? "" : preferredName.trim();
      if (baseName.isEmpty()) {
        baseName = DEFAULT_TAB_NAME;
      }

      ContentManager contentManager = toolWindow.getContentManager();
      if (contentManager.findContent(baseName) == null) {
        return baseName;
      }

      int index = 2;
      while (contentManager.findContent(baseName + " (" + index + ")") != null) {
        index++;
      }
      return baseName + " (" + index + ")";
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

  private static final class ShortcutCommandsAction extends DumbAwareAction {
    private final RightTerminalTabs tabs;

    private ShortcutCommandsAction(RightTerminalTabs tabs) {
      super("Shortcut Commands", "Run a right terminal shortcut command", AllIcons.General.ChevronDown);
      this.tabs = tabs;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DefaultActionGroup group = new DefaultActionGroup();
      List<RightTerminalShortcutSettings.ShortcutCommand> commands = validCommands();
      if (commands.isEmpty()) {
        group.add(new DisabledMenuAction("No shortcut commands configured"));
      }
      else {
        for (RightTerminalShortcutSettings.ShortcutCommand command : commands) {
          group.add(new RunShortcutCommandAction(tabs, command));
        }
      }
      group.addSeparator();
      group.add(new ManageShortcutCommandsAction(tabs.project));

      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
          null,
          group,
          e.getDataContext(),
          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
          true
      );
      InputEvent inputEvent = e.getInputEvent();
      Component component = inputEvent == null ? null : inputEvent.getComponent();
      if (component != null) {
        popup.showUnderneathOf(component);
      }
      else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }

    private List<RightTerminalShortcutSettings.ShortcutCommand> validCommands() {
      List<RightTerminalShortcutSettings.ShortcutCommand> commands = new ArrayList<>();
      for (RightTerminalShortcutSettings.ShortcutCommand command : RightTerminalShortcutSettings.getInstance().getCommands()) {
        if (command.command != null && !command.command.isBlank()) {
          commands.add(command);
        }
      }
      return commands;
    }
  }

  private static final class RunShortcutCommandAction extends DumbAwareAction {
    private final RightTerminalTabs tabs;
    private final RightTerminalShortcutSettings.ShortcutCommand command;

    private RunShortcutCommandAction(RightTerminalTabs tabs, RightTerminalShortcutSettings.ShortcutCommand command) {
      super(command.displayName(), nullIfBlank(command.description), null);
      this.tabs = tabs;
      this.command = command.copy();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      tabs.runShortcut(command);
    }
  }

  private static final class ManageShortcutCommandsAction extends DumbAwareAction {
    private final Project project;

    private ManageShortcutCommandsAction(Project project) {
      super("Settings", "Manage right terminal shortcut commands", AllIcons.General.Settings);
      this.project = project;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      new RightTerminalShortcutSettingsDialog(project).show();
    }
  }

  private static final class DisabledMenuAction extends DumbAwareAction {
    private DisabledMenuAction(String text) {
      super(text);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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

  private static String nullIfBlank(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
