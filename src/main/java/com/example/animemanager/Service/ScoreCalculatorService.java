package com.example.animemanager.Service;

import com.example.animemanager.Util.JsonConfigUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
        Map<String, Double> weights = JsonConfigUtil.readAnimeWeights("config.json");
        if (weights == null) weights = new HashMap<>();

        ANIME_WEIGHTS = weights;
        WEIGHT_STORY = ANIME_WEIGHTS.getOrDefault("story", 0.25);
        WEIGHT_CHARACTER = ANIME_WEIGHTS.getOrDefault("character", 0.20);
        WEIGHT_VISUAL = ANIME_WEIGHTS.getOrDefault("visual", 0.20);
        WEIGHT_ATMOSPHERE = ANIME_WEIGHTS.getOrDefault("atmosphere", 0.20);
        WEIGHT_LOVE = ANIME_WEIGHTS.getOrDefault("love", 0.15);
    }

    public ScoreCalculatorService() {
    }

    // 核心评分逻辑
    public static double calculateTotalScore(double infoRaw, double storyRaw, double characterRaw, double visualRaw, double atmosphereRaw, double loveRaw) {
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
        if (story < 3.0 || character < 3.0 || visual < 3.0 || atmosphere < 3.0 || love < 3.0) {
            performanceScore *= 0.8;
        }

        // 4. 计算信息量得分 (满分15分)
        double infoScore = calculateInfoScore(info);

        // 5. 计算初步总分
        // 结构：保底(10) + 信息(0-15) + 表现(0-90) = Max 115
        double totalScore = BASE_SCORE + infoScore + performanceScore;
        if (info < 5.0) {
            // 信息量总分打折
            totalScore *= 0.8;
        }


        // 7. 最终兜底，确保不低于BASE
        return Math.max(BASE_SCORE, totalScore);
    }

    public static Map<String, String> AnimeReport(double info, double story, double character, double visual, double atmosphere, double love) {
        double totalScore = calculateTotalScore(info, story, character, visual, atmosphere, love);

        String grade, comment, advice;
        double temp = 0;

        // 评级系统 (115分制)
        if (totalScore >= 105) {
            grade = "✦ 神作 ✦";
            comment = "难以超越的巅峰之作";
            temp = (totalScore - 105) / 10 + 10.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 95) {
            grade = "★ 准神作 ★";
            comment = "绝对能打的顶尖之作";
            temp = (totalScore - 95) / 10 + 9.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 85) {
            grade = "★★★★★";
            comment = "不得不看的杰出之作";
            temp = (totalScore - 85) / 10 + 8.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 70) {
            grade = "★★★★☆";
            comment = "值得一看的优秀之作";
            temp = (totalScore - 70) / 15 + 7.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 60) {
            grade = "★★★☆☆";
            comment = "可以去看的不错之作";
            temp = (totalScore - 60) / 10 + 6.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 48) {
            grade = "★★☆☆☆";
            comment = "随便看看的平庸之作";
            temp = (totalScore - 48) / 12 + 5.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 32) {
            grade = "★☆☆☆☆";
            comment = "勉强能看的瑕疵之作";
            temp = (totalScore - 32) / 16 + 4.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else if (totalScore >= 24) {
            grade = "☆☆☆☆☆";
            comment = "为啥要看的无趣之作";
            temp = (totalScore - 24) / 8 + 3.0;
            advice = "bangumi-" + String.format("%.3f", temp);
        } else {
            grade = "☆ 纯石 ☆";
            comment = "怀疑人生的逆天之作";
            temp = totalScore / 24 + 1.0;
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

    public static String getScoreDescription(double rawScore) {
        int scoreInt = (int) Math.round(rawScore);
        return switch (scoreInt) {
            case 10 -> "巅峰";
            case 9 -> "惊艳";
            case 8 -> "杰出";
            case 7 -> "优秀";
            case 6 -> "不错";
            case 5 -> "合格";
            case 4 -> "较差";
            case 3 -> "极差";
            case 2 -> "纯石";
            case 1 -> "逆天";
            default -> rawScore >= 0 ? "无敌" : "未知";
        };
    }

    private static double correctScore(double rawScore) {
        if (rawScore < 0) return 0.0;
        if (rawScore > 10) return 10.0;
        return rawScore;
    }

    // 曲线映射核心：两端敏感，中间平缓，上端加速大于下端
    private static double calculateCurveValue(double score) {
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

    private static double calculateInfoScore(double info) {
        // 信息量 0-15分
        return MAX_INFO_SCORE * Math.pow(info / 10.0, 1.5);
    }

    private static double fixLowScore(double score, double valscore) {
        if (score < 2.0) {
            valscore *= 0.5;
        }
        else if (score < 2.5) {
            valscore *= 0.6;
        }
        else if (score < 3.0) {
            valscore *= 0.7;
        }
        return valscore;
    }
}