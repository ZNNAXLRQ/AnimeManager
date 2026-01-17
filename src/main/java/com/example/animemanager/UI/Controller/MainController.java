package com.example.animemanager.UI.Controller;

import com.example.animemanager.Service.ScoreCalculatorService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

@Controller
public class MainController implements Initializable {

    @Autowired
    private ScoreCalculatorService scoreCalculatorService;

    // 雷达图颜色常量
    private static final Color RADAR_GRID_COLOR = Color.web("#e2e8f0");
    private static final Color RADAR_AXIS_COLOR = Color.web("#cbd5e0");
    private static final Color RADAR_LABEL_COLOR = Color.web("#4a5568");
    private static final Color RADAR_DATA_FILL = Color.web("rgba(102, 126, 234, 0.3)");
    private static final Color RADAR_DATA_STROKE = Color.web("#667eea");
    private static final Color RADAR_DOT_FILL = Color.WHITE;
    private static final Color RADAR_DOT_STROKE = Color.web("#667eea");

    @FXML private TextField infoField, storyField, charField, visualField, atmosField, loveField;
    @FXML private Canvas radarCanvas;
    @FXML private Label totalScoreLabel, gradeLabel, commentLabel, adviceLabel;

    private final String[] LABELS = {"信息", "故事", "人物", "视听", "氛围", "喜爱"};
    // 保存当前六个点的坐标，用于鼠标交互检测
    private Point2D[] dataPoints = new Point2D[6];
    private double[] currentValues = {5, 5, 5, 5, 5, 5};

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupInteractiveCanvas();
        calculateAndUpdate(); // 初始绘制
    }

    private void setupInteractiveCanvas() {
        // 鼠标移动监听：实现悬停显示数值
        radarCanvas.setOnMouseMoved(e -> {
            boolean found = false;
            for (int i = 0; i < 6; i++) {
                if (dataPoints[i] != null) {
                    double dist = dataPoints[i].distance(e.getX(), e.getY());
                    if (dist < 15) { // 鼠标距离点15像素内触发
                        drawRadarChart(currentValues); // 重绘清除旧tooltip
                        drawTooltip(i, dataPoints[i].getX(), dataPoints[i].getY());
                        found = true;
                        break;
                    }
                }
            }
            // 如果鼠标没有悬停在任何点上，保持纯净图表
            if (!found) {
                drawRadarChart(currentValues);
            }
        });
    }

    @FXML
    private void onCalculateClick() {
        calculateAndUpdate();
    }

    @FXML
    private void resetToDefault() {
        infoField.setText("5.0");
        storyField.setText("5.0");
        charField.setText("5.0");
        visualField.setText("5.0");
        atmosField.setText("5.0");
        loveField.setText("5.0");
        calculateAndUpdate();
    }

    private void calculateAndUpdate() {
        try {
            // 解析输入，限制在0-10之间
            double[] values = {
                    parseInput(infoField), parseInput(storyField), parseInput(charField),
                    parseInput(visualField), parseInput(atmosField), parseInput(loveField)
            };
            this.currentValues = values;

            // 1. 计算结果
            Map<String, String> report = scoreCalculatorService.AnimeReport(
                    values[0], values[1], values[2], values[3], values[4], values[5]
            );

            // 2. 更新下方UI文本
            updateResultUI(report);

            // 3. 绘制雷达图
            drawRadarChart(values);

        } catch (NumberFormatException e) {
            // 可以加一个Alert提示用户输入数字
            System.out.println("请输入有效的数字");
        }
    }

    private double parseInput(TextField field) {
        try {
            double v = Double.parseDouble(field.getText());
            return Math.max(0, Math.min(10, v));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void updateResultUI(Map<String, String> report) {
        totalScoreLabel.setText(report.get("totalScore"));
        gradeLabel.setText(report.get("grade"));
        commentLabel.setText(report.get("comment"));
        adviceLabel.setText("建议: " + report.get("advice"));

        double score = Double.parseDouble(report.get("totalScore"));
        String colorHex = score >= 85 ? "#FFD700" : (score >= 60 ? "#69f0ae" : "#ff5252");
        totalScoreLabel.setStyle("-fx-text-fill: " + colorHex + ";");
    }

    // --- 绘图逻辑 ---

    private void drawRadarChart(double[] values) {
        GraphicsContext gc = radarCanvas.getGraphicsContext2D();
        double w = radarCanvas.getWidth();
        double h = radarCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;
        double maxRadius = Math.min(w, h) / 2 - 50; // 留出更多边距给文字

        gc.clearRect(0, 0, w, h);

        // 1. 绘制网格背景
        gc.setStroke(Color.web("#e2e8f0")); // 使用CSS中的颜色
        gc.setLineWidth(1.0);
        for (int i = 1; i <= 5; i++) {
            double r = maxRadius * i / 5.0;
            drawPolygon(gc, cx, cy, r, 6);
        }

        // 2. 绘制轴线和标签
        gc.setFill(Color.web("#4a5568")); // 使用CSS中的颜色
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(new Font("Microsoft YaHei", 14));

        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60 - 90);
            double xEdge = cx + maxRadius * Math.cos(angle);
            double yEdge = cy + maxRadius * Math.sin(angle);

            // 轴线
            gc.setStroke(Color.web("#cbd5e0")); // 使用CSS中的颜色
            gc.strokeLine(cx, cy, xEdge, yEdge);

            // 标签 (根据位置微调偏移量)
            double textR = maxRadius + 25;
            double tx = cx + textR * Math.cos(angle);
            double ty = cy + textR * Math.sin(angle);
            gc.fillText(LABELS[i], tx, ty + 5);
        }

        // 3. 计算数据点坐标并存储
        double[] xPoints = new double[6];
        double[] yPoints = new double[6];

        for (int i = 0; i < 6; i++) {
            double val = values[i] / 10.0;
            double r = maxRadius * val;
            double angle = Math.toRadians(i * 60 - 90);
            xPoints[i] = cx + r * Math.cos(angle);
            yPoints[i] = cy + r * Math.sin(angle);

            // 存储坐标用于鼠标交互
            dataPoints[i] = new Point2D(xPoints[i], yPoints[i]);
        }

        // 4. 绘制数据区域
        gc.beginPath();
        gc.moveTo(xPoints[0], yPoints[0]);
        for(int i=1; i<6; i++) gc.lineTo(xPoints[i], yPoints[i]);
        gc.closePath();

        // 填充样式
        gc.setFill(Color.web("rgba(102, 126, 234, 0.3)")); // 使用CSS中的颜色
        gc.fill();
        gc.setStroke(Color.web("#667eea")); // 使用CSS中的颜色
        gc.setLineWidth(2); // 调整线宽以匹配CSS
        gc.stroke();

        // 绘制顶点圆点
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.web("#667eea")); // 使用CSS中的颜色
        gc.setLineWidth(2);
        for(int i=0; i<6; i++) {
            gc.fillOval(xPoints[i]-5, yPoints[i]-5, 10, 10);
            gc.strokeOval(xPoints[i]-5, yPoints[i]-5, 10, 10);
        }
    }

    private void drawTooltip(int index, double x, double y) {
        GraphicsContext gc = radarCanvas.getGraphicsContext2D();
        double val = currentValues[index];
        // 获取该维度的评价描述
        String desc = scoreCalculatorService.getScoreDescription(val);
        String text = String.format("%s: %.1f\n[%s]", LABELS[index], val, desc);

        // 绘制Tooltip背景框
        gc.setFill(Color.web("rgba(0, 0, 0, 0.8)"));
        double boxW = 100;
        double boxH = 50;
        // 智能调整位置防止出界
        double drawX = x + 15;
        double drawY = y - 25;
        if (drawX + boxW > radarCanvas.getWidth()) drawX = x - 15 - boxW;

        gc.fillRoundRect(drawX, drawY, boxW, boxH, 10, 10);

        // 绘制文字
        gc.setFill(Color.WHITE);
        gc.setFont(new Font("Microsoft YaHei", 12));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(text, drawX + 10, drawY + 20);
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
}