package com.example.animemanager.UI.Controller;

import com.example.animemanager.Entity.Subject;
import com.example.animemanager.Main;
import com.example.animemanager.Service.ScoreCalculatorService;
import com.example.animemanager.Service.SubjectService;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

@Controller
public class SubjectController implements Initializable {

    @Autowired private SubjectService subjectService;
    @Autowired private ScoreCalculatorService scoreCalculatorService;

    @FXML private ImageView poster;
    @FXML private Label headerTitleLabel;
    @FXML private Label nameLabel;
    @FXML private Label originalNameLabel;
    @FXML private Label epsLabel;
    @FXML private Label dateLabel;
    @FXML private TextArea summaryArea;

    // 改为 TextField 类型，名称与 FXML 中的 fx:id 对应
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

    private Subject currentSubject;
    private double currentTotalScore = 0.0;

    private double[] currentValues = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
    private final String[] LABELS = {"信息", "故事", "人物", "喜爱", "视听", "氛围"};

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
        setupEnterKeyHandlers();  // 修改为监听 TextField 的回车
        drawRadarChart();
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

    // 修改为监听 TextField 的回车事件
    private void setupEnterKeyHandlers() {
        TextField[] fields = {infoField, storyField, characterField, visualField, atmosphereField, loveField};
        for (TextField field : fields) {
            field.setOnAction(e -> onCalculateAndSaveClick());
        }
    }

    public void initData(Subject subject) {
        this.currentSubject = subject;
        headerTitleLabel.setText("ID: " + subject.getId());
        nameLabel.setText(subject.getNameCn() != null && !subject.getNameCn().isEmpty() ? subject.getNameCn() : subject.getName());
        originalNameLabel.setText(subject.getName());
        epsLabel.setText("话数: " + subject.getEps());
        dateLabel.setText("放送: " + subject.getDate());
        summaryArea.setText(subject.getSummary());

        if (subject.getImages() != null && subject.getImages().getSmall() != null) {
            poster.setImage(new Image(subject.getImages().getSmall(), true));
        }

        if (subject.getRating() != null && subject.getRating().getTotalscore() != null) {
            infoField.setText(String.valueOf(subject.getRating().getInformation()));
            storyField.setText(String.valueOf(subject.getRating().getStory()));
            characterField.setText(String.valueOf(subject.getRating().getCharacter()));
            visualField.setText(String.valueOf(subject.getRating().getQuality()));
            atmosphereField.setText(String.valueOf(subject.getRating().getAtmosphere()));
            loveField.setText(String.valueOf(subject.getRating().getLove()));
            onCalculateAndSaveClick();
        } else {
            resetToDefault();
        }
    }

    @FXML
    private void onBackClick() {
        try {
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
            double visual = Double.parseDouble(visualField.getText());
            double atmos = Double.parseDouble(atmosphereField.getText());
            double love = Double.parseDouble(loveField.getText());

            // 限制在0-10之间（可选）
            info = Math.max(0, Math.min(10, info));
            story = Math.max(0, Math.min(10, story));
            chara = Math.max(0, Math.min(10, chara));
            visual = Math.max(0, Math.min(10, visual));
            atmos = Math.max(0, Math.min(10, atmos));
            love = Math.max(0, Math.min(10, love));

            currentValues = new double[]{info, story, chara, visual, atmos, love};

            Map<String, String> report = scoreCalculatorService.AnimeReport(info, story, chara, visual, atmos, love);
            currentTotalScore = Double.parseDouble(report.get("totalScore"));
            totalScoreLabel.setText(String.valueOf(currentTotalScore));

            gradeLabel.setText(report.get("grade"));
            commentLabel.setText(report.get("comment"));
            adviceLabel.setText("建议: " + report.get("advice"));

            drawRadarChart();
            subjectService.UpdateSubject(currentSubject.getId(), info, story, chara, visual, atmos, love, currentTotalScore);

        } catch (NumberFormatException e) {
            showAlert("输入错误", "请确保所有评分字段都输入了有效的数字！");
        } catch (Exception e) {
            showAlert("错误", "更新失败：" + e.getMessage());
        }
    }

    @FXML
    private void resetToDefault() {
        infoField.setText("0.0");
        storyField.setText("0.0");
        characterField.setText("0.0");
        visualField.setText("0.0");
        atmosphereField.setText("0.0");
        loveField.setText("0.0");
        onCalculateAndSaveClick();
    }

    private void drawRadarChart() {
        GraphicsContext gc = radarCanvas.getGraphicsContext2D();
        double w = radarCanvas.getWidth();
        double h = radarCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;
        double maxRadius = Math.min(w, h) / 2 - 50;

        gc.clearRect(0, 0, w, h);

        gc.setStroke(RADAR_GRID_COLOR);
        gc.setLineWidth(1.0);
        for (int i = 1; i <= 5; i++) {
            double r = maxRadius * i / 5.0;
            drawPolygon(gc, cx, cy, r, 6);
        }

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

    private void showToast(String message) {
        Window window = null;
        if (radarCanvas.getScene() != null) {
            window = radarCanvas.getScene().getWindow();
        }
        if (window == null) {
            showAlert("提示", message);
            return;
        }
        Popup popup = new Popup();
        Label label = new Label(message);
        label.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 30;");
        popup.getContent().add(label);
        popup.show(window);
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(e -> popup.hide());
        delay.play();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}