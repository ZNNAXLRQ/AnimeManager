package com.example.animemanager.UI.Controller;

import com.example.animemanager.Entity.Subject;
import com.example.animemanager.Main;
import com.example.animemanager.Service.DataImportService;
import com.example.animemanager.Service.SubjectService;
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
import java.util.Comparator;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class MainController implements Initializable {

    @FXML private ListView<Subject> subjectListView;
    @FXML private ComboBox<String> sortCombo;
    @FXML private ToggleButton ascDescToggle;
    @FXML private Label statusLabel;
    @FXML private TextField searchField;  // 新增搜索框

    @Autowired private SubjectService subjectService;
    @Autowired private DataImportService dataImportService;

    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private final ObservableList<Subject> observableSubjects = FXCollections.observableArrayList();
    private FilteredList<Subject> filteredSubjects;  // 新增过滤列表

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSortControls();
        setupListView();
        setupSearch();  // 新增搜索绑定
        loadSubjectsAsync(false);
    }

    private void setupSortControls() {
        sortCombo.getItems().addAll("ID排序", "中文名排序", "Rank排序", "Bgm Score排序", "本地总分排序", "放送日期排序");
        sortCombo.getSelectionModel().selectFirst();

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
                    starBox.getChildren().clear();
                    double total = (subject.getRating() != null && subject.getRating().getTotalscore() != null) ? subject.getRating().getTotalscore() : 0;
                    int fullStars = 0;
                    if (total >= 105) {
                        fullStars = 10;
                    } else if (total >= 95) {
                        fullStars = 9;
                    } else if (total >= 85) {
                        fullStars = 8;
                    } else if (total >= 70) {
                        fullStars = 7;
                    } else if (total >= 60) {
                        fullStars = 6;
                    } else if (total >= 48) {
                        fullStars = 5;
                    } else if (total >= 32) {
                        fullStars = 4;
                    } else if (total >= 24) {
                        fullStars = 3;
                    } else if (total >= 19) {
                       fullStars = 2;
                    } else if (total >= 14) {
                        fullStars = 1;
                    } else {
                        fullStars = 0;
                    }
                    for (int i = 0; i < 10; i++) {
                        FontIcon star = new FontIcon();
                        star.setIconLiteral(i < fullStars ? "fas-star" : "far-star");
                        star.setIconSize(14);
                        star.setIconColor(i < fullStars ? Color.GOLD : Color.LIGHTGRAY);
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

    private void openSubjectDetail(Subject subject) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/animemanager/FXML/subject.fxml"));
            loader.setControllerFactory(Main.getContext()::getBean);
            Parent root = loader.load();

            SubjectController controller = loader.getController();
            controller.initData(subject);

            Stage stage = (Stage) subjectListView.getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 800));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}