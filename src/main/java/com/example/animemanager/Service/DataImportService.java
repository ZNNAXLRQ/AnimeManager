package com.example.animemanager.Service;

import com.example.animemanager.DTO.ImportDTO;
import com.example.animemanager.Entity.*;
import com.example.animemanager.Entity.Character;
import com.example.animemanager.Repository.*;
import com.example.animemanager.Util.JsonConfigUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class DataImportService {
    private final SubjectRepository subjectRepository;
    private final CharacterRepository characterRepository;
    private final PersonRepository personRepository;
    private final EpisodeRepository episodeRepository;
    private final InfoboxRepository infoboxRepository;

    private final ExecutorService executor;
    private String accessToken;
    private String username;
    private String proxy;
    private boolean hasToken = false;
    private boolean hasUsername = false;
    private boolean hasProxy = false;

    private static final int BATCH_SIZE = 20;
    private static final long REQUEST_TIMEOUT = 60000;
    private static final int MAX_RETRIES = 3;
    private static final long BATCH_INTERVAL_MS = 500;

    @Autowired
    @Lazy
    private DataImportService self;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public DataImportService(
            SubjectRepository subjectRepository,
            CharacterRepository characterRepository,
            PersonRepository personRepository,
            EpisodeRepository episodeRepository,
            InfoboxRepository infoboxRepository) {
        this.subjectRepository = subjectRepository;
        this.characterRepository = characterRepository;
        this.personRepository = personRepository;
        this.episodeRepository = episodeRepository;
        this.infoboxRepository = infoboxRepository;

        initializeToken();
        testProxyIfNeeded();

        int corePoolSize = Math.min(4, Runtime.getRuntime().availableProcessors());
        int maxPoolSize = corePoolSize * 2;
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private void initializeToken() {
        try {
            this.accessToken = JsonConfigUtil.readToken("Data/config.json");
            this.username = JsonConfigUtil.readUser("Data/config.json");
            if (accessToken != null && !accessToken.isEmpty()) {
                this.hasToken = true;
                System.out.println("[DataImport] 检测到API令牌，已启用认证请求");
            } else {
                System.out.println("[DataImport] 未检测到API令牌，将使用匿名请求（可能被限流）");
                System.out.println("[DataImport] 如需提高请求频率，请访问 https://bgm.tv/dev/app 创建应用并获取令牌");
                System.out.println("[DataImport] 将token添加到config.json文件中");
                this.hasToken = false;
            }
            if (username != null && !username.isEmpty()) {
                this.hasUsername = true;
                System.out.println("[DataImport] 检测到用户 " + username);
            } else {
                System.out.println("[DataImport] 未设置用户, 请尽快设置用户名");
                System.out.println("[DataImport] 将Bangumi账号填入config.json文件中");
            }
        } catch (Exception e) {
            System.err.println("[DataImport] 初始化设置失败: " + e.getMessage());
            this.hasToken = false;
        }
    }

    private void testProxyIfNeeded() {
        // 读取代理配置
        this.proxy = JsonConfigUtil.readProxy("Data/config.json");
        if (proxy != null && !proxy.isEmpty()) {
            System.out.println("[Proxy] 检测到代理配置，正在进行连通性测试...");
            String testUrl = "https://api.bgm.tv/v0/subjects/1";
            HttpHeaders headers = createHeaders();
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        testUrl,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    System.out.println("[Proxy] 代理连通性测试成功（Bangumi API 可访问）");
                    this.hasProxy = true;
                } else {
                    System.out.println("[Proxy] 代理连通性测试失败: HTTP " + response.getStatusCode());
                    this.hasProxy = false;
                }
            } catch (Exception e) {
                System.out.println("[Proxy] 代理连通性测试失败: " + e.getMessage());
                this.hasProxy = false;
            }
        } else {
            System.out.println("[Proxy] 未配置代理，跳过代理连通性测试");
            this.hasProxy = false;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "ZNNAXLRQ/AnimeManager (https://github.com/ZNNAXLRQ/AnimeManager)");
        if (hasToken && accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    public CompletableFuture<Void> startCollect() {
        System.out.println("[DataImport] 接收到数据导入请求，准备在后台线程池执行...");
        return CompletableFuture.runAsync(() -> {
            try {
                this.DataImport();
            } catch (Exception e) {
                System.err.println("[DataImport] 后台数据导入过程中发生异常: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, this.executor).whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("[DataImport] !!! 数据导入任务失败: " + ex.getMessage());
            } else {
                System.out.println("[DataImport] === 数据导入任务已全部顺利完成 ===");
            }
        });
    }

    public void DataImport() {
        System.out.println("[DataImport] >>> 开始执行后台数据同步任务");

        if (!hasToken) {
            System.out.println("[DataImport] 当前未使用API令牌，同步速度将较慢（约18次/分钟）");
            System.out.println("[DataImport] 建议添加API令牌以提高效率");
        } else {
            System.out.println("[DataImport] 使用API令牌，请求频率较高（约60次/分钟）");
        }

        if (!hasUsername) {
            System.err.println("[DataImport] 用户名配置为空，跳过任务");
            System.err.println("[DataImport] 请在config.json中添加username字段");
            return;
        }

        if (!hasProxy) {
            System.err.println("[DataImport] 当前未配置代理，无法连接至Bangumi获取数据");
            System.err.println("[DataImport] 请在config.json中配置proxy字段");
        }

        try {
            System.out.println("[DataImport] 正在获取用户 [" + username + "] 的收藏列表...");
            List<Long> subjectIds = getUserCollectionSubjectIds(username);
            int total = subjectIds.size();
            System.out.println("[DataImport] 获取完成，共需同步 " + total + " 个动画条目");
            if (total == 0) return;

            List<Long> failedIds = processSubjectsInBatches(subjectIds);
            System.out.println("[DataImport] 第一轮导入完成，成功 " + (total - failedIds.size()) + " 个，失败 " + failedIds.size() + " 个");

            if (!failedIds.isEmpty()) {
                List<Long> finalFailed = retryFailedSubjects(failedIds, 3);
                if (!finalFailed.isEmpty()) {
                    System.err.println("[DataImport] 以下条目最终导入失败，共 " + finalFailed.size() + " 个：");
                    finalFailed.forEach(id -> System.err.println("[DataImport] 失败ID: " + id));
                } else {
                    System.out.println("[DataImport] 所有条目已成功导入！");
                }
            } else {
                System.out.println("[DataImport] 所有条目一次性导入成功！");
            }
        } catch (Exception e) {
            System.err.println("[DataImport] 导入主流程异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<Long> getUserCollectionSubjectIds(String username) {
        List<Long> subjectIds = new ArrayList<>();
        int limit = 100;
        int offset = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = String.format("https://api.bgm.tv/v0/users/%s/collections?limit=%d&offset=%d",
                        username, limit, offset);

                if (offset > 0) {
                    Thread.sleep(hasToken ? 1000 : 2000);
                }

                HttpHeaders headers = createHeaders();
                String responseBody = fetchJsonDataWithRetry(url, headers, MAX_RETRIES);
                if (responseBody == null) {
                    System.err.println("[DataImport] 获取用户收藏失败（重试后仍失败），offset=" + offset);
                    break;
                }

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode dataNode = root.path("data");

                if (dataNode.isArray() && !dataNode.isEmpty()) {
                    for (JsonNode item : dataNode) {
                        JsonNode subjectNode = item.path("subject");
                        if (!subjectNode.isMissingNode()) {
                            int type = subjectNode.path("type").asInt(-1);
                            if (type == 2) {
                                Long subjectId = subjectNode.path("id").asLong();
                                if (subjectId != null && subjectId > 0) {
                                    subjectIds.add(subjectId);
                                }
                            }
                        }
                    }

                    int total = root.path("total").asInt(0);
                    offset += limit;
                    if (offset >= total) {
                        hasMore = false;
                    }
                } else {
                    hasMore = false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                hasMore = false;
            } catch (Exception e) {
                System.err.println("[DataImport] 获取用户收藏异常: " + e.getMessage());
                hasMore = false;
            }
        }

        System.out.println("[DataImport] 共获取到 " + subjectIds.size() + " 个动画条目（已过滤非动画类型）");
        return subjectIds;
    }

    private List<Long> processSubjectsInBatches(List<Long> subjectIds) {
        List<List<Long>> batches = partitionList(subjectIds, BATCH_SIZE);
        Set<Long> failedIds = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < batches.size(); i++) {
            System.out.println("[DataImport] 处理批次 " + (i + 1) + "/" + batches.size());
            List<Long> batch = batches.get(i);

            List<CompletableFuture<Boolean>> futures = batch.stream()
                    .map(this::processSingleSubjectWithTimeout)
                    .collect(Collectors.toList());

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(REQUEST_TIMEOUT * batch.size(), TimeUnit.MILLISECONDS);
                System.out.println("[DataImport] 批次 " + (i + 1) + "/" + batches.size() + " 处理完成");
            } catch (TimeoutException e) {
                System.err.println("[DataImport] 批次 " + (i + 1) + " 处理超时");
            } catch (Exception e) {
                System.err.println("[DataImport] 批次 " + (i + 1) + " 处理异常: " + e.getMessage());
            }

            for (int j = 0; j < futures.size(); j++) {
                try {
                    Boolean success = futures.get(j).getNow(false);
                    if (!success) {
                        failedIds.add(batch.get(j));
                    }
                } catch (Exception e) {
                    failedIds.add(batch.get(j));
                }
            }

            if (i < batches.size() - 1) {
                try {
                    Thread.sleep(BATCH_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return new ArrayList<>(failedIds);
    }

    private List<Long> retryFailedSubjects(List<Long> failedIds, int maxRetries) {
        if (failedIds.isEmpty()) return Collections.emptyList();
        Set<Long> currentFailed = new HashSet<>(failedIds);
        Set<Long> lastFailed = new HashSet<>();

        for (int retry = 1; retry <= maxRetries; retry++) {
            if (currentFailed.isEmpty()) break;
            System.out.println("[DataImport] 第 " + retry + " 次重试，共 " + currentFailed.size() + " 个失败条目");

            List<Long> retryList = new ArrayList<>(currentFailed);
            List<Long> newFailed = processSubjectsInBatches(retryList);

            lastFailed.clear();
            lastFailed.addAll(currentFailed);
            currentFailed.clear();
            currentFailed.addAll(newFailed);

            if (currentFailed.isEmpty()) {
                System.out.println("[DataImport] 所有条目已成功导入！");
                break;
            }
            if (currentFailed.size() == lastFailed.size()) {
                System.out.println("[DataImport] 重试后失败条目数不再减少（" + currentFailed.size() + "），停止重试");
                break;
            }
        }
        return new ArrayList<>(currentFailed);
    }

    private CompletableFuture<Boolean> processSingleSubjectWithTimeout(Long subjectId) {
        long timeout = hasToken ? REQUEST_TIMEOUT : REQUEST_TIMEOUT * 3;
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        return processSingleSubject(subjectId);
                    } catch (Exception e) {
                        System.err.println("[DataImport] 处理Subject " + subjectId + " 发生异常: " + e.getMessage());
                        return false;
                    }
                }, executor).orTimeout(timeout, TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    System.err.println("[DataImport] 处理Subject " + subjectId + " 超时或失败: " + e.getMessage());
                    return false;
                });
    }

    private boolean processSingleSubject(Long subjectId) {
        long startTime = System.currentTimeMillis();
        try {
            System.out.println("[DataImport] 开始处理 SubjectID: " + subjectId);

            if (subjectRepository.existsById(subjectId)) {
                System.out.println("[DataImport] 动漫 " + subjectId + " 已存在，仅获取主数据进行更新");
                self.updateExistingAnime(subjectId);
            } else {
                System.out.println("[DataImport] 动漫 " + subjectId + " 不存在，准备获取完整数据");
                ImportDTO data = fetchSubjectDataParallel(subjectId);
                if (data != null && data.getSubjectJson() != null) {
                    self.importSingleAnimeData(data);
                    System.out.println("[DataImport] SubjectID: " + subjectId + " 新增处理成功");
                } else {
                    System.out.println("[DataImport] SubjectID: " + subjectId + " 核心数据缺失，跳过");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("[DataImport] SubjectID: " + subjectId + " 处理失败: " + e.getMessage());
            return false;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
        }
    }

    public void updateExistingAnime(Long subjectId) throws Exception {
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) return;

        if (isRecentSubject(subject.getDate())) {
            System.out.println("[DataImport] 动漫 " + subjectId + " 放送时间在近一年内，执行全量更新");
            self.fullyUpdateAnime(subjectId);
        } else {
            System.out.println("[DataImport] 动漫 " + subjectId + " 放送时间超过一年，仅更新评分");
            self.updateRatingOnly(subjectId);
        }
    }

    private boolean isRecentSubject(String date) {
        if (date == null || date.trim().isEmpty()) {
            return true;
        }
        LocalDate parsed = parseSubjectDate(date.trim());
        if (parsed == null) {
            return true;
        }
        LocalDate threshold = LocalDate.now().minusYears(1);
        return !parsed.isBefore(threshold);
    }

    private LocalDate parseSubjectDate(String date) {
        String cleaned = date.trim();
        try {
            if (cleaned.length() == 4) {
                return LocalDate.of(Integer.parseInt(cleaned), 1, 1);
            }
            if (cleaned.length() == 7) {
                YearMonth ym = YearMonth.parse(cleaned);
                return ym.atDay(1);
            }
            return LocalDate.parse(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void updateRatingOnly(Long subjectId) throws Exception {
        String host = "https://api.bgm.tv/v0";
        HttpHeaders headers = createHeaders();

        String subjectJson = fetchJsonDataWithRetry(host + "/subjects/" + subjectId, headers, MAX_RETRIES);
        if (subjectJson == null) {
            System.out.println("[DataImport] 更新时获取 SubjectID: " + subjectId + " 主数据失败");
            return;
        }

        JsonNode subjectNode = objectMapper.readTree(subjectJson);
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) return;

        JsonNode ratingNode = subjectNode.path("rating");
        if (!ratingNode.isMissingNode() && !ratingNode.isEmpty()) {
            Rating rating = subject.getRating();
            if (rating == null) rating = new Rating();
            rating.setRank(ratingNode.path("rank").asInt());
            rating.setTotal(ratingNode.path("total").asInt(0));
            rating.setScore(ratingNode.path("score").asDouble(0.0));
            rating.setInformation(rating.getInformation() != null ? rating.getInformation() : 0.0);
            rating.setStory(rating.getStory() != null ? rating.getStory() : 0.0);
            rating.setCharacter(rating.getCharacter() != null ? rating.getCharacter() : 0.0);
            rating.setQuality(rating.getQuality() != null ? rating.getQuality() : 0.0);
            rating.setAtmosphere(rating.getAtmosphere() != null ? rating.getAtmosphere() : 0.0);
            rating.setLove(rating.getLove() != null ? rating.getLove() : 0.0);
            rating.setTotalscore(rating.getTotalscore() != null ? rating.getTotalscore() : 0.0);
            subject.setRating(rating);
            subjectRepository.save(subject);
            System.out.println("[DataImport] 动漫 " + subjectId + " 评分更新成功");
        }
    }

    @Transactional
    public void fullyUpdateAnime(Long subjectId) throws Exception {
        ImportDTO data = fetchSubjectDataParallel(subjectId);
        if (data == null || data.getSubjectJson() == null) {
            System.out.println("[DataImport] 全量更新 SubjectID: " + subjectId + " 获取数据失败，跳过");
            return;
        }

        JsonNode subjectNode = objectMapper.readTree(data.getSubjectJson());
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) return;

        // 1. 同步 Person
        Map<Long, Person> personMap = new HashMap<>();
        List<Person> allPersons = new ArrayList<>();
        List<Person> newPersons = new ArrayList<>();
        JsonNode personArray = objectMapper.readTree(data.getPersonJson());

        Map<Long, Person> existingPersons = new HashMap<>();
        if (personArray.isArray() && !personArray.isEmpty()) {
            List<Long> personIds = new ArrayList<>();
            for (JsonNode personNode : personArray) {
                long id = personNode.path("id").asLong();
                if (id > 0) personIds.add(id);
            }
            for (Person p : personRepository.findAllById(personIds)) {
                existingPersons.put(p.getId(), p);
            }
        }

        if (personArray.isArray()) {
            for (JsonNode personNode : personArray) {
                long id = personNode.path("id").asLong();
                if (id <= 0 || personMap.containsKey(id)) continue;
                Person person = existingPersons.get(id);
                if (person == null) {
                    person = parseSinglePerson(personNode);
                    newPersons.add(person);
                } else {
                    applyPersonFields(person, personNode);
                }
                personMap.put(id, person);
                allPersons.add(person);
            }
        }

        if (!newPersons.isEmpty()) {
            newPersons.sort(Comparator.comparing(Person::getId));
            batchMergePersons(newPersons);
        }

        // 2. 同步 Character
        Map<Long, Character> characterMap = new HashMap<>();
        List<Character> allCharacters = new ArrayList<>();
        List<Character> newCharacters = new ArrayList<>();
        JsonNode characterArray = objectMapper.readTree(data.getCharacterJson());

        Map<Long, Character> existingCharacters = new HashMap<>();
        if (characterArray.isArray() && !characterArray.isEmpty()) {
            List<Long> characterIds = new ArrayList<>();
            for (JsonNode characterNode : characterArray) {
                long id = characterNode.path("id").asLong();
                if (id > 0) characterIds.add(id);
            }
            for (Character c : characterRepository.findAllById(characterIds)) {
                existingCharacters.put(c.getId(), c);
            }
        }

        if (characterArray.isArray()) {
            for (JsonNode characterNode : characterArray) {
                long id = characterNode.path("id").asLong();
                if (id <= 0 || characterMap.containsKey(id)) continue;
                Character character = existingCharacters.get(id);
                if (character == null) {
                    character = new Character();
                    character.setId(id);
                    character.setAttitude(0);
                    applyCharacterFields(character, characterNode);
                    newCharacters.add(character);
                } else {
                    applyCharacterFields(character, characterNode);
                }

                JsonNode actorsNode = characterNode.path("actors");
                if (actorsNode.isArray()) {
                    List<Person> casts = new ArrayList<>();
                    for (JsonNode actorNode : actorsNode) {
                        long actorId = actorNode.path("id").asLong();
                        Person person = personMap.get(actorId);
                        if (person == null) {
                            person = existingPersons.get(actorId);
                        }
                        if (person == null) {
                            person = personRepository.findById(actorId).orElse(null);
                        }
                        if (person == null) {
                            person = parseSinglePerson(actorNode);
                            personRepository.save(person);
                        }
                        personMap.put(actorId, person);
                        casts.add(person);
                    }
                    character.setCasts(casts);
                }

                characterMap.put(id, character);
                allCharacters.add(character);
            }
        }

        if (!newCharacters.isEmpty()) {
            batchMergeCharacters(newCharacters);
        }

        // 3. 更新 Subject 主字段
        applySubjectFields(subject, subjectNode);

        // 4. 重建 Episodes
        rebuildEpisodes(data.getEpisodeJson(), subject);

        // 5. 重建 Infobox
        rebuildInfoboxes(subjectNode.path("infobox"), subject);

        // 6. 同步关联集合
        subject.getCharacters().clear();
        subject.getCharacters().addAll(allCharacters);
        subject.getPersons().clear();
        subject.getPersons().addAll(allPersons);
        subjectRepository.save(subject);

        System.out.println("[DataImport] 动漫 " + subjectId + " 全量更新成功");
    }

    private void applySubjectFields(Subject subject, JsonNode subjectNode) {
        subject.setName(subjectNode.path("name").asText());
        subject.setNameCn(subjectNode.path("name_cn").asText());
        subject.setDate(subjectNode.path("date").asText());
        subject.setPlatform(subjectNode.path("platform").asText());
        subject.setSummary(subjectNode.path("summary").asText());
        subject.setEps(subjectNode.path("eps").asInt(subject.getEps() != null ? subject.getEps() : 0));
        subject.setVolumes(subjectNode.path("volumes").asInt(subject.getVolumes() != null ? subject.getVolumes() : 0));
        subject.setSeries(subjectNode.path("series").asBoolean(false));
        subject.setLocked(subjectNode.path("locked").asBoolean(false));
        subject.setNsfw(subjectNode.path("nsfw").asBoolean(false));
        subject.setType(subjectNode.path("type").asInt(subject.getType() != null ? subject.getType() : 0));

        JsonNode imagesNode = subjectNode.path("images");
        if (!imagesNode.isMissingNode() && !imagesNode.isEmpty()) {
            Images images = subject.getImages();
            if (images == null) images = new Images();
            images.setSmall(imagesNode.path("small").asText());
            images.setGrid(imagesNode.path("grid").asText());
            images.setLarge(imagesNode.path("large").asText());
            images.setMedium(imagesNode.path("medium").asText());
            images.setCommon(imagesNode.path("common").asText());
            subject.setImages(images);
        }

        JsonNode ratingNode = subjectNode.path("rating");
        if (!ratingNode.isMissingNode() && !ratingNode.isEmpty()) {
            Rating rating = subject.getRating();
            if (rating == null) rating = new Rating();
            rating.setRank(ratingNode.path("rank").asInt());
            rating.setTotal(ratingNode.path("total").asInt(0));
            rating.setScore(ratingNode.path("score").asDouble(rating.getScore() != null ? rating.getScore() : 0.0));
            rating.setInformation(rating.getInformation() != null ? rating.getInformation() : 0.0);
            rating.setStory(rating.getStory() != null ? rating.getStory() : 0.0);
            rating.setCharacter(rating.getCharacter() != null ? rating.getCharacter() : 0.0);
            rating.setQuality(rating.getQuality() != null ? rating.getQuality() : 0.0);
            rating.setAtmosphere(rating.getAtmosphere() != null ? rating.getAtmosphere() : 0.0);
            rating.setLove(rating.getLove() != null ? rating.getLove() : 0.0);
            rating.setTotalscore(rating.getTotalscore() != null ? rating.getTotalscore() : 0.0);
            subject.setRating(rating);
        }
    }

    private void applyPersonFields(Person person, JsonNode personNode) {
        person.setName(personNode.path("name").asText());
        person.setShortSummary(personNode.path("short_summary").asText());
        person.setType(personNode.path("type").asInt(person.getType() != null ? person.getType() : 0));
        person.setLocked(personNode.path("locked").asBoolean(false));

        JsonNode imagesNode = personNode.path("images");
        if (!imagesNode.isMissingNode() && !imagesNode.isEmpty()) {
            Images images = person.getImages();
            if (images == null) images = new Images();
            images.setSmall(imagesNode.path("small").asText());
            images.setGrid(imagesNode.path("grid").asText());
            images.setLarge(imagesNode.path("large").asText());
            images.setMedium(imagesNode.path("medium").asText());
            person.setImages(images);
        }

        JsonNode careersNode = personNode.path("career");
        if (careersNode.isArray()) {
            List<String> careers = new ArrayList<>();
            for (JsonNode careerNode : careersNode) {
                careers.add(careerNode.asText());
            }
            person.setCareers(careers);
        }
    }

    private void applyCharacterFields(Character character, JsonNode characterNode) {
        character.setName(characterNode.path("name").asText());
        character.setSummary(characterNode.path("summary").asText());
        character.setRelation(characterNode.path("relation").asText());
        character.setType(characterNode.path("type").asInt(character.getType() != null ? character.getType() : 0));

        JsonNode imagesNode = characterNode.path("images");
        if (!imagesNode.isMissingNode() && !imagesNode.isEmpty()) {
            Images images = character.getImages();
            if (images == null) images = new Images();
            images.setSmall(imagesNode.path("small").asText());
            images.setGrid(imagesNode.path("grid").asText());
            images.setLarge(imagesNode.path("large").asText());
            images.setMedium(imagesNode.path("medium").asText());
            character.setImages(images);
        }
    }

    private void rebuildEpisodes(String episodeJson, Subject subject) throws Exception {
        Map<Long, Integer> oldAttitude = new HashMap<>();
        List<Episode> oldEpisodes = episodeRepository.findBySubject(subject);
        for (Episode ep : oldEpisodes) {
            oldAttitude.put(ep.getId(), ep.getAttitude() != null ? ep.getAttitude() : 0);
        }

        if (!oldEpisodes.isEmpty()) {
            episodeRepository.deleteAll(oldEpisodes);
            episodeRepository.flush();
        }

        JsonNode episodesNode = objectMapper.readTree(episodeJson).path("data");
        if (episodesNode.isArray() && !episodesNode.isEmpty()) {
            List<Episode> episodes = new ArrayList<>();
            for (JsonNode episodeNode : episodesNode) {
                Episode episode = new Episode();
                episode.setId(episodeNode.path("id").asLong());
                episode.setEp(episodeNode.path("ep").asInt());
                episode.setName(episodeNode.path("name").asText());
                episode.setNameCn(episodeNode.path("name_cn").asText());
                episode.setDuration(episodeNode.path("duration").asText());
                episode.setDescription(episodeNode.path("desc").asText());
                episode.setAirdate(episodeNode.path("airdate").asText());
                episode.setAttitude(oldAttitude.getOrDefault(episode.getId(), 0));
                episode.setSubject(subject);
                episodes.add(episode);
            }
            episodeRepository.saveAll(episodes);
        }
    }

    private void rebuildInfoboxes(JsonNode infoboxNode, Subject subject) {
        List<Infobox> oldInfoboxes = infoboxRepository.findBySubject(subject);
        if (!oldInfoboxes.isEmpty()) {
            infoboxRepository.deleteAll(oldInfoboxes);
            infoboxRepository.flush();
        }

        if (infoboxNode.isArray() && !infoboxNode.isEmpty()) {
            List<Infobox> infoboxes = new ArrayList<>();
            for (JsonNode infoboxItem : infoboxNode) {
                Infobox infobox = new Infobox();
                infobox.setKey(infoboxItem.path("key").asText());
                infobox.setValue(infoboxItem.path("value").asText());
                infobox.setSubject(subject);
                infoboxes.add(infobox);
            }
            infoboxRepository.saveAll(infoboxes);
        }
    }

    private ImportDTO fetchSubjectDataParallel(Long subjectId) {
        ImportDTO dto = new ImportDTO();
        dto.setSubjectId(subjectId);
        String host = "https://api.bgm.tv/v0";
        HttpHeaders headers = createHeaders();

        try {
            String subjectJson = fetchJsonDataWithRetry(host + "/subjects/" + subjectId, headers, MAX_RETRIES);
            dto.setSubjectJson(subjectJson);
            if (subjectJson == null) {
                System.out.println("[DataImport] SubjectID: " + subjectId + " 主数据获取失败，终止后续请求");
                return null;
            }

            smartSleep();

            CompletableFuture<String> personFuture = CompletableFuture.supplyAsync(
                    () -> fetchJsonDataWithRetry(host + "/subjects/" + subjectId + "/persons", headers, MAX_RETRIES),
                    executor);
            CompletableFuture<String> characterFuture = CompletableFuture.supplyAsync(
                    () -> fetchJsonDataWithRetry(host + "/subjects/" + subjectId + "/characters", headers, MAX_RETRIES),
                    executor);
            CompletableFuture<String> episodeFuture = CompletableFuture.supplyAsync(
                    () -> fetchJsonDataWithRetry(host + "/episodes?subject_id=" + subjectId + "&limit=100", headers, MAX_RETRIES),
                    executor);

            dto.setPersonJson(personFuture.join());
            dto.setCharacterJson(characterFuture.join());
            dto.setEpisodeJson(episodeFuture.join());

            if (dto.getPersonJson() == null) dto.setPersonJson("[]");
            if (dto.getCharacterJson() == null) dto.setCharacterJson("[]");
            if (dto.getEpisodeJson() == null) dto.setEpisodeJson("{\"data\": []}");

            return dto;
        } catch (Exception e) {
            System.err.println("[DataImport] SubjectID: " + subjectId + " 获取数据异常: " + e.getMessage());
            return null;
        }
    }

    private void smartSleep() {
        try {
            long sleepTime = hasToken ? 700 : 2000;
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String fetchJsonData(String url, HttpHeaders headers) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                System.err.println("[DataImport] 请求失败 " + url + ": HTTP " + response.getStatusCode());
                if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    System.out.println("[DataImport] API请求过多，等待10秒后重试");
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return null;
            }
        } catch (Exception e) {
            System.err.println("[DataImport] 请求失败 " + url + ": " + e.getMessage());
            return null;
        }
    }

    private String fetchJsonDataWithRetry(String url, HttpHeaders headers, int maxAttempts) {
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                if (Thread.currentThread().isInterrupted()) return null;
                String result = fetchJsonData(url, headers);
                if (result != null) return result;
            } catch (Exception e) {
                attempt++;
                System.out.println("[DataImport] 请求失败 (第 " + attempt + "/" + maxAttempts + " 次): " + url);
                if (attempt >= maxAttempts) {
                    System.err.println("[DataImport] 请求 " + url + " 重试" + maxAttempts + "次后失败");
                    return null;
                }
                try {
                    long sleepTime = 1000L * (1L << (attempt - 1));
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    @Transactional
    public void importBatchData(List<ImportDTO> requests) {
        System.out.println("[DataImport] 开始导入动漫数据，共 " + requests.size() + " 个条目");
        int successCount = 0, errorCount = 0;
        for (ImportDTO request : requests) {
            try {
                importSingleAnimeData(request);
                successCount++;
                System.out.println("[DataImport] 成功导入动漫: " + request.getSubjectId());
            } catch (Exception e) {
                errorCount++;
                System.out.println("[DataImport] 导入失败 (ID: " + request.getSubjectId() + "): " + e.getMessage());
            }
        }
        System.out.println("[DataImport] 导入完成！成功: " + successCount + ", 失败: " + errorCount);
    }

    @Transactional
    public void importSingleAnimeData(ImportDTO tar) throws Exception {
        long subjectId = tar.getSubjectId();
        JsonNode subjectNode = objectMapper.readTree(tar.getSubjectJson());
        System.out.println("[DataImport] 开始导入新动漫ID: " + subjectId);

        Map<Long, Person> personMap = new HashMap<>();
        List<Person> newPersons = new ArrayList<>();
        JsonNode personArray = objectMapper.readTree(tar.getPersonJson());
        parsePersons(personArray, personMap, newPersons);

        Map<Long, Character> characterMap = new HashMap<>();
        List<Character> newCharacters = new ArrayList<>();
        JsonNode characterArray = objectMapper.readTree(tar.getCharacterJson());
        parseCharacters(characterArray, personMap, newPersons, characterMap, newCharacters);

        if (!newPersons.isEmpty()) {
            newPersons.sort(Comparator.comparing(Person::getId));
            batchMergePersons(newPersons);
            System.out.println("[DataImport] 合并了 " + newPersons.size() + " 个Person");
        }

        if (!newCharacters.isEmpty()) {
            batchMergeCharacters(newCharacters);
            System.out.println("[DataImport] 保存了 " + newCharacters.size() + " 个新Character");
        }

        List<Character> allCharacters = new ArrayList<>(characterMap.values());
        Subject subject = parseSubject(subjectNode, allCharacters, new ArrayList<>(personMap.values()));

        JsonNode episodeData = objectMapper.readTree(tar.getEpisodeJson());
        saveEpisodes(episodeData.path("data"), subject);
        saveInfoboxes(subjectNode.path("infobox"), subject);
        System.out.println("[DataImport] 完成导入新动漫ID: " + subjectId);
    }

    private void batchMergePersons(List<Person> persons) {
        String sql = "MERGE INTO persons (person_id, name, short_summary, person_type, locked, " +
                "small_image_url, grid_image_url, large_image_url, medium_image_url, common_image_url) " +
                "KEY(person_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Person p = persons.get(i);
                Images images = p.getImages();
                ps.setLong(1, p.getId());
                ps.setString(2, p.getName());
                ps.setString(3, p.getShortSummary());
                ps.setInt(4, p.getType());
                ps.setBoolean(5, p.getLocked());
                ps.setString(6, images != null ? images.getSmall() : null);
                ps.setString(7, images != null ? images.getGrid() : null);
                ps.setString(8, images != null ? images.getLarge() : null);
                ps.setString(9, images != null ? images.getMedium() : null);
                ps.setString(10, images != null ? images.getCommon() : null);
            }
            @Override
            public int getBatchSize() {
                return persons.size();
            }
        });

        String deleteCareersSql = "DELETE FROM person_careers WHERE person_id = ?";
        String insertCareersSql = "INSERT INTO person_careers (person_id, career) VALUES (?, ?)";
        for (Person p : persons) {
            jdbcTemplate.update(deleteCareersSql, p.getId());
            List<String> careers = p.getCareers();
            if (careers != null && !careers.isEmpty()) {
                List<Object[]> batchArgs = new ArrayList<>();
                for (String career : careers) {
                    batchArgs.add(new Object[]{p.getId(), career});
                }
                jdbcTemplate.batchUpdate(insertCareersSql, batchArgs);
            }
        }
    }

    private void batchMergeCharacters(List<Character> characters) {
        if (characters.isEmpty()) return;
        String sql = "MERGE INTO characters (character_id, name, summary, relation, character_type, attitude, " +
                "small_image_url, grid_image_url, large_image_url, medium_image_url, common_image_url) " +
                "KEY(character_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Character c = characters.get(i);
                Images images = c.getImages();
                ps.setLong(1, c.getId());
                ps.setString(2, c.getName());
                ps.setString(3, c.getSummary());
                ps.setString(4, c.getRelation());
                ps.setInt(5, c.getType());      // 对应 character_type 列
                ps.setInt(6, c.getAttitude());
                ps.setString(7, images != null ? images.getSmall() : null);
                ps.setString(8, images != null ? images.getGrid() : null);
                ps.setString(9, images != null ? images.getLarge() : null);
                ps.setString(10, images != null ? images.getMedium() : null);
                ps.setString(11, images != null ? images.getCommon() : null);
            }
            @Override
            public int getBatchSize() {
                return characters.size();
            }
        });

        // 处理 casts 关联表（表名和列名与实体映射一致）
        String deleteCastsSql = "DELETE FROM character_cast WHERE character_id = ?";
        String insertCastsSql = "INSERT INTO character_cast (character_id, person_id) VALUES (?, ?)";
        for (Character c : characters) {
            jdbcTemplate.update(deleteCastsSql, c.getId());
            List<Person> casts = c.getCasts();
            if (casts != null && !casts.isEmpty()) {
                List<Object[]> batchArgs = new ArrayList<>();
                for (Person person : casts) {
                    batchArgs.add(new Object[]{c.getId(), person.getId()});
                }
                jdbcTemplate.batchUpdate(insertCastsSql, batchArgs);
            }
        }
    }

    private void parsePersons(JsonNode personsNode, Map<Long, Person> personMap, List<Person> newPersons) {
        if (personsNode.isArray()) {
            for (JsonNode personNode : personsNode) {
                long id = personNode.path("id").asLong();
                if (personMap.containsKey(id)) continue;
                Optional<Person> existing = personRepository.findById(id);
                if (existing.isPresent()) {
                    personMap.put(id, existing.get());
                } else {
                    Person person = parseSinglePerson(personNode);
                    personMap.put(id, person);
                    newPersons.add(person);
                }
            }
        }
    }

    private Person parseSinglePerson(JsonNode personNode) {
        Person person = new Person();
        person.setId(personNode.path("id").asLong());
        person.setName(personNode.path("name").asText());
        person.setShortSummary(personNode.path("short_summary").asText());
        person.setType(personNode.path("type").asInt(0));
        person.setLocked(personNode.path("locked").asBoolean(false));
        JsonNode imagesNode = personNode.path("images");
        if (!imagesNode.isMissingNode() && !imagesNode.isEmpty()) {
            Images images = new Images();
            images.setSmall(imagesNode.path("small").asText());
            images.setGrid(imagesNode.path("grid").asText());
            images.setLarge(imagesNode.path("large").asText());
            images.setMedium(imagesNode.path("medium").asText());
            person.setImages(images);
        }
        JsonNode careersNode = personNode.path("career");
        if (careersNode.isArray() && !careersNode.isEmpty()) {
            List<String> careers = new ArrayList<>();
            for (JsonNode careerNode : careersNode) {
                careers.add(careerNode.asText());
            }
            person.setCareers(careers);
        }
        return person;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePersonsSafe(List<Person> persons) {
        List<Person> toSave = new ArrayList<>();
        for (Person p : persons) {
            if (!personRepository.existsById(p.getId())) {
                toSave.add(p);
            }
        }
        if (!toSave.isEmpty()) {
            personRepository.saveAll(toSave);
        }
    }

    private void parseCharacters(JsonNode charactersNode, Map<Long, Person> personMap, List<Person> newPersons,
                                 Map<Long, Character> characterMap, List<Character> newCharacters) throws ParseException {
        if (charactersNode.isArray()) {
            for (JsonNode characterNode : charactersNode) {
                long id = characterNode.path("id").asLong();
                if (characterMap.containsKey(id)) continue;
                Character character;
                Optional<Character> existing = characterRepository.findById(id);
                if (existing.isPresent()) {
                    character = existing.get();
                } else {
                    character = new Character();
                    character.setId(id);
                    character.setName(characterNode.path("name").asText());
                    character.setSummary(characterNode.path("summary").asText());
                    character.setRelation(characterNode.path("relation").asText());
                    character.setType(characterNode.path("type").asInt(0));
                    character.setAttitude(0);
                    JsonNode imagesNode = characterNode.path("images");
                    if (!imagesNode.isMissingNode() && !imagesNode.isEmpty()) {
                        Images images = new Images();
                        images.setSmall(imagesNode.path("small").asText());
                        images.setGrid(imagesNode.path("grid").asText());
                        images.setLarge(imagesNode.path("large").asText());
                        images.setMedium(imagesNode.path("medium").asText());
                        character.setImages(images);
                    }
                    newCharacters.add(character);
                }
                JsonNode actorsNode = characterNode.path("actors");
                if (actorsNode.isArray()) {
                    List<Person> casts = new ArrayList<>();
                    for (JsonNode actorNode : actorsNode) {
                        long actorId = actorNode.path("id").asLong();
                        Person person = personMap.get(actorId);
                        if (person == null) {
                            Optional<Person> existingPerson = personRepository.findById(actorId);
                            if (existingPerson.isPresent()) {
                                person = existingPerson.get();
                                personMap.put(actorId, person);
                            } else {
                                person = parseSinglePerson(actorNode);
                                personMap.put(actorId, person);
                                newPersons.add(person);
                            }
                        }
                        casts.add(person);
                    }
                    character.setCasts(casts);
                }
                characterMap.put(id, character);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCharactersSafe(List<Character> characters) {
        List<Character> toSave = new ArrayList<>();
        for (Character c : characters) {
            if (!characterRepository.existsById(c.getId())) {
                toSave.add(c);
            }
        }
        if (!toSave.isEmpty()) {
            characterRepository.saveAll(toSave);
        }
    }

    private Subject parseSubject(JsonNode subjectNode, List<Character> characters, List<Person> persons) throws ParseException {
        Subject subject = new Subject();
        subject.setId(subjectNode.path("id").asLong());
        subject.setName(subjectNode.path("name").asText());
        subject.setNameCn(subjectNode.path("name_cn").asText());
        subject.setDate(subjectNode.path("date").asText());
        subject.setPlatform(subjectNode.path("platform").asText());
        subject.setSummary(subjectNode.path("summary").asText());
        subject.setEps(subjectNode.path("eps").asInt(0));
        subject.setVolumes(subjectNode.path("volumes").asInt(0));
        subject.setSeries(subjectNode.path("series").asBoolean(false));
        subject.setLocked(subjectNode.path("locked").asBoolean(false));
        subject.setNsfw(subjectNode.path("nsfw").asBoolean(false));
        subject.setType(subjectNode.path("type").asInt(0));
        JsonNode imagesNode = subjectNode.path("images");
        if (!imagesNode.isMissingNode() && !imagesNode.isEmpty()) {
            Images images = new Images();
            images.setSmall(imagesNode.path("small").asText());
            images.setGrid(imagesNode.path("grid").asText());
            images.setLarge(imagesNode.path("large").asText());
            images.setMedium(imagesNode.path("medium").asText());
            images.setCommon(imagesNode.path("common").asText());
            subject.setImages(images);
        }
        JsonNode ratingNode = subjectNode.path("rating");
        if (!ratingNode.isMissingNode() && !ratingNode.isEmpty()) {
            Rating rating = new Rating();
            rating.setRank(ratingNode.path("rank").asInt());
            rating.setTotal(ratingNode.path("total").asInt(0));
            rating.setScore(ratingNode.path("score").asDouble(0.0));
            rating.setInformation(0.0);
            rating.setStory(0.0);
            rating.setCharacter(0.0);
            rating.setQuality(0.0);
            rating.setAtmosphere(0.0);
            rating.setLove(0.0);
            rating.setTotalscore(0.0);
            subject.setRating(rating);
        }
        subject.setCharacters(characters);
        subject.setPersons(persons);
        return subjectRepository.save(subject);
    }

    private void saveEpisodes(JsonNode episodesNode, Subject subject) throws ParseException {
        if (episodesNode.isArray()) {
            List<Episode> episodes = new ArrayList<>();
            for (JsonNode episodeNode : episodesNode) {
                Episode episode = new Episode();
                episode.setId(episodeNode.path("id").asLong());
                episode.setEp(episodeNode.path("ep").asInt());
                episode.setName(episodeNode.path("name").asText());
                episode.setNameCn(episodeNode.path("name_cn").asText());
                episode.setDuration(episodeNode.path("duration").asText());
                episode.setDescription(episodeNode.path("desc").asText());
                episode.setAttitude(0);
                episode.setSubject(subject);
                episode.setAirdate(episodeNode.path("airdate").asText());
                if (!episodeRepository.existsById(episode.getId())) {
                    episodes.add(episode);
                }
            }
            if (!episodes.isEmpty()) {
                episodeRepository.saveAll(episodes);
            }
        }
    }

    private void saveInfoboxes(JsonNode infoboxNode, Subject subject) {
        if (infoboxNode.isArray()) {
            List<Infobox> infoboxes = new ArrayList<>();
            for (JsonNode infoboxItem : infoboxNode) {
                Infobox infobox = new Infobox();
                infobox.setKey(infoboxItem.path("key").asText());
                JsonNode valueNode = infoboxItem.path("value");
                infobox.setValue(valueNode.asText());
                infobox.setSubject(subject);
                infoboxes.add(infobox);
            }
            if (!infoboxes.isEmpty()) {
                infoboxRepository.saveAll(infoboxes);
            }
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("[DataImport] 关闭线程池...");
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}