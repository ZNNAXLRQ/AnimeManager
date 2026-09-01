package com.example.animemanager.Service;

import com.example.animemanager.Entity.Subject;
import com.example.animemanager.Util.JsonConfigUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScoreCalculatorService {

    private static final double BASE_SCORE = 10.0; // 基础保底分

    // 权重配置
    private static final Map<String, Double> ANIME_WEIGHTS;
    private static double WEIGHT_STORY = 0.25;
    private static double WEIGHT_CHARACTER = 0.20;
    private static double WEIGHT_VISUAL = 0.20;
    private static double WEIGHT_ATMOSPHERE = 0.20;
    private static double WEIGHT_LOVE = 0.15;

    // 数值平衡参数
    private static final double MAX_PERFORMANCE_SCORE = 90.0; // 五维属性满分能提供的最大分数
    private static final double NEUTRAL_PERFORMANCE_SCORE = 40.0; // 5分(及格)对应的分数价值
    private static final double MAX_INFO_SCORE = 15.0; // 信息量满分能提供的最大分数
    private static final double CURVE_POWER_UP = 2.2; // 上半区(5-10)加速指数，越大10分越珍贵
    private static final double CURVE_POWER_DOWN = 0.6; // 下半区(0-5)减速指数

    static {
        Map<String, Double> weights = null;
        try {
            weights = JsonConfigUtil.readAnimeWeights("Data/config.json");
        } catch (Exception e) {
            System.err.println("[Score]      读取权重配置文件失败，使用默认权重: " + e.getMessage());
            weights = new HashMap<>();
        }
        if (weights == null || weights.isEmpty()) {
            weights = new HashMap<>();
        }
        ANIME_WEIGHTS = weights;
        WEIGHT_STORY = ANIME_WEIGHTS.getOrDefault("story", 0.25);
        WEIGHT_CHARACTER = ANIME_WEIGHTS.getOrDefault("character", 0.20);
        WEIGHT_VISUAL = ANIME_WEIGHTS.getOrDefault("visual", 0.20);
        WEIGHT_ATMOSPHERE = ANIME_WEIGHTS.getOrDefault("atmosphere", 0.20);
        WEIGHT_LOVE = ANIME_WEIGHTS.getOrDefault("love", 0.15);
        System.out.println("[Score]      当前权重: " + ANIME_WEIGHTS);
    }

    public ScoreCalculatorService() {
    }

    // 核心评分逻辑
    public double calculateTotalScore(double infoRaw, double storyRaw, double characterRaw, double visualRaw, double atmosphereRaw, double loveRaw) {
        // 1. 分数矫正 (0-10)
        double info = correctScore(infoRaw);
        double story = correctScore(storyRaw);
        double character = correctScore(characterRaw);
        double visual = correctScore(visualRaw);
        double atmosphere = correctScore(atmosphereRaw);
        double love = correctScore(loveRaw);

        // 2. 计算各项属性的“价值分” (应用加速曲线)
        double valStory = calculateCurveValue(story);
        valStory = fixLowScore(story, valStory);
        double valCharacter = calculateCurveValue(character);
        valCharacter = fixLowScore(character, valCharacter);
        double valVisual = calculateCurveValue(visual);
        valVisual = fixLowScore(visual, valVisual);
        double valAtmosphere = calculateCurveValue(atmosphere);
        valAtmosphere = fixLowScore(atmosphere, valAtmosphere);
        double valLove = calculateCurveValue(love);
        valLove = fixLowScore(love, valLove);
        // 3. 计算五维加权总分 (满分约90分)
        double performanceScore = valStory * WEIGHT_STORY + valCharacter * WEIGHT_CHARACTER + valVisual * WEIGHT_VISUAL + valAtmosphere * WEIGHT_ATMOSPHERE + valLove * WEIGHT_LOVE;
        if (story < 3.5 || character < 3.5 || visual < 3.5 || atmosphere < 3.5 || love < 3.5) {
            performanceScore *= 0.8;
        }
        // 4. 计算信息量得分 (满分15分)
        double infoScore = calculateInfoScore(info);
        // 5. 计算初步总分 结构：保底(10) + 信息(0-15) + 表现(0-90) = Max 115
        double totalScore = BASE_SCORE + infoScore + performanceScore;
        if (info < 5.0) {
            // 信息量总分打折
            totalScore *= 0.9;
        }
        // 7. 最终兜底，确保不低于BASE
        return Math.max(BASE_SCORE, totalScore);
    }

    public Map<String, String> AnimeReport(double info, double story, double character, double visual, double atmosphere, double love) {
        double totalScore = calculateTotalScore(info, story, character, visual, atmosphere, love);

        String grade, comment, advice;
        double temp = 0;

        // 评级系统 (115分制)
        if (totalScore >= 110) {
            grade = "✦ 神作 ✦";
            comment = "难以超越的巅峰之作";
            temp = (totalScore - 110) / 10 + 10.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 100) {
            grade = "★ 准神作 ★";
            comment = "绝对能打的顶尖之作";
            temp = (totalScore - 100) / 10 + 9.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 90) {
            grade = "★★★★★";
            comment = "不得不看的杰出之作";
            temp = (totalScore - 90) / 10 + 8.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 80) {
            grade = "★★★★☆";
            comment = "值得一看的优秀之作";
            temp = (totalScore - 80) / 10 + 7.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 65) {
            grade = "★★★☆☆";
            comment = "可以去看的不错之作";
            temp = (totalScore - 65) / 15 + 6.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 50) {
            grade = "★★☆☆☆";
            comment = "随便看看的平庸之作";
            temp = (totalScore - 50) / 15 + 5.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 32) {
            grade = "★☆☆☆☆";
            comment = "勉强能看的瑕疵之作";
            temp = (totalScore - 32) / 18 + 4.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 24) {
            grade = "☆☆☆☆☆";
            comment = "为啥要看的无趣之作";
            temp = (totalScore - 24) / 8 + 3.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else {
            grade = "☆ 纯石 ☆";
            comment = "怀疑人生的逆天之作";
            temp = (totalScore - 10) / 7 + 1.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        }

        Map<String, String> map = new HashMap<>();
        map.put("grade", grade);
        map.put("comment", comment);
        map.put("advice", advice);
        map.put("totalScore", String.format("%.2f", totalScore));
        map.put("info", String.valueOf(info));
        map.put("story", String.valueOf(story));
        map.put("character", String.valueOf(character));
        map.put("visual", String.valueOf(visual));
        map.put("atmosphere", String.valueOf(atmosphere));
        map.put("love", String.valueOf(love));
        return map;
    }

    public double calculateLocallevel(List<Subject> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        List<Double> scores = new ArrayList<>();
        for (Subject subject : subjects) {
            double rating = subject.getRating().getTotalscore();
            scores.add(rating);
            sum += rating;
        }
        int n = scores.size();
        double mu = sum / n;

        // 新公式参数（可根据实际数据微调）
        double a = 0.012;      // 作品数影响系数
        double c = 0.05;       // 方差惩罚系数
        double K = 500.0;      // 方差饱和常数
        double alpha = 0.04;   // 偏移影响幅度
        double beta = 0.0001;  // 偏移压缩系数

        // 作品数因子 f(N) = 1 + a * ln(1+N)
        double f = 1 + a * Math.log(1 + n);

        // 方差因子 g(V) = 1 - c * V/(V+K)
        double sumSq = 0;
        for (double s : scores) {
            sumSq += (s - mu) * (s - mu);
        }
        double V = sumSq / n;
        double g = 1 - c * V / (V + K);

        // 偏移因子 h(u) = 1 + α * tanh(β * u)，其中 u = (Σ (s_i-60)^3)/n
        double D = 0;
        for (double s : scores) {
            double diff = s - 60.0;
            D += diff * diff * diff; // 立方放大
        }
        double u = D / n;
        double h = 1 + alpha * Math.tanh(beta * u);

        // 综合原始分
        return mu * f * g * h;
    }

    public double calculateBangumiLevel(List<Subject> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        List<Double> scores = new ArrayList<>();
        for (Subject subject : subjects) {
            double rating = subject.getRating().getScore() + 0.5;
            double num = 0;
            if (rating >= 10) {
                num = 110 + (rating - 10.0) * 10;
            } else if (rating >= 9) {
                num = 100 + (rating - 9.0) * 10;
            } else if (rating >= 8) {
                num = 90 + (rating - 8.0) * 10;
            } else if (rating >= 7) {
                num = 80 + (rating - 7.0) * 10;
            } else if (rating >= 6) {
                num = 65 + (rating - 6.0) * 15;
            } else if (rating >= 5) {
                num = 50 + (rating - 5.0) * 15;
            } else if (rating >= 4) {
                num = 32 + (rating - 4.0) * 18;
            } else if (rating >= 3) {
                num = 24 + (rating - 3.0) * 8;
            } else {
                num = 10 + (rating - 1.0) * 7;
            }
            scores.add(num);
            sum += num;
        }
        int n = scores.size();
        double mu = sum / n;

        // 新公式参数（与上同）
        double a = 0.012;
        double c = 0.05;
        double K = 500.0;
        double alpha = 0.04;
        double beta = 0.0001;

        double f = 1 + a * Math.log(1 + n);

        double sumSq = 0;
        for (double s : scores) {
            sumSq += (s - mu) * (s - mu);
        }
        double V = sumSq / n;
        double g = 1 - c * V / (V + K);

        double D = 0;
        for (double s : scores) {
            double diff = s - 60.0;
            D += diff * diff * diff;
        }
        double u = D / n;
        double h = 1 + alpha * Math.tanh(beta * u);

        return mu * f * g * h;
    }

    public String getScoreDescription(double rawScore) {
        String res = "未知";
        if (rawScore >= 9.5) {
            res = "巅峰";
        }
        else if (rawScore >= 9.0) {
            res = "惊艳";
        }
        else if (rawScore >= 8.0) {
            res = "杰出";
        }
        else if (rawScore >= 7.0) {
            res = "优秀";
        }
        else if (rawScore >= 6.0) {
            res = "不错";
        }
        else if (rawScore >= 5.0) {
            res = "合格";
        }
        else if (rawScore >= 3.5) {
            res = "较差";
        }
        else if (rawScore >= 2.0) {
            res = "极差";
        }
        else if (rawScore >= 1.0) {
            res = "纯石";
        }
        else if (rawScore > 0.0) {
            res = "无敌";
        }
        return res;
    }

    private double correctScore(double rawScore) {
        if (rawScore < 0) return 0.0;
        if (rawScore > 10) return 10.0;
        return rawScore;
    }

    // 曲线映射核心：两端敏感，中间平缓，上端加速大于下端
    private double calculateCurveValue(double score) {
        if (score >= 5.0) {
            // [5, 10] -> [40, 90]
            double range = 10.0 - 5.0;
            double progress = (score - 5.0) / range;
            double addedValue = (MAX_PERFORMANCE_SCORE - NEUTRAL_PERFORMANCE_SCORE) * Math.pow(progress, CURVE_POWER_UP);
            return NEUTRAL_PERFORMANCE_SCORE + addedValue;
        } else {
            // [0, 5) -> [0, 40)
            double range = 5.0;
            double progress = (5.0 - score) / range;
            double lostValue = NEUTRAL_PERFORMANCE_SCORE * Math.pow(progress, CURVE_POWER_DOWN);
            return NEUTRAL_PERFORMANCE_SCORE - lostValue;
        }
    }

    private double calculateInfoScore(double info) {
        // 信息量 0-15分
        return MAX_INFO_SCORE * Math.pow(info / 10.0, 1.5);
    }

    private double fixLowScore(double score, double valscore) {
        if (score < 2.0) {
            valscore *= 0.5;
        }
        else if (score < 3.0) {
            valscore *= 0.6;
        }
        else if (score < 3.5) {
            valscore *= 0.7;
        }
        else if (score < 4.0) {
            valscore *= 0.8;
        }
        else if (score < 5.0) {
            valscore *= 0.9;
        }
        return valscore;
    }
}