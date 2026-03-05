# あにめManager - 本地化评分与统计程序

## 📖 简介

あにめManager 是一款基于 [Bangumi 番组计划](https://bgm.tv/) 数据的本地化评分与统计工具。它通过 Bangumi API 同步用户收藏的动漫条目，并提供多维度的自定义评分、标签管理、角色/剧集态度标记以及丰富的筛选和排序功能。程序核心特色在于两套评分体系：**本地总分**（单个条目的自定义评分）和**加权均值**（基于列表统计的综合得分），帮助用户更客观地量化自己的观看体验。

## 🧩 功能概览

- **数据同步**：通过 Bangumi API 拉取用户收藏的动漫条目，包括基本信息、制作人员、角色、剧集、标签等，存储于本地 H2 数据库。支持并建议使用 Access Token 提高请求频率。
- **主界面**：
    - 以卡片列表展示条目，包含封面、标题、放送日期、Rank、Bangumi 评分、本地总分及星级显示。
    - 支持按中文名、Rank、Bangumi 评分、本地总分、放送日期、ID 排序（正序/逆序）。
    - 支持按名称（中文/原文）实时搜索。
    - 高级筛选：按标签、制作人员、角色态度（±1）、剧集态度（±1）以及放送日期范围筛选条目。
    - 一键计算当前列表中所有条目的 Bangumi 水平分、本地水平分及二者差值。
- **条目详情页**：
    - 显示完整信息框、标签、制作人员、角色、剧集列表。
    - **六维评分**：对每个条目从信息、故事、人物、喜爱、视听、氛围六个维度打分（0-10），系统实时计算本地总分并给出评级、评论和建议。
    - **角色/剧集态度**：对每个角色和剧集标记 “喜欢”、“不喜欢” 或 “中立”，这些态度可用于主界面筛选。
    - **标签管理**：支持添加现有标签、创建新标签、选择标签关联到当前条目，并可切换到删除模式批量删除标签。
- **日志查看器**：在界面底部可展开日志抽屉，实时查看程序运行日志（包括 System.out 和 System.err 输出）。

## ⚙️ 技术栈

- **前端**：JavaFX (FXML)
- **后端**：Spring Boot (非 Web 模式)
- **数据库**：H2 (嵌入式，JPA/Hibernate)
- **API 客户端**：RestTemplate
- **构建工具**：Maven
- **其他**：Lombok, Jackson, Ikonli (图标库)

## 🧮 核心分数计算详解

程序包含两套核心分数：**本地总分（totalscore）** 和 **加权均值（level score）**。加权均值又分为 Bangumi 加权分和本地加权分，两者对数值进行统一化处理, 以保证结果的标准化。

### 1. 本地总分（totalscore）

本地总分是针对单个条目的综合评分，由用户输入的六个维度分（信息、故事、人物、喜爱、视听、氛围）通过非线性加权计算得出。

**计算公式**（`ScoreCalculatorService.calculateTotalScore`）：

```
totalScore = BASE_SCORE + infoScore + performanceScore
```

- **BASE_SCORE** = 10.0（保底分）
- **infoScore** = MAX_INFO_SCORE × (info/10)^1.5，其中 MAX_INFO_SCORE = 15.0
- **performanceScore** = Σ (各维度价值分 × 对应权重)

各维度价值分通过曲线映射函数 `calculateCurveValue` 计算：

- 当维度分 score ≥ 5.0：
  ```
  value = NEUTRAL_PERFORMANCE_SCORE + (MAX_PERFORMANCE_SCORE - NEUTRAL_PERFORMANCE_SCORE) × ((score-5)/5)^CURVE_POWER_UP
  ```
- 当维度分 score < 5.0：
  ```
  value = NEUTRAL_PERFORMANCE_SCORE - NEUTRAL_PERFORMANCE_SCORE × ((5-score)/5)^CURVE_POWER_DOWN
  ```

其中：
- `NEUTRAL_PERFORMANCE_SCORE` = 40.0（5分对应的价值分）
- `MAX_PERFORMANCE_SCORE` = 90.0（10分对应的价值分）
- `CURVE_POWER_UP` = 2.2（上半区加速指数）
- `CURVE_POWER_DOWN` = 0.6（下半区减速指数）

**额外惩罚**：
- 若任一维度分低于 3.0，则该维度价值分额外乘以系数（`fixLowScore`）：低于 2.0 乘 0.5，低于 2.5 乘 0.6，低于 3.0 乘 0.7。
- 若任一维度分低于 3.0（故事、人物、视听、氛围、喜爱五个维度），则 performanceScore 整体再乘 0.8。
- 若信息分 info < 5.0，则总分再乘 0.8。

**权重**（默认值，可从配置文件 `config.json` 的 `anime_weights` 字段读取）：
- 故事：0.25
- 人物：0.20
- 视听：0.20
- 氛围：0.20
- 喜爱：0.15

**总分范围**：约 10 ～ 115 分。

**数据来源**：用户在前端详情页为每个条目输入的六个维度分，保存于 `Rating` 实体的对应字段（`information`、`story`、`character`、`quality`、`atmosphere`、`love`），计算出的 `totalscore` 也存入该实体。

### 2. 加权均值（level score）

加权均值用于评估一个条目集合（例如当前筛选出的列表）的整体水平。程序提供两种加权均值：
- **Bangumi 加权分**：基于条目自带的 Bangumi 评分计算。
- **本地加权分**：基于条目的本地总分（totalscore）计算。

两种分数的计算方法完全相同，唯一区别在于输入的原始分数不同。

**计算公式**（`ScoreCalculatorService.calculateBangumiLevel` / `calculateLocallevel`）：

给定一个列表的分数集合 `scores`（长度 n），计算步骤：

1. **平均值**：
   ```
   μ = (Σ scores) / n
   ```

2. **作品数因子 f**：
   ```
   f = 1 + a × ln(1 + n)
   ```
   其中 a = 0.012。数量越多，分数略有提升。


3. **方差因子 g**：
   ```
   V = Σ (scores_i - μ)² / n
   g = 1 - c × V / (V + K)
   ```
   其中 c = 0.05，K = 500.0。方差越大，分数越低，且受饱和常数 K 限制。


4. **偏移因子 h**：
   ```
   u = Σ (scores_i - 60)³ / n
   h = 1 + α × tanh(β × u)
   ```
   其中 α = 0.04，β = 0.0001。该因子反映分数分布相对于 60 分的偏移程度：高分多（正偏）提升分数，低分多（负偏）降低分数，通过 tanh 非线性压缩。


5. **最终加权均值**：
   ```
   level = μ × f × g × h
   ```

**Bangumi 评分的预处理**：由于 Bangumi 原生评分（0-10）与本地总分量纲不同，在计算 Bangumi 水平分前需将每个条目的 Bangumi 评分映射到本地总分相近的量级（约 10-115）。映射规则（`calculateBangumiLevel` 方法中）：

- rating ≥ 10：`num = 115 - (10.5 - rating) / 0.5 * 10`
- 9 ≤ rating < 10：`num = 105 - (10 - rating) * 10`
- 8 ≤ rating < 9：`num = 95 - (9 - rating) * 10`
- 7 ≤ rating < 8：`num = 85 - (8 - rating) * 15`
- 6 ≤ rating < 7：`num = 70 - (7 - rating) * 10`
- 5 ≤ rating < 6：`num = 60 - (5 - rating) * 12`
- 4 ≤ rating < 5：`num = 48 - (4 - rating) * 16`
- 3 ≤ rating < 4：`num = 32 - (3 - rating) * 8`
- rating < 3：`num = 10 + (rating - 1.5) * 14`

然后将 `num` 作为该条目的分数参与上述加权均值计算。

**数据来源**：
- Bangumi 水平分：`Subject.getRating().getScore()`（Bangumi 原始评分）经映射后使用。
- 本地水平分：`Subject.getRating().getTotalscore()`（本地总分）。

## 📁 主要代码文件说明

| 文件 | 功能 |
|------|------|
| `Main.java` | Spring Boot 启动类，同时作为 JavaFX Application 入口，初始化 Spring 上下文并加载主界面。 |
| `MainController.java` | 主界面控制器，管理条目列表、排序、筛选、日志抽屉等。 |
| `SubjectController.java` | 详情页控制器，处理六维评分、标签管理、角色/剧集态度等。 |
| `DataImportService.java` | 数据同步服务，调用 Bangumi API 获取用户收藏及相关数据，使用 JPA/Hibernate 存入 H2。 |
| `FilterService.java` | 筛选服务，提供按标签、制作人员、态度等的数据库查询。 |
| `ScoreCalculatorService.java` | 评分计算服务，包含本地总分计算和加权均值计算的全部逻辑。 |
| `SubjectService.java` | 条目服务，提供缓存、更新评分、更新标签等操作。 |
| `JsonConfigUtil.java` | 配置文件工具，读取 `config.json` 中的用户名、令牌、权重等。 |
| `LogCollector.java` | 日志收集器，将 `System.out/err` 重定向到 JavaFX 界面。 |
| Entity 包 | JPA 实体类：`Subject`、`Rating`、`Character`、`Person`、`Episode`、`Tag`、`Infobox` 等。 |
| Repository 包 | Spring Data JPA 仓库接口。 |

## 🔧 使用说明

### 环境要求
- Java 17 或更高版本
- Maven（可选，若需要自行编译）

### 快速开始
1. **配置 Bangumi 账号**：在 `src/main/resources/com/example/animemanager/Data/config.json` 中添加：
   ```json
   {
     "username": "你的Bangumi用户名",
     "token": "你的Bangumi API令牌（可选，但强烈建议）",
     "anime_weights": {
       "story": 0.25,
       "character": 0.20,
       "visual": 0.20,
       "atmosphere": 0.20,
       "love": 0.15
     }
   }
   ```
    - 令牌可在 [Bangumi AccessToken页面](https://next.bgm.tv/demo/access-token/create) 获取。
2. **运行程序**：
    - 直接运行 `AppLaunch.main()`
3. **首次使用**：点击主界面“更新数据”按钮，程序将开始同步你的收藏列表（可能需要较长时间，取决于收藏数量）。
4. **浏览与评分**：
    - 点击列表中的条目进入详情页。
    - 在六个文本框中输入 0-10 的分数（支持小数），按回车或点击 “计算并保存” 即可看到总分、评级和建议Bangumi评分。
    - 点击左侧信息栏标签页面 “添加” 按钮可添加标签, 点击 "删除" 按钮切换删除模式。
    - 角色和剧集旁的按钮可标记态度, 用于记录并筛选喜欢或不喜欢的人物与单集。
5. **筛选与统计**：
    - 使用顶部的筛选控件按标签、人员、态度、日期范围筛选。
    - 点击 “计算均分” 查看当前列表的 Bangumi 加权分、本地加权分及差值。

## 📊 评分机制可视化

- **雷达图**：详情页展示六维评分的雷达图，鼠标悬停在顶点可查看该维度得分及描述（“巅峰”、“惊艳”……）。
- **星级**：主界面列表中以星级（实心星、半星、空心星）直观表示本地总分档次。

## 📝 注意事项

- 数据同步时若未配置令牌，请求频率受限（约 18 次/分钟），同步速度较慢；配置令牌后可提升至约 60 次/分钟。
- 数据库默认存储于用户目录下的 `animemanager.mv.db`，可通过配置文件修改路径。
- 日志抽屉收集所有标准输出和错误输出，便于调试。

---

あにめManager 设计初衷为辅助本人进行年度总结相关数据的整理, 同时由于专业水平的限制, 程序功能不算强大, 仅仅是对Bangumi现有功能的一些优化和个人化处理。

后续程序大概率不会继续更新, 如果对现有功能不够满意或对计算模式有新的想法请自行修改, 这方面AI可以胜任, 也欢迎大手子contribute, 也欢迎具有相似评价标准的同好使用。