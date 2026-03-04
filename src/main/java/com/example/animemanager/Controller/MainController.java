package com.example.animemanager.Controller;

import com.example.animemanager.Entity.Subject;
import com.example.animemanager.Main;
import com.example.animemanager.Service.DataImportService;
import com.example.animemanager.Service.FilterService;
import com.example.animemanager.Service.ScoreCalculatorService;
import com.example.animemanager.Service.SubjectService;
import com.example.animemanager.Util.LogCollector;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class MainController implements Initializable {

    @FXML private ListView<Subject> subjectListView;
    @FXML private ComboBox<String> sortCombo;
    @FXML private ToggleButton ascDescToggle;
    @FXML private Label statusLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterTypeCombo;
    @FXML private TextField filterInput;
    @FXML private TextField startDateField;
    @FXML private TextField endDateField;
    @FXML private Label bangumiLevelLabel;
    @FXML private Label localLevelLabel;
    @FXML private Label Distancelabel;
    @FXML private VBox logDrawer;
    @FXML private ListView<String> logListView;

    @Autowired private SubjectService subjectService;
    @Autowired private DataImportService dataImportService;
    @Autowired private FilterService filterService;
    @Autowired private ScoreCalculatorService scoreCalculatorService;

    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private final ObservableList<Subject> observableSubjects = FXCollections.observableArrayList();
    private FilteredList<Subject> filteredSubjects;
    private boolean logDrawerVisible = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSortControls();
        setupFilterControls();
        setupListView();
        setupSearch();
        startDateField.setPromptText("起始日期 (如2026-03-05)");
        endDateField.setPromptText("结束日期 (如2026-03-05)");
        loadSubjectsAsync(false);
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

    private void setupSortControls() {
        sortCombo.getItems().addAll("放送日期排序", "中文名排序", "Rank排序", "Bgm Score排序", "本地总分排序", "ID排序");
        sortCombo.getSelectionModel().selectFirst();
        ascDescToggle.setSelected(true);
        ascDescToggle.setText("逆序 (DESC)");

        sortCombo.setOnAction(e -> applySorting());
        ascDescToggle.setOnAction(e -> {
            ascDescToggle.setText(ascDescToggle.isSelected() ? "逆序 (DESC)" : "顺序 (ASC)");
            applySorting();
        });
    }

    private void setupSearch() {
        // 创建过滤列表并绑定到 ListView
        filteredSubjects = new FilteredList<>(observableSubjects, p -> true);
        subjectListView.setItems(filteredSubjects);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredSubjects.setPredicate(subject -> {
                if (newVal == null || newVal.isEmpty()) return true;
                String lower = newVal.toLowerCase();
                String cnName = subject.getNameCn();
                String name = subject.getName();
                return (cnName != null && cnName.toLowerCase().contains(lower)) ||
                        (name != null && name.toLowerCase().contains(lower));
            });
            // 更新状态栏显示数量
            statusLabel.setText(filteredSubjects.size() + " 部番剧");
        });
    }

    private void setupFilterControls() {
        filterTypeCombo.getItems().addAll("Tag 标签", "Person 制作人员", "Character 角色态度(±1)", "Episode 剧集态度(±1)");
        filterTypeCombo.getSelectionModel().selectFirst();
    }

    private void setupListView() {
        // 注意：ListView 的 items 稍后会被 filteredSubjects 覆盖，此处不设置
        subjectListView.setCellFactory(param -> new ListCell<Subject>() {
            private HBox content;
            private ImageView imageView;
            private Label title, rankInfo, scoreInfo, dateInfo;
            private HBox starBox;  // 新增星级容器

            {
                imageView = new ImageView();
                imageView.setFitWidth(80);
                imageView.setFitHeight(110);
                imageView.setPreserveRatio(true);

                title = new Label();
                title.getStyleClass().add("list-title");
                title.setWrapText(true);

                dateInfo = new Label();
                dateInfo.getStyleClass().add("list-info");

                rankInfo = new Label();
                rankInfo.getStyleClass().add("list-info");

                scoreInfo = new Label();
                scoreInfo.getStyleClass().add("list-score");

                starBox = new HBox(2);  // 间距2
                starBox.setAlignment(Pos.CENTER_RIGHT);

                VBox centerBox = new VBox(5, title, dateInfo);
                centerBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(centerBox, Priority.ALWAYS);

                VBox rightBox = new VBox(5, rankInfo, starBox, scoreInfo);  // 将星级插入中间
                rightBox.setAlignment(Pos.CENTER_RIGHT);

                content = new HBox(15, imageView, centerBox, rightBox);
                content.setAlignment(Pos.CENTER_LEFT);
                content.getStyleClass().add("list-cell-card");
            }

            @Override
            protected void updateItem(Subject subject, boolean empty) {
                super.updateItem(subject, empty);
                if (empty || subject == null) {
                    setGraphic(null);
                } else {
                    title.setText(subject.getNameCn() != null && !subject.getNameCn().isEmpty() ? subject.getNameCn() : subject.getName());
                    dateInfo.setText("放送: " + (subject.getDate() != null ? subject.getDate() : "未知"));

                    String rank = (subject.getRating() != null && subject.getRating().getRank() != null) ? String.valueOf(subject.getRating().getRank()) : "--";
                    rankInfo.setText("Rank: " + rank);

                    String bgmScore = (subject.getRating() != null && subject.getRating().getScore() != null) ? String.valueOf(subject.getRating().getScore()) : "--";
                    String localScore = (subject.getRating() != null && subject.getRating().getTotalscore() != null) ? String.format("%.1f", subject.getRating().getTotalscore()) : "--";
                    scoreInfo.setText(String.format("BGM: %s | 本地: %s", bgmScore, localScore));

                    // 星级显示
                    double total = (subject.getRating() != null && subject.getRating().getTotalscore() != null)
                            ? subject.getRating().getTotalscore() : 0;

                    int baseStars = 0;          // 整星数量
                    boolean hasHalfStar = false; // 是否有半星

                    if (total >= 105) {
                        baseStars = 10;          // 10星，无半星
                    } else if (total >= 95) {
                        baseStars = 9;
                        if (total >= 100) hasHalfStar = true;   // 半星阈值 100
                    } else if (total >= 85) {
                        baseStars = 8;
                        if (total >= 90) hasHalfStar = true;     // 90
                    } else if (total >= 70) {
                        baseStars = 7;
                        if (total >= 77.5) hasHalfStar = true;   // 77.5
                    } else if (total >= 60) {
                        baseStars = 6;
                        if (total >= 65) hasHalfStar = true;     // 65
                    } else if (total >= 48) {
                        baseStars = 5;
                        if (total >= 54) hasHalfStar = true;     // 54
                    } else if (total >= 32) {
                        baseStars = 4;
                        if (total >= 40) hasHalfStar = true;     // 40
                    } else if (total >= 24) {
                        baseStars = 3;
                        if (total >= 28) hasHalfStar = true;     // 28
                    } else if (total >= 19) {
                        baseStars = 2;
                        if (total >= 21.5) hasHalfStar = true;   // 21.5
                    } else if (total >= 14) {
                        baseStars = 1;
                        if (total >= 16.5) hasHalfStar = true;   // 16.5
                    } // 否则 baseStars = 0，hasHalfStar 保持 false
                    starBox.getChildren().clear();
                    for (int i = 0; i < 10; i++) {
                        FontIcon star = new FontIcon();
                        star.setIconSize(14);
                        if (i < baseStars) {
                            star.setIconLiteral("fas-star");
                            star.setIconColor(Color.GOLD);
                        } else if (hasHalfStar && i == baseStars) {
                            star.setIconLiteral("fas-star-half-alt");   // 半星图标
                            star.setIconColor(Color.GOLD);
                        } else {
                            star.setIconLiteral("far-star");
                            star.setIconColor(Color.LIGHTGRAY);
                        }
                        starBox.getChildren().add(star);
                    }

                    // 图片缓存
                    String imageUrl = null;
                    if (subject.getImages() != null && subject.getImages().getGrid() != null) {
                        imageUrl = subject.getImages().getGrid();
                    }
                    if (imageUrl != null) {
                        if (IMAGE_CACHE.containsKey(imageUrl)) {
                            imageView.setImage(IMAGE_CACHE.get(imageUrl));
                        } else {
                            Image poster = new Image(imageUrl, true);
                            IMAGE_CACHE.put(imageUrl, poster);
                            imageView.setImage(poster);
                        }
                    } else {
                        imageView.setImage(null);
                    }

                    content.setOnMouseClicked(e -> openSubjectDetail(subject));
                    setGraphic(content);
                }
            }
        });
    }

    private void loadSubjectsAsync(boolean forceUpdate) {
        statusLabel.setText("加载中...");
        CompletableFuture.supplyAsync(() -> {
            if (forceUpdate) subjectService.clearCache();
            return subjectService.getAllSubjects();
        }).thenAccept(subjects -> Platform.runLater(() -> {
            observableSubjects.setAll(subjects);
            applySorting();  // 排序会作用在 observableSubjects 上，FilteredList 会自动更新
            statusLabel.setText(filteredSubjects.size() + " 部番剧");
        }));
    }

    private void applyFiltersAsync() {
        // 读取当前UI值
        String type = filterTypeCombo.getValue();
        String keyword = filterInput.getText();
        String start = startDateField.getText();
        String end = endDateField.getText();

        statusLabel.setText("筛选数据中...");
        CompletableFuture.supplyAsync(() -> {
            // 1. 根据类型关键词获取基础列表
            List<Subject> baseList;
            if (keyword != null && !keyword.trim().isEmpty()) {
                baseList = switch (type) {
                    case "Tag 标签" -> filterService.filterByTag(keyword);
                    case "Person 制作人员" -> filterService.filterByPerson(keyword);
                    case "Character 角色态度(±1)" -> filterService.filterByCharacterAttitude(keyword);
                    case "Episode 剧集态度(±1)" -> filterService.filterByEpisodeAttitude(keyword);
                    default -> subjectService.getAllSubjects();
                };
            } else {
                baseList = subjectService.getAllSubjects();
            }

            // 2. 应用日期范围过滤（内存过滤）
            if ((start != null && !start.trim().isEmpty()) ||
                    (end != null && !end.trim().isEmpty())) {
                baseList = filterService.filterSubjectsByDateRange(baseList, start, end);
            }
            return baseList;
        }).thenAccept(filtered -> Platform.runLater(() -> {
            observableSubjects.setAll(filtered);
            applySorting();
            statusLabel.setText("筛选完成: " + filteredSubjects.size() + " 部番剧");
        }));
    }

    private void applySorting() {
        if (observableSubjects.isEmpty()) return;

        String criteria = sortCombo.getValue();
        boolean isDesc = ascDescToggle.isSelected();

        Comparator<Subject> comparator = switch (criteria) {
            case "中文名排序" -> Comparator.comparing(s -> s.getNameCn() != null ? s.getNameCn() : s.getName());
            case "Rank排序" -> Comparator.comparing(s -> (s.getRating() != null && s.getRating().getRank() != null) ? s.getRating().getRank() : Integer.MAX_VALUE);
            case "Bgm Score排序" -> Comparator.comparing(s -> (s.getRating() != null && s.getRating().getScore() != null) ? s.getRating().getScore() : -1.0);
            case "本地总分排序" -> Comparator.comparing(s -> (s.getRating() != null && s.getRating().getTotalscore() != null) ? s.getRating().getTotalscore() : -1.0);
            case "放送日期排序" -> Comparator.comparing(s -> s.getDate() != null ? s.getDate() : "");
            default -> Comparator.comparing(Subject::getId);
        };

        if (isDesc) comparator = comparator.reversed();
        FXCollections.sort(observableSubjects, comparator);
    }

    @FXML
    private void onUpdateDataClick() {
        statusLabel.setText("后台数据更新中...");
        Task<Void> updateTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                dataImportService.DataImport();
                return null;
            }
        };

        updateTask.setOnSucceeded(e -> {
            statusLabel.setText("更新完成，正在刷新...");
            // 清空所有筛选条件
            filterInput.clear();
            startDateField.clear();
            endDateField.clear();
            // 重新加载全部数据（不使用筛选）
            loadSubjectsAsync(true);
        });


        updateTask.setOnFailed(e -> {
            statusLabel.setText("更新失败!");
            e.getSource().getException().printStackTrace();
        });

        Thread thread = new Thread(updateTask);
        thread.setDaemon(true);
        thread.start();
    }

    // 3. 添加执行筛选逻辑的方法
    @FXML
    private void onFilterClick() {
        applyFiltersAsync();
    }

    // 4. 添加清空筛选逻辑的方法
    @FXML
    private void onClearFilterClick() {
        filterInput.clear();
        applyFiltersAsync();
    }

    @FXML
    private void onDateFilterClick() {
        applyFiltersAsync();
    }

    @FXML
    private void onClearDateClick() {
        startDateField.clear();
        endDateField.clear();
        applyFiltersAsync();
    }

    private void openSubjectDetail(Subject subject) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/animemanager/FXML/subject.fxml"));
            loader.setControllerFactory(Main.getContext()::getBean);
            Parent root = loader.load();

            SubjectController controller = loader.getController();
            controller.initData(subject);

            Stage stage = (Stage) subjectListView.getScene().getWindow();
            // 获取当前窗口尺寸
            double width = stage.getWidth();
            double height = stage.getHeight();
            stage.setScene(new Scene(root, width, height));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void calculateLevels() {
        List<Subject> currentList = new ArrayList<>(filteredSubjects);
        if (currentList.isEmpty()) {
            bangumiLevelLabel.setText("--");
            localLevelLabel.setText("--");
            Distancelabel.setText("--");
            statusLabel.setText("列表为空，无法计算");
            return;
        }

        double bangumiLevel = scoreCalculatorService.calculateBangumiLevel(currentList);
        double localLevel = scoreCalculatorService.calculateLocallevel(currentList);
        double distance = (bangumiLevel - localLevel) / 10;

        bangumiLevelLabel.setText(String.format("%.2f", bangumiLevel));
        localLevelLabel.setText(String.format("%.2f", localLevel));
        Distancelabel.setText(String.format("% .2f", distance));
        statusLabel.setText("均分计算完成");
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
}