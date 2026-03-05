package com.example.animemanager.Controller;

import com.example.animemanager.Entity.*;
import com.example.animemanager.Entity.Character;
import com.example.animemanager.Main;
import com.example.animemanager.Repository.*;
import com.example.animemanager.Service.ScoreCalculatorService;
import com.example.animemanager.Service.SubjectService;
import com.example.animemanager.Util.LogCollector;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class SubjectController implements Initializable {

    @Autowired private SubjectService subjectService;
    @Autowired private ScoreCalculatorService scoreCalculatorService;
    @Autowired private InfoboxRepository infoboxRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private SubjectRepository subjectRepository;

    @FXML private ImageView poster;
    @FXML private Label headerTitleLabel;
    @FXML private Label nameLabel;
    @FXML private Label originalNameLabel;
    @FXML private Label epsLabel;
    @FXML private Label dateLabel;
    @FXML private TextArea summaryArea;

    @FXML private TextField infoField;
    @FXML private TextField storyField;
    @FXML private TextField characterField;
    @FXML private TextField visualField;
    @FXML private TextField atmosphereField;
    @FXML private TextField loveField;

    @FXML private Canvas radarCanvas;
    @FXML private Label totalScoreLabel;
    @FXML private Label gradeLabel;
    @FXML private Label commentLabel;
    @FXML private Label adviceLabel;

    @FXML private ListView<Infobox> infoboxListView;
    @FXML private ListView<Tag> tagListView;
    @FXML private ListView<Character> characterListView;
    @FXML private ListView<Person> personListView;
    @FXML private ListView<Episode> episodeListView;

    @FXML private Label statusMessageLabel;
    @FXML private VBox logDrawer;
    @FXML private ListView<String> logListView;
    @FXML private VBox tagDrawer;
    @FXML private TextField tagSearchField;
    @FXML private TextField newTagField;
    @FXML private FlowPane tagButtonsContainer;
    @FXML private Button deleteModeButton;
    @FXML private Button saveTagButton;


    private Subject currentSubject;
    private double currentTotalScore = 0.0;
    private boolean logDrawerVisible = false;
    private boolean deleteMode = false;

    private double[] currentValues = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
    private final String[] LABELS = {"信息", "故事", "人物", "喜爱", "视听", "氛围"};
    private final ObservableList<Tag> allTags = FXCollections.observableArrayList();
    private final Set<Long> selectedTagIds = new HashSet<>();
    private final Set<Long> selectedForDeletionIds = new HashSet<>();
    private Point2D[] dataPoints = new Point2D[6];

    private static final Color RADAR_GRID_COLOR = Color.web("#e2e8f0");
    private static final Color RADAR_AXIS_COLOR = Color.web("#cbd5e0");
    private static final Color RADAR_LABEL_COLOR = Color.web("#4a5568");
    private static final Color RADAR_DATA_FILL = Color.web("rgba(102, 126, 234, 0.3)");
    private static final Color RADAR_DATA_STROKE = Color.web("#667eea");
    private static final Color RADAR_DOT_FILL = Color.WHITE;
    private static final Color RADAR_DOT_STROKE = Color.web("#667eea");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupInteractiveCanvas();
        setupEnterKeyHandlers();
        drawRadarChart();
        logListView.setItems(LogCollector.getInstance().getLogLines());
        logListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item);
            }
        });
    }

    private void setupInteractiveCanvas() {
        radarCanvas.setOnMouseMoved(e -> {
            boolean found = false;
            for (int i = 0; i < 6; i++) {
                if (dataPoints[i] != null) {
                    double dist = dataPoints[i].distance(e.getX(), e.getY());
                    if (dist < 15) {
                        drawRadarChart();
                        drawTooltip(i, dataPoints[i].getX(), dataPoints[i].getY());
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                drawRadarChart();
            }
        });
    }

    private void setupEnterKeyHandlers() {
        TextField[] fields = {infoField, storyField, characterField, visualField, atmosphereField, loveField};
        for (TextField field : fields) {
            field.setOnAction(e -> onCalculateAndSaveClick());
        }
    }

    public void initData(Subject subject) {
        this.currentSubject = subjectRepository.findByIdWithTags(subject.getId()).orElse(subject);
        headerTitleLabel.setText("ID: " + subject.getId());
        nameLabel.setText(subject.getNameCn() != null && !subject.getNameCn().isEmpty() ? subject.getNameCn() : subject.getName());
        originalNameLabel.setText(subject.getName());
        epsLabel.setText("话数: " + subject.getEps());
        dateLabel.setText("放送: " + subject.getDate());
        summaryArea.setText(subject.getSummary());

        if (subject.getImages() != null && subject.getImages().getSmall() != null) {
            poster.setImage(new Image(subject.getImages().getSmall(), true));
        }

        // 加载评分数据
        if (subject.getRating() != null && subject.getRating().getTotalscore() != null) {
            infoField.setText(String.valueOf(subject.getRating().getInformation()));
            storyField.setText(String.valueOf(subject.getRating().getStory()));
            characterField.setText(String.valueOf(subject.getRating().getCharacter()));
            visualField.setText(String.valueOf(subject.getRating().getQuality()));
            atmosphereField.setText(String.valueOf(subject.getRating().getAtmosphere()));
            loveField.setText(String.valueOf(subject.getRating().getLove()));
            onCalculateAndSaveClick(); // 这会触发雷达图更新
        } else {
            resetToDefault();
        }

        // 初始化所有 ListView 为空列表（避免 null）
        infoboxListView.setItems(FXCollections.observableArrayList());
        tagListView.setItems(FXCollections.observableArrayList());
        characterListView.setItems(FXCollections.observableArrayList());
        personListView.setItems(FXCollections.observableArrayList());
        episodeListView.setItems(FXCollections.observableArrayList());

        // 加载关联数据
        loadInfobox();
        loadTags();
        loadCharacters();
        loadPersons();
        loadEpisodes();
    }

    private void loadInfobox() {
        List<Infobox> infoboxList = infoboxRepository.findBySubject(currentSubject);
        infoboxListView.setItems(FXCollections.observableArrayList(infoboxList));
        infoboxListView.setCellFactory(lv -> new ListCell<Infobox>() {
            @Override
            protected void updateItem(Infobox item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                }
                else setText(item.getKey() + ": " + item.getValue());
            }
        });
    }

    private void loadTags() {
        List<Tag> tagList = currentSubject.getTags();
        if (tagList == null) tagList = new ArrayList<>();
        tagList.sort(Comparator.comparing(Tag::getCount).reversed());
        tagListView.setItems(FXCollections.observableArrayList(tagList));
        tagListView.setCellFactory(lv -> new ListCell<Tag>() {
            @Override
            protected void updateItem(Tag item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.getName() + " (" + item.getCount() + ")");
            }
        });
    }

    private void loadCharacters() {
        List<Character> characterList = characterRepository.findBySubjectsContaining(currentSubject);
        characterListView.setItems(FXCollections.observableArrayList(characterList));
        characterListView.setCellFactory(lv -> new ListCell<Character>() {
            private final HBox container = new HBox(10);
            private final Label nameLabel = new Label();
            private final Label castLabel = new Label(); // 显示声优
            private final ToggleButton likeBtn = new ToggleButton("👍");
            private final ToggleButton dislikeBtn = new ToggleButton("👎");
            private final ToggleButton neutralBtn = new ToggleButton("😐");
            private final ToggleGroup group = new ToggleGroup();

            {
                likeBtn.setToggleGroup(group);
                dislikeBtn.setToggleGroup(group);
                neutralBtn.setToggleGroup(group);
                castLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 12px;");
                VBox textBox = new VBox(2, nameLabel, castLabel);
                container.getChildren().addAll(textBox, likeBtn, dislikeBtn, neutralBtn);
                container.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(textBox, Priority.ALWAYS);

                likeBtn.setOnAction(e -> updateAttitude(1));
                dislikeBtn.setOnAction(e -> updateAttitude(-1));
                neutralBtn.setOnAction(e -> updateAttitude(0));
            }

            private void updateAttitude(int newAttitude) {
                Character character = getItem();
                if (character != null) {
                    character.setAttitude(newAttitude);
                    characterRepository.save(character);
                }
            }

            @Override
            protected void updateItem(Character item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.getName());
                    // 显示关联的声优（如果有）
                    String casts = item.getCasts().stream()
                            .map(Person::getName)
                            .collect(Collectors.joining(", "));
                    castLabel.setText(casts.isEmpty() ? "无" : "CV: " + casts);

                    int attitude = item.getAttitude() != null ? item.getAttitude() : 0;
                    switch (attitude) {
                        case 1 -> group.selectToggle(likeBtn);
                        case -1 -> group.selectToggle(dislikeBtn);
                        default -> group.selectToggle(neutralBtn);
                    }
                    setGraphic(container);
                }
            }
        });
    }

    private void loadPersons() {
        List<Person> personList = personRepository.findBySubjectsContaining(currentSubject);
        personListView.setItems(FXCollections.observableArrayList(personList));
        personListView.setCellFactory(lv -> new ListCell<Person>() {
            @Override
            protected void updateItem(Person item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    String careers = item.getCareers() != null ? String.join(", ", item.getCareers()) : "";
                    setText(item.getName() + (careers.isEmpty() ? "" : " (" + careers + ")"));
                }
            }
        });
    }

    private void loadEpisodes() {
        List<Episode> episodeList = episodeRepository.findBySubject(currentSubject);
        episodeListView.setItems(FXCollections.observableArrayList(episodeList));
        episodeListView.setCellFactory(lv -> new ListCell<Episode>() {
            private final HBox container = new HBox(10);
            private final Label nameLabel = new Label();
            private final ToggleButton likeBtn = new ToggleButton("👍");
            private final ToggleButton dislikeBtn = new ToggleButton("👎");
            private final ToggleButton neutralBtn = new ToggleButton("😐");
            private final ToggleGroup group = new ToggleGroup();

            {
                likeBtn.setToggleGroup(group);
                dislikeBtn.setToggleGroup(group);
                neutralBtn.setToggleGroup(group);
                VBox textBox = new VBox(1, nameLabel);
                container.getChildren().addAll(textBox, likeBtn, dislikeBtn, neutralBtn);
                container.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(textBox, Priority.ALWAYS);

                likeBtn.setOnAction(e -> updateAttitude(1));
                dislikeBtn.setOnAction(e -> updateAttitude(-1));
                neutralBtn.setOnAction(e -> updateAttitude(0));
            }

            private void updateAttitude(int newAttitude) {
                Episode episode = getItem();
                if (episode != null) {
                    episode.setAttitude(newAttitude);
                    episodeRepository.save(episode);
                }
            }

            @Override
            protected void updateItem(Episode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String displayName = item.getNameCn() != null && !item.getNameCn().isEmpty() ? item.getNameCn() : item.getName();
                    nameLabel.setText("第" + item.getEp() + "话 " + (displayName != null ? displayName : ""));
                    int attitude = item.getAttitude() != null ? item.getAttitude() : 0;
                    switch (attitude) {
                        case 1 -> group.selectToggle(likeBtn);
                        case -1 -> group.selectToggle(dislikeBtn);
                        default -> group.selectToggle(neutralBtn);
                    }
                    setGraphic(container);
                }
            }
        });
    }

    @FXML
    private void onAddTagClick() {
        // 展开抽屉
        tagDrawer.setManaged(true);
        tagDrawer.setVisible(true);
        setupTagDrawer();
        deleteMode = false;
        deleteModeButton.setText("删除");
        deleteModeButton.getStyleClass().remove("delete-mode-active");
        saveTagButton.setText("保存");
        statusMessageLabel.setText("标签管理模式已打开");
    }

    private void loadAllTags() {
        allTags.setAll(tagRepository.findAll());
        allTags.sort(Comparator.comparing(Tag::getCount).reversed());
    }

    private void setupTagDrawer() {
        tagButtonsContainer.getChildren().clear();
        loadAllTags();

        selectedTagIds.clear();
        if (currentSubject.getTags() != null) {
            currentSubject.getTags().forEach(tag -> selectedTagIds.add(tag.getTagId()));
        }

        for (Tag tag : allTags) {
            Button btn = new Button(tag.getName());
            btn.setUserData(tag);
            btn.getStyleClass().add("tag-toggle-button"); // 基础样式
            btn.setMaxWidth(Double.MAX_VALUE);

            // 根据当前模式设置初始样式
            updateButtonStyle(btn, tag);

            btn.setOnAction(e -> handleTagButtonAction(btn));
            tagButtonsContainer.getChildren().add(btn);
        }

        // 搜索过滤监听
        tagSearchField.textProperty().addListener((obs, old, val) -> filterTags(val));
        newTagField.setOnAction(e -> addNewTag());
    }

    private void updateButtonStyle(Button btn, Tag tag) {
        btn.getStyleClass().removeAll("tag-button-normal-selected", "tag-button-delete-selected", "delete-mode");
        if (deleteMode) {
            btn.getStyleClass().add("delete-mode");
            if (selectedForDeletionIds.contains(tag.getTagId())) {
                btn.getStyleClass().add("tag-button-delete-selected");
            }
        } else {
            if (selectedTagIds.contains(tag.getTagId())) {
                btn.getStyleClass().add("tag-button-normal-selected");
            }
        }
    }

    private void handleTagButtonAction(Button btn) {
        Tag tag = (Tag) btn.getUserData();
        Long tagId = tag.getTagId();
        if (deleteMode) {
            if (selectedForDeletionIds.contains(tagId))
                selectedForDeletionIds.remove(tagId);
            else
                selectedForDeletionIds.add(tagId);
        } else {
            if (selectedTagIds.contains(tagId))
                selectedTagIds.remove(tagId);
            else
                selectedTagIds.add(tagId);
        }
        updateButtonStyle(btn, tag);
    }

    private void filterTags(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            for (javafx.scene.Node node : tagButtonsContainer.getChildren()) {
                node.setVisible(true);
                node.setManaged(true);
            }
        } else {
            String lower = keyword.toLowerCase();
            for (javafx.scene.Node node : tagButtonsContainer.getChildren()) {
                Button btn = (Button) node;  // 改为 Button
                Tag tag = (Tag) btn.getUserData();
                boolean match = tag.getName().toLowerCase().contains(lower);
                btn.setVisible(match);
                btn.setManaged(match);
            }
        }
    }

    private void refreshTagButtons() {
        // 重建所有按钮（简化实现，也可只更新状态）
        setupTagDrawer();
    }

    @FXML
    private void toggleDeleteMode() {
        deleteMode = !deleteMode;
        if (deleteMode) {
            deleteModeButton.setText("退出删除");
            deleteModeButton.getStyleClass().add("delete-mode-active");
            saveTagButton.setText("删除");
            selectedForDeletionIds.clear();
            statusMessageLabel.setText("已切换到删除模式，点击标签选择要删除的项");
        } else {
            deleteModeButton.setText("删除");
            deleteModeButton.getStyleClass().remove("delete-mode-active");
            saveTagButton.setText("保存");
            selectedForDeletionIds.clear();
            statusMessageLabel.setText("已退出删除模式，标签管理模式已打开");
        }
        for (Node node : tagButtonsContainer.getChildren()) {
            Button btn = (Button) node;
            Tag tag = (Tag) btn.getUserData();
            updateButtonStyle(btn, tag);
        }
    }

    @FXML
    private void addNewTag() {
        String name = newTagField.getText().trim();
        if (name.isEmpty()) {
            statusMessageLabel.setText("标签名不能为空");
            return;
        }
        // 检查是否已存在（不区分大小写）
        Optional<Tag> existing = allTags.stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst();
        if (existing.isPresent()) {
            // 已存在则直接选中（正常模式）
            if (!deleteMode) {
                selectedTagIds.add(existing.get().getTagId());
                refreshTagButtons();
            }
            newTagField.clear();
            return;
        }
        // 新建标签
        Tag newTag = new Tag();
        newTag.setName(name);
        newTag.setCount(0); // 显式设置 count 为 0
        tagRepository.save(newTag);
        allTags.add(newTag);
        if (!deleteMode) {
            selectedTagIds.add(newTag.getTagId());
        }
        newTagField.clear();
        refreshTagButtons();
        statusMessageLabel.setText("标签已添加");
    }

    @FXML
    private void saveOrDeleteTags() {
        if (deleteMode) {
            if (selectedForDeletionIds.isEmpty()) { /* 提示 */ return; }
            for (Long tagId : selectedForDeletionIds) {
                subjectService.deleteTagAndRelations(tagId);
            }
            allTags.removeIf(tag -> selectedForDeletionIds.contains(tag.getTagId()));
            selectedTagIds.removeAll(selectedForDeletionIds);
            selectedForDeletionIds.clear();
            refreshTagButtons();
        } else {
            subjectService.updateSubjectTags(currentSubject.getId(), new ArrayList<>(selectedTagIds));
            currentSubject = subjectRepository.findByIdWithTags(currentSubject.getId()).orElse(currentSubject);
            loadTags();
            closeTagDrawer();
        }
    }

    @FXML
    private void closeTagDrawer() {
        tagDrawer.setManaged(false);
        tagDrawer.setVisible(false);
    }

    @FXML
    private void onBackClick() {
        try {
            // 获取 MainController 实例，设置恢复标志
            MainController mainController = Main.getContext().getBean(MainController.class);
            mainController.setRestoringFromBack(true);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/animemanager/FXML/main.fxml"));
            loader.setControllerFactory(Main.getContext()::getBean);
            Parent root = loader.load();
            Stage stage = (Stage) nameLabel.getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 800));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onCalculateAndSaveClick() {
        if (currentSubject == null) return;
        try {
            double info = Double.parseDouble(infoField.getText());
            double story = Double.parseDouble(storyField.getText());
            double chara = Double.parseDouble(characterField.getText());
            double love = Double.parseDouble(loveField.getText());
            double visual = Double.parseDouble(visualField.getText());
            double atmos = Double.parseDouble(atmosphereField.getText());

            info = Math.max(0, Math.min(10, info));
            story = Math.max(0, Math.min(10, story));
            chara = Math.max(0, Math.min(10, chara));
            love = Math.max(0, Math.min(10, love));
            visual = Math.max(0, Math.min(10, visual));
            atmos = Math.max(0, Math.min(10, atmos));

            currentValues = new double[]{info, story, chara, love, visual, atmos};

            Map<String, String> report = scoreCalculatorService.AnimeReport(info, story, chara, love, visual, atmos);
            currentTotalScore = Double.parseDouble(report.get("totalScore"));
            totalScoreLabel.setText(String.valueOf(currentTotalScore));

            gradeLabel.setText(report.get("grade"));
            commentLabel.setText(report.get("comment"));
            adviceLabel.setText("建议: " + report.get("advice"));

            drawRadarChart(); // 更新雷达图
            subjectService.UpdateSubject(currentSubject.getId(), info, story, chara, love, visual, atmos, currentTotalScore);
            statusMessageLabel.setText("保存成功");
        } catch (NumberFormatException e) {
            statusMessageLabel.setText("输入错误：请填写有效数字");
        } catch (Exception e) {
            statusMessageLabel.setText("保存失败：" + e.getMessage());
        }
    }

    @FXML
    private void resetToDefault() {
        infoField.setText("0.0");
        storyField.setText("0.0");
        characterField.setText("0.0");
        loveField.setText("0.0");
        visualField.setText("0.0");
        atmosphereField.setText("0.0");
        onCalculateAndSaveClick();
    }

    @FXML
    private void toggleLogDrawer() {
        logDrawerVisible = !logDrawerVisible;
        logDrawer.setManaged(logDrawerVisible);
        logDrawer.setVisible(logDrawerVisible);
    }

    @FXML
    private void closeLogDrawer() {
        logDrawerVisible = false;
        logDrawer.setManaged(false);
        logDrawer.setVisible(false);
    }

    @FXML
    private void clearLog() {
        LogCollector.getInstance().clear();
    }

    // 完整的雷达图绘制方法
    private void drawRadarChart() {
        GraphicsContext gc = radarCanvas.getGraphicsContext2D();
        double w = radarCanvas.getWidth();
        double h = radarCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;
        double maxRadius = Math.min(w, h) / 2 - 50;

        gc.clearRect(0, 0, w, h);

        // 1. 绘制五层网格
        gc.setStroke(RADAR_GRID_COLOR);
        gc.setLineWidth(1.0);
        for (int i = 1; i <= 5; i++) {
            double r = maxRadius * i / 5.0;
            drawPolygon(gc, cx, cy, r, 6);
        }

        // 2. 绘制轴线和标签
        gc.setFill(RADAR_LABEL_COLOR);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(new Font("Microsoft YaHei", 14));

        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60 - 90);
            double xEdge = cx + maxRadius * Math.cos(angle);
            double yEdge = cy + maxRadius * Math.sin(angle);

            gc.setStroke(RADAR_AXIS_COLOR);
            gc.strokeLine(cx, cy, xEdge, yEdge);

            double textR = maxRadius + 25;
            double tx = cx + textR * Math.cos(angle);
            double ty = cy + textR * Math.sin(angle);
            gc.fillText(LABELS[i], tx, ty + 5);
        }

        // 3. 计算数据点坐标并存储
        double[] xPoints = new double[6];
        double[] yPoints = new double[6];

        for (int i = 0; i < 6; i++) {
            double val = currentValues[i] / 10.0;
            double r = maxRadius * val;
            double angle = Math.toRadians(i * 60 - 90);
            xPoints[i] = cx + r * Math.cos(angle);
            yPoints[i] = cy + r * Math.sin(angle);
            dataPoints[i] = new Point2D(xPoints[i], yPoints[i]);
        }

        // 4. 绘制数据区域
        gc.beginPath();
        gc.moveTo(xPoints[0], yPoints[0]);
        for (int i = 1; i < 6; i++) {
            gc.lineTo(xPoints[i], yPoints[i]);
        }
        gc.closePath();

        gc.setFill(RADAR_DATA_FILL);
        gc.fill();
        gc.setStroke(RADAR_DATA_STROKE);
        gc.setLineWidth(2);
        gc.stroke();

        // 5. 绘制顶点圆点
        gc.setFill(RADAR_DOT_FILL);
        gc.setStroke(RADAR_DOT_STROKE);
        gc.setLineWidth(2);
        for (int i = 0; i < 6; i++) {
            gc.fillOval(xPoints[i] - 5, yPoints[i] - 5, 10, 10);
            gc.strokeOval(xPoints[i] - 5, yPoints[i] - 5, 10, 10);
        }
    }

    private void drawPolygon(GraphicsContext gc, double cx, double cy, double r, int sides) {
        double[] xPoints = new double[sides];
        double[] yPoints = new double[sides];
        for (int i = 0; i < sides; i++) {
            double angle = Math.toRadians(i * (360.0 / sides) - 90);
            xPoints[i] = cx + r * Math.cos(angle);
            yPoints[i] = cy + r * Math.sin(angle);
        }
        gc.strokePolygon(xPoints, yPoints, sides);
    }

    private void drawTooltip(int index, double x, double y) {
        GraphicsContext gc = radarCanvas.getGraphicsContext2D();
        double val = currentValues[index];
        String desc = scoreCalculatorService.getScoreDescription(val);
        String text = String.format("%s: %.1f\n[%s]", LABELS[index], val, desc);

        gc.setFill(Color.web("rgba(0, 0, 0, 0.8)"));
        double boxW = 100;
        double boxH = 50;
        double drawX = x + 15;
        double drawY = y - 25;
        if (drawX + boxW > radarCanvas.getWidth()) {
            drawX = x - 15 - boxW;
        }

        gc.fillRoundRect(drawX, drawY, boxW, boxH, 10, 10);
        gc.setFill(Color.WHITE);
        gc.setFont(new Font("Microsoft YaHei", 12));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(text, drawX + 10, drawY + 20);
    }
}