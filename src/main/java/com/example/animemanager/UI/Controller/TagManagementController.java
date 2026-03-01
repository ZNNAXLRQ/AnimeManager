package com.example.animemanager.UI.Controller;

import com.example.animemanager.Entity.Tag;
import com.example.animemanager.Repository.TagRepository;
import com.example.animemanager.Service.SubjectService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class TagManagementController implements Initializable {

    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private SubjectService subjectService;

    @FXML private ListView<Tag> tagListView;
    @FXML private TextField searchField;
    @FXML private TextField newTagField;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button clearAllButton;

    private ObservableList<Tag> allTags = FXCollections.observableArrayList();
    private Set<Tag> selectedTags = new HashSet<>();
    private List<Tag> preSelectedTags; // 保存传入的原始标签列表（用于初始化）

    public void setPreSelectedTags(List<Tag> tags) {
        this.preSelectedTags = tags;
        // 在加载完所有标签后，根据 ID 重新设置选中项
        refreshSelectionFromPreSelected();
    }

    private void refreshSelectionFromPreSelected() {
        selectedTags.clear();
        if (preSelectedTags != null) {
            // 遍历 allTags，如果其 ID 在 preSelectedTags 的 ID 集合中，则加入 selectedTags
            Set<Long> preSelectedIds = preSelectedTags.stream()
                    .map(Tag::getTagId)
                    .collect(Collectors.toSet());
            for (Tag tag : allTags) {
                if (preSelectedIds.contains(tag.getTagId())) {
                    selectedTags.add(tag);
                }
            }
        }
        tagListView.refresh();
    }

    public List<Tag> getSelectedTags() {
        return new ArrayList<>(selectedTags);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 自定义单元格：每个标签带复选框
        tagListView.setCellFactory(lv -> new ListCell<Tag>() {
            private final CheckBox checkBox = new CheckBox();

            {
                checkBox.setOnAction(e -> {
                    Tag tag = getItem();
                    if (tag != null) {
                        if (checkBox.isSelected()) {
                            selectedTags.add(tag);
                        } else {
                            selectedTags.remove(tag);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Tag item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    checkBox.setText(item.getName());
                    checkBox.setSelected(selectedTags.contains(item));
                    setGraphic(checkBox);
                }
            }
        });

        tagListView.setItems(allTags);

        // 搜索过滤
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                tagListView.setItems(allTags);
            } else {
                List<Tag> filtered = allTags.stream()
                        .filter(tag -> tag.getName().toLowerCase().contains(newVal.toLowerCase()))
                        .collect(Collectors.toList());
                tagListView.setItems(FXCollections.observableArrayList(filtered));
            }
        });

        // 添加标签
        addButton.setOnAction(e -> {
            String name = newTagField.getText().trim();
            if (name.isEmpty()) {
                showAlert("标签名不能为空");
                return;
            }
            Optional<Tag> existing = tagRepository.findByName(name);
            if (existing.isPresent()) {
                showAlert("标签已存在");
                return;
            }
            Tag newTag = new Tag();
            newTag.setName(name);
            newTag.setCount(0);
            tagRepository.save(newTag);
            allTags.add(newTag);
            newTagField.clear();
            selectedTags.add(newTag); // 自动勾选新标签
            tagListView.refresh();
        });

        // 编辑标签
        editButton.setOnAction(e -> {
            Tag selected = tagListView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("请先选择一个标签");
                return;
            }
            TextInputDialog dialog = new TextInputDialog(selected.getName());
            dialog.setTitle("编辑标签");
            dialog.setHeaderText("修改标签名称");
            dialog.setContentText("新名称:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(newName -> {
                newName = newName.trim();
                if (newName.isEmpty()) {
                    showAlert("名称不能为空");
                    return;
                }
                Optional<Tag> conflict = tagRepository.findByName(newName);
                if (conflict.isPresent() && !conflict.get().getTagId().equals(selected.getTagId())) {
                    showAlert("标签名已存在");
                    return;
                }
                selected.setName(newName);
                tagRepository.save(selected);
                // 编辑后刷新列表，选中状态不变（selected 对象仍在 selectedTags 中）
                refreshList();
            });
        });

        // 删除标签
        deleteButton.setOnAction(e -> {
            Tag selected = tagListView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("请先选择一个标签");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "确定删除标签 \"" + selected.getName() + "\" 吗？\n此操作将从所有作品中移除该标签。",
                    ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    try {
                        subjectService.deleteTagAndRelations(selected.getTagId());  // 调用服务方法
                        allTags.remove(selected);
                        selectedTags.remove(selected);
                        tagListView.refresh();
                    } catch (Exception ex) {
                        showAlert("删除失败：" + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
            });
        });

        // 清除所有勾选
        if (clearAllButton != null) {
            clearAllButton.setOnAction(e -> {
                selectedTags.clear();
                tagListView.refresh();
            });
        }

        // 保存并关闭
        saveButton.setOnAction(e -> {
            ((Stage) saveButton.getScene().getWindow()).close();
        });

        // 取消：直接关闭，放弃更改
        cancelButton.setOnAction(e -> {
            ((Stage) cancelButton.getScene().getWindow()).close();
        });

        loadAllTags();
    }

    private void loadAllTags() {
        List<Tag> tags = tagRepository.findAll();
        allTags.setAll(tags);
        // 如果有预选标签，在加载后重新建立选中集合
        if (preSelectedTags != null) {
            refreshSelectionFromPreSelected();
        }
    }

    private void refreshList() {
        // 重新加载数据库，保持 allTags 最新
        loadAllTags();
        // 重新应用搜索过滤
        searchField.setText(searchField.getText());
        // 注意：loadAllTags 中已经调用了 refreshSelectionFromPreSelected，所以无需额外处理
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        alert.showAndWait();
    }
}