package com.github.rightterminal;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
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
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

public final class RightTerminalToolWindowFactory implements ToolWindowFactory, DumbAware {
  static final String TOOL_WINDOW_TITLE = "Right Terminal";
  private static final String DEFAULT_TAB_NAME = "Local";

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    RightTerminalTabs tabs = new RightTerminalTabs(project, toolWindow);

    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.TABBED);
    ToolWindowContentUi.setAllowTabsReordering(toolWindow, true);
    toolWindow.setTitle(TOOL_WINDOW_TITLE);
    toolWindow.setStripeTitle(TOOL_WINDOW_TITLE);
    toolWindow.setTitleActions(List.of(
        new NewTerminalTabAction(tabs, null),
        new ShortcutCommandsAction(tabs, null)
    ));
    toolWindow.setAdditionalGearActions(new DefaultActionGroup(List.of(
        new NewTerminalTabAction(tabs, null),
        new RenameTerminalTabAction(tabs, null),
        new CloseTerminalTabAction(tabs, null)
    )));

    tabs.addTab();
  }

  static ActionGroup createContentActions(Project project, Content content) {
    RightTerminalTabs tabs = new RightTerminalTabs(project, null);
    return tabs.createContentActions(content);
  }

  static void sendTextToRightTerminal(Project project, String text) {
    if (text == null || text.isBlank()) {
      return;
    }

    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_TITLE);
    if (toolWindow == null) {
      return;
    }

    toolWindow.activate(() -> sendTextToSelectedContent(project, toolWindow, text), true);
  }

  private static void sendTextToSelectedContent(Project project, ToolWindow toolWindow, String text) {
    Content content = toolWindow.getContentManager().getSelectedContent();
    if (content == null) {
      content = new RightTerminalTabs(project, toolWindow).addTab();
    }
    RightTerminalContent.sendCommand(content, text, false);
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
      installContentActions(content);

      ContentManager contentManager = toolWindow.getContentManager();
      contentManager.addContent(content);
      contentManager.setSelectedContent(content);
      RightTerminalContent.requestFocus(content);
      return content;
    }

    private ActionGroup createContentActions(Content content) {
      return new DefaultActionGroup(List.of(
          new NewTerminalTabAction(this, content),
          new ShortcutCommandsAction(this, content),
          new TerminalTabOptionsAction(this, content)
      ));
    }

    private void installContentActions(Content content) {
      content.setActions(createContentActions(content), ActionPlaces.TOOLWINDOW_CONTENT, content.getComponent());
    }

    private Content addTab(Content sourceContent) {
      ContentManager contentManager = contentManager(sourceContent);
      if (contentManager == null) {
        return toolWindow == null ? null : addTab();
      }
      return addTab(contentManager, sourceContent, DEFAULT_TAB_NAME);
    }

    private Content addTab(Content sourceContent, String preferredTabName) {
      ContentManager contentManager = contentManager(sourceContent);
      if (contentManager == null) {
        return toolWindow == null ? null : addTab(preferredTabName);
      }
      return addTab(contentManager, sourceContent, preferredTabName);
    }

    private Content addTab(ContentManager contentManager, Content sourceContent, String preferredTabName) {
      Disposable tabDisposable = Disposer.newDisposable("Right Terminal Tab");
      Disposer.register(contentManager, tabDisposable);

      Content content = RightTerminalContent.create(project, tabDisposable, nextTabName(contentManager, preferredTabName));
      installContentActions(content);
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

    private void runShortcut(Content sourceContent, RightTerminalShortcutSettings.ShortcutCommand command) {
      if (command.command == null || command.command.isBlank()) {
        return;
      }

      Content targetContent = command.newTab ? addTab(sourceContent, command.displayName()) : selectedContent(sourceContent);
      if (targetContent == null) {
        targetContent = addTab(sourceContent);
      }
      if (targetContent == null) {
        return;
      }

      Content content = targetContent;
      ApplicationManager.getApplication().invokeLater(
          () -> RightTerminalContent.sendCommand(content, command.command, command.immediately),
          ModalityState.nonModal()
      );
    }

    private void renameSelectedTab() {
      Content selectedContent = selectedContent();
      renameTab(selectedContent);
    }

    private void renameTab(Content selectedContent) {
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

    private void closeTab(Content content) {
      ContentManager contentManager = contentManager(content);
      if (contentManager != null && content != null) {
        contentManager.removeContent(content, true);
      }
    }

    private boolean hasSelectedTab() {
      return selectedContent() != null;
    }

    private Content selectedContent() {
      return toolWindow.getContentManager().getSelectedContent();
    }

    private Content selectedContent(Content sourceContent) {
      ContentManager contentManager = contentManager(sourceContent);
      return contentManager == null ? null : contentManager.getSelectedContent();
    }

    private String nextTabName(String preferredName) {
      return nextTabName(toolWindow.getContentManager(), preferredName);
    }

    private String nextTabName(ContentManager contentManager, String preferredName) {
      String baseName = preferredName == null ? "" : preferredName.trim();
      if (baseName.isEmpty()) {
        baseName = DEFAULT_TAB_NAME;
      }

      if (contentManager.findContent(baseName) == null) {
        return baseName;
      }

      int index = 2;
      while (contentManager.findContent(baseName + " (" + index + ")") != null) {
        index++;
      }
      return baseName + " (" + index + ")";
    }

    private ContentManager contentManager(Content content) {
      if (content != null && content.getManager() != null) {
        return content.getManager();
      }
      return toolWindow == null ? null : toolWindow.getContentManager();
    }
  }

  private static final class NewTerminalTabAction extends DumbAwareAction {
    private final RightTerminalTabs tabs;
    private final Content content;

    private NewTerminalTabAction(RightTerminalTabs tabs, Content content) {
      super("New Terminal Tab", "Open a new right terminal tab", AllIcons.General.Add);
      this.tabs = tabs;
      this.content = content;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (content == null) {
        tabs.addTab();
      }
      else {
        tabs.addTab(content);
      }
    }
  }

  private static final class ShortcutCommandsAction extends DumbAwareAction {
    private final RightTerminalTabs tabs;
    private final Content content;

    private ShortcutCommandsAction(RightTerminalTabs tabs, Content content) {
      super("Shortcut Commands", "Run a right terminal shortcut command", AllIcons.General.ChevronDown);
      this.tabs = tabs;
      this.content = content;
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
          group.add(new RunShortcutCommandAction(tabs, content, command));
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
    private final Content content;
    private final RightTerminalShortcutSettings.ShortcutCommand command;

    private RunShortcutCommandAction(RightTerminalTabs tabs, Content content, RightTerminalShortcutSettings.ShortcutCommand command) {
      super(command.displayName(), nullIfBlank(command.description), null);
      this.tabs = tabs;
      this.content = content;
      this.command = command.copy();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (content == null) {
        tabs.runShortcut(command);
      }
      else {
        tabs.runShortcut(content, command);
      }
    }
  }

  private static final class TerminalTabOptionsAction extends DefaultActionGroup {
    private TerminalTabOptionsAction(RightTerminalTabs tabs, Content content) {
      super("Terminal Tab Options", true);
      getTemplatePresentation().setDescription("Manage this right terminal tab");
      getTemplatePresentation().setIcon(AllIcons.Actions.More);
      add(new RenameTerminalTabAction(tabs, content));
      add(new CloseTerminalTabAction(tabs, content));
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
    private final Content content;

    private RenameTerminalTabAction(RightTerminalTabs tabs, Content content) {
      super("Rename Terminal Tab", "Rename the selected right terminal tab", AllIcons.Actions.Edit);
      this.tabs = tabs;
      this.content = content;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(content != null || tabs.hasSelectedTab());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (content == null) {
        tabs.renameSelectedTab();
      }
      else {
        tabs.renameTab(content);
      }
    }
  }

  private static final class CloseTerminalTabAction extends DumbAwareAction {
    private final RightTerminalTabs tabs;
    private final Content content;

    private CloseTerminalTabAction(RightTerminalTabs tabs, Content content) {
      super("Close Terminal Tab", "Close the selected right terminal tab", AllIcons.Actions.Close);
      this.tabs = tabs;
      this.content = content;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(content != null || tabs.hasSelectedTab());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (content == null) {
        tabs.closeSelectedTab();
      }
      else {
        tabs.closeTab(content);
      }
    }
  }

  private static String nullIfBlank(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
