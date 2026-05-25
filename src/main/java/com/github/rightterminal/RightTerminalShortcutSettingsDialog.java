package com.github.rightterminal;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

final class RightTerminalShortcutSettingsDialog extends DialogWrapper {
  private final DefaultListModel<RightTerminalShortcutSettings.ShortcutCommand> commandModel = new DefaultListModel<>();
  private final JBList<RightTerminalShortcutSettings.ShortcutCommand> commandList = new JBList<>(commandModel);
  private final JBTextField nameField = new JBTextField();
  private final JBTextArea commandField = new JBTextArea(6, 40);
  private final JBTextArea descriptionField = new JBTextArea(4, 40);
  private final JCheckBox newTabCheckBox = new JCheckBox("New tab");
  private final JCheckBox immediatelyCheckBox = new JCheckBox("Run immediately");

  private boolean updatingFields;

  RightTerminalShortcutSettingsDialog(Project project) {
    super(project, true);
    setTitle("Right Terminal Shortcuts");
    setResizable(true);
    setOKButtonText("Save");

    for (RightTerminalShortcutSettings.ShortcutCommand command : RightTerminalShortcutSettings.getInstance().getCommands()) {
      commandModel.addElement(command);
    }

    configureList();
    configureFields();
    init();

    if (!commandModel.isEmpty()) {
      commandList.setSelectedIndex(0);
    }
    updateEditorEnabled();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    Splitter splitter = new Splitter(false, 0.34f);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setFirstComponent(createListPanel());
    splitter.setSecondComponent(createEditorPanel());

    JPanel panel = new JBPanel<>(new BorderLayout());
    panel.setPreferredSize(JBUI.size(760, 460));
    panel.add(splitter, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    for (int i = 0; i < commandModel.size(); i++) {
      RightTerminalShortcutSettings.ShortcutCommand command = commandModel.get(i);
      if (command.name == null || command.name.trim().isEmpty()) {
        commandList.setSelectedIndex(i);
        return new ValidationInfo("Name is required.", nameField);
      }
      if (command.command == null || command.command.trim().isEmpty()) {
        commandList.setSelectedIndex(i);
        return new ValidationInfo("Command is required.", commandField);
      }
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    RightTerminalShortcutSettings.getInstance().setCommands(getCommands());
    super.doOKAction();
  }

  private JPanel createListPanel() {
    JPanel decoratedList = ToolbarDecorator.createDecorator(commandList)
        .setAddAction(button -> addCommand())
        .setRemoveAction(button -> removeSelectedCommand())
        .setMoveUpAction(button -> moveSelectedCommand(-1))
        .setMoveDownAction(button -> moveSelectedCommand(1))
        .setEditAction(button -> nameField.requestFocusInWindow())
        .setRemoveActionUpdater(event -> commandList.getSelectedIndex() >= 0)
        .setEditActionUpdater(event -> commandList.getSelectedIndex() >= 0)
        .setMoveUpActionUpdater(event -> commandList.getSelectedIndex() > 0)
        .setMoveDownActionUpdater(event -> {
          int selectedIndex = commandList.getSelectedIndex();
          return selectedIndex >= 0 && selectedIndex < commandModel.size() - 1;
        })
        .createPanel();
    decoratedList.setMinimumSize(JBUI.size(220, 260));
    return decoratedList;
  }

  private JComponent createEditorPanel() {
    JPanel checkBoxPanel = new JBPanel<>(new BorderLayout(JBUI.scale(12), 0));
    checkBoxPanel.add(newTabCheckBox, BorderLayout.WEST);
    checkBoxPanel.add(immediatelyCheckBox, BorderLayout.CENTER);

    JPanel formPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Name:", nameField)
        .addLabeledComponentFillVertically("Command:", new JBScrollPane(commandField))
        .addLabeledComponentFillVertically("Description:", new JBScrollPane(descriptionField))
        .addLabeledComponent("", checkBoxPanel)
        .getPanel();
    formPanel.setBorder(JBUI.Borders.empty(0, 16, 0, 0));
    formPanel.setMinimumSize(JBUI.size(360, 260));
    return formPanel;
  }

  private void configureList() {
    commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    commandList.setEmptyText("No shortcut commands");
    commandList.setCellRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(
          javax.swing.JList<? extends RightTerminalShortcutSettings.ShortcutCommand> list,
          RightTerminalShortcutSettings.ShortcutCommand value,
          int index,
          boolean selected,
          boolean hasFocus
      ) {
        if (value == null) {
          return;
        }

        append(value.displayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        String description = emptyToNull(value.description);
        if (description != null) {
          append("  " + singleLine(description), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
        }
      }
    });
    commandList.addListSelectionListener(event -> {
      if (!event.getValueIsAdjusting()) {
        loadSelectedCommand();
      }
    });
    commandList.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent event) {
        if (event.getClickCount() == 2 && commandList.getSelectedIndex() >= 0) {
          nameField.requestFocusInWindow();
        }
      }
    });
    new ListSpeedSearch<>(commandList, command -> command == null ? "" : command.displayName());
  }

  private void configureFields() {
    nameField.setMargin(JBUI.insets(0, 8));
    commandField.setLineWrap(true);
    commandField.setWrapStyleWord(false);
    commandField.setMargin(JBUI.insets(8, 8));
    descriptionField.setLineWrap(true);
    descriptionField.setWrapStyleWord(true);
    descriptionField.setMargin(JBUI.insets(8, 8));

    DocumentListener documentListener = new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent event) {
        storeSelectedCommand();
      }

      @Override
      public void removeUpdate(DocumentEvent event) {
        storeSelectedCommand();
      }

      @Override
      public void changedUpdate(DocumentEvent event) {
        storeSelectedCommand();
      }
    };
    nameField.getDocument().addDocumentListener(documentListener);
    commandField.getDocument().addDocumentListener(documentListener);
    descriptionField.getDocument().addDocumentListener(documentListener);
    newTabCheckBox.addActionListener(event -> storeSelectedCommand());
    immediatelyCheckBox.addActionListener(event -> storeSelectedCommand());
  }

  private void addCommand() {
    RightTerminalShortcutSettings.ShortcutCommand command = new RightTerminalShortcutSettings.ShortcutCommand();
    command.name = nextCommandName();
    command.immediately = true;
    commandModel.addElement(command);
    commandList.setSelectedIndex(commandModel.size() - 1);
    nameField.requestFocusInWindow();
    nameField.selectAll();
  }

  private void removeSelectedCommand() {
    int selectedIndex = commandList.getSelectedIndex();
    if (selectedIndex < 0) {
      return;
    }

    commandModel.remove(selectedIndex);
    if (!commandModel.isEmpty()) {
      commandList.setSelectedIndex(Math.min(selectedIndex, commandModel.size() - 1));
    }
    updateEditorEnabled();
  }

  private void moveSelectedCommand(int offset) {
    int selectedIndex = commandList.getSelectedIndex();
    int targetIndex = selectedIndex + offset;
    if (selectedIndex < 0 || targetIndex < 0 || targetIndex >= commandModel.size()) {
      return;
    }

    RightTerminalShortcutSettings.ShortcutCommand command = commandModel.remove(selectedIndex);
    commandModel.add(targetIndex, command);
    commandList.setSelectedIndex(targetIndex);
  }

  private void loadSelectedCommand() {
    RightTerminalShortcutSettings.ShortcutCommand command = commandList.getSelectedValue();
    updatingFields = true;
    try {
      nameField.setText(command == null ? "" : nullToEmpty(command.name));
      commandField.setText(command == null ? "" : nullToEmpty(command.command));
      descriptionField.setText(command == null ? "" : nullToEmpty(command.description));
      newTabCheckBox.setSelected(command != null && command.newTab);
      immediatelyCheckBox.setSelected(command == null || command.immediately);
    }
    finally {
      updatingFields = false;
    }
    updateEditorEnabled();
  }

  private void storeSelectedCommand() {
    if (updatingFields) {
      return;
    }

    int selectedIndex = commandList.getSelectedIndex();
    if (selectedIndex < 0) {
      return;
    }

    RightTerminalShortcutSettings.ShortcutCommand command = commandModel.get(selectedIndex);
    command.name = nameField.getText();
    command.command = commandField.getText();
    command.description = descriptionField.getText();
    command.newTab = newTabCheckBox.isSelected();
    command.immediately = immediatelyCheckBox.isSelected();
    commandModel.set(selectedIndex, command);
  }

  private void updateEditorEnabled() {
    boolean enabled = commandList.getSelectedIndex() >= 0;
    nameField.setEnabled(enabled);
    commandField.setEnabled(enabled);
    descriptionField.setEnabled(enabled);
    newTabCheckBox.setEnabled(enabled);
    immediatelyCheckBox.setEnabled(enabled);
  }

  private List<RightTerminalShortcutSettings.ShortcutCommand> getCommands() {
    List<RightTerminalShortcutSettings.ShortcutCommand> commands = new ArrayList<>();
    for (int i = 0; i < commandModel.size(); i++) {
      commands.add(commandModel.get(i).copy());
    }
    return commands;
  }

  private String nextCommandName() {
    int index = commandModel.size() + 1;
    String candidate = "Command " + index;
    while (containsCommandName(candidate)) {
      index++;
      candidate = "Command " + index;
    }
    return candidate;
  }

  private boolean containsCommandName(String name) {
    for (int i = 0; i < commandModel.size(); i++) {
      if (name.equals(commandModel.get(i).displayName())) {
        return true;
      }
    }
    return false;
  }

  private static String singleLine(String value) {
    return value.replace('\n', ' ').replace('\r', ' ').trim();
  }

  private static String emptyToNull(String value) {
    String trimmed = nullToEmpty(value).trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
