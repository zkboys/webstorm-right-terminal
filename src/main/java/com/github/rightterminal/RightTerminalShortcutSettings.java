package com.github.rightterminal;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Service(Service.Level.APP)
@State(
    name = "RightTerminalShortcutSettings",
    storages = @Storage("right-terminal.xml"),
    category = SettingsCategory.TOOLS
)
public final class RightTerminalShortcutSettings implements PersistentStateComponent<RightTerminalShortcutSettings.State> {
  private State state = new State();

  public static RightTerminalShortcutSettings getInstance() {
    return ApplicationManager.getApplication().getService(RightTerminalShortcutSettings.class);
  }

  @Override
  public @NotNull State getState() {
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
    if (this.state.commands == null) {
      this.state.commands = new ArrayList<>();
    }
  }

  public List<ShortcutCommand> getCommands() {
    List<ShortcutCommand> result = new ArrayList<>();
    for (ShortcutCommand command : state.commands) {
      if (command != null) {
        result.add(command.copy());
      }
    }
    return result;
  }

  public void setCommands(List<ShortcutCommand> commands) {
    state.commands = new ArrayList<>();
    for (ShortcutCommand command : commands) {
      if (command != null) {
        state.commands.add(command.copy());
      }
    }
    ApplicationManager.getApplication().saveSettings();
  }

  public static final class State {
    public List<ShortcutCommand> commands = new ArrayList<>();
  }

  public static final class ShortcutCommand {
    public String name = "";
    public String command = "";
    public String description = "";
    public boolean newTab;
    public boolean immediately = true;

    public ShortcutCommand copy() {
      ShortcutCommand copy = new ShortcutCommand();
      copy.name = nullToEmpty(name);
      copy.command = nullToEmpty(command);
      copy.description = nullToEmpty(description);
      copy.newTab = newTab;
      copy.immediately = immediately;
      return copy;
    }

    public String displayName() {
      String trimmedName = nullToEmpty(name).trim();
      return trimmedName.isEmpty() ? "Unnamed Command" : trimmedName;
    }

    @Override
    public String toString() {
      return displayName();
    }

    private static String nullToEmpty(String value) {
      return value == null ? "" : value;
    }
  }
}
