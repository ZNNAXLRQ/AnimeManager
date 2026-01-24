package com.example.animemanager.Service;

import com.example.animemanager.DTO.ImportDTO;
import com.example.animemanager.Entity.*;
import com.example.animemanager.Entity.Character;
import com.example.animemanager.Repository.*;
import com.example.animemanager.Util.JsonConfigUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataImportService {
    private final SubjectRepository subjectRepository;
    private final CharacterRepository characterRepository;
    private final PersonRepository personRepository;
    private final EpisodeRepository episodeRepository;
    private final InfoboxRepository infoboxRepository;

    private final RestTemplate restTemplate;
    private final ExecutorService executor;
    private String accessToken;
    private boolean hasToken = false;

    // 配置常量
    private static final int BATCH_SIZE = 10;
    private static final long REQUEST_TIMEOUT = 60000;
    private static final int MAX_RETRIES = 3;
    private static final long BATCH_INTERVAL_MS = 5000;
    private static final long REQUEST_INTERVAL_MS = 2000;

    @Autowired
    @Lazy
    private DataImportService self;
    private final Object SHARED_ENTITY_LOCK = new Object();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        // 1. 初始化令牌
        initializeToken();

        // 2. 配置线程池
        int corePoolSize = Math.min(4, Runtime.getRuntime().availableProcessors());
        int maxPoolSize = corePoolSize * 2;
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 3. 配置RestTemplate
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    private void initializeToken() {
        try {
            // 从配置文件读取令牌
            this.accessToken = JsonConfigUtil.readToken("config.json");
            if (accessToken != null && !accessToken.isEmpty()) {
                this.hasToken = true;
                log.info("检测到API令牌，已启用认证请求");
            } else {
                log.warn("未检测到API令牌，将使用匿名请求（可能被限流）");
                log.info("如需提高请求频率，请访问 https://bgm.tv/dev/app 创建应用并获取令牌");
                log.info("将access_token添加到config.json文件中");
                this.hasToken = false;
            }
        } catch (Exception e) {
            log.error("初始化令牌失败: {}", e.getMessage());
            this.hasToken = false;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "AnimeManager/1.0 (https://github.com/ZNNAXLRQ/AnimeManager)");
        if (hasToken && accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        // 添加接受JSON的header
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    public void DataImport() {
        log.info(">>> 开始执行后台数据同步任务");

        // 检查令牌状态
        if (!hasToken) {
            log.warn("当前未使用API令牌，同步速度将较慢（约18次/分钟）");
            log.warn("建议添加API令牌以提高效率");
        } else {
            log.info("使用API令牌，请求频率较高（约60次/分钟）");
        }

        String username = JsonConfigUtil.readUser("config.json");

        if (username == null || username.isEmpty()) {
            log.error("用户名配置为空，跳过任务");
            log.error("请在config.json中添加username字段");
            return;
        }

        try {
            log.info("正在获取用户 [{}] 的收藏列表...", username);
            List<Long> subjectIds = getUserCollectionSubjectIds(username);
            int total = subjectIds.size();
            log.info("获取完成，共需同步 {} 个动漫条目", total);
            if (total == 0) {
                log.info("未找到需要同步的动漫条目");
                return;
            }

            // 分批处理
            processSubjectsInBatches(subjectIds);

            log.info("<<< 所有数据导入任务完成");
        } catch (Exception e) {
            log.error("导入主流程异常", e);
        }
    }

    private void processSubjectsInBatches(List<Long> subjectIds) {
        List<List<Long>> batches = partitionList(subjectIds, BATCH_SIZE);
        int batchCount = batches.size();

        for (int i = 0; i < batchCount; i++) {
            log.info("处理批次 {}/{}", i + 1, batchCount);
            List<Long> batch = batches.get(i);

            List<CompletableFuture<Void>> futures = batch.stream()
                    .map(this::processSingleSubjectWithTimeout)
                    .collect(Collectors.toList());

            try {
                // 等待当前批次完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(REQUEST_TIMEOUT * batch.size(), TimeUnit.MILLISECONDS);
                log.info("批次 {}/{} 处理完成", i + 1, batchCount);
            } catch (TimeoutException e) {
                log.error("批次 {} 处理超时", i + 1);
            } catch (Exception e) {
                log.error("批次 {} 处理异常: {}", i + 1, e.getMessage());
            }

            // 批次间间隔，避免API限制
            if (i < batchCount - 1) {
                try {
                    Thread.sleep(BATCH_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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

    private CompletableFuture<Void> processSingleSubjectWithTimeout(Long subjectId) {
        return CompletableFuture.runAsync(() -> processSingleSubject(subjectId), executor)
                .orTimeout(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    log.error("处理Subject {} 超时或失败: {}", subjectId, e.getMessage());
                    return null;
                });
    }

    private void processSingleSubject(Long subjectId) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("开始处理 SubjectID: {}", subjectId);
            ImportDTO data = fetchSubjectDataParallel(subjectId);
            if (data != null && data.getSubjectJson() != null) {
                importSingleAnimeData(data);
                log.info("SubjectID: {} 处理成功", subjectId);
            } else {
                log.warn("SubjectID: {} 核心数据缺失，跳过", subjectId);
            }
        } catch (Exception e) {
            log.error("SubjectID: {} 处理失败: {}", subjectId, e.getMessage(), e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Subject {} 处理耗时: {}ms", subjectId, duration);
        }
    }

    private ImportDTO fetchSubjectDataParallel(Long subjectId) {
        ImportDTO dto = new ImportDTO();
        dto.setSubjectId(subjectId);
        String host = "https://api.bgm.tv/v0";
        HttpHeaders headers = createHeaders();

        try {
            // 1. 获取 Subject 主条目
            String subjectJson = fetchJsonDataWithRetry(host + "/subjects/" + subjectId, headers, MAX_RETRIES);
            dto.setSubjectJson(subjectJson);

            if (subjectJson == null) {
                log.warn("SubjectID: {} 主数据获取失败，终止后续请求", subjectId);
                return null;
            }

            // 2. 获取 Persons (为了避免触发 API 速率限制，可以在此处微量休眠，但在单线程内休眠不会死锁)
            smartSleep();
            dto.setPersonJson(fetchJsonDataWithRetry(host + "/subjects/" + subjectId + "/persons", headers, MAX_RETRIES));

            // 3. 获取 Characters
            smartSleep();
            dto.setCharacterJson(fetchJsonDataWithRetry(host + "/subjects/" + subjectId + "/characters", headers, MAX_RETRIES));

            // 4. 获取 Episodes
            smartSleep();
            dto.setEpisodeJson(fetchJsonDataWithRetry(host + "/episodes?subject_id=" + subjectId + "&limit=100", headers, MAX_RETRIES));

            // 填充默认值防止空指针
            if (dto.getPersonJson() == null) dto.setPersonJson("[]");
            if (dto.getCharacterJson() == null) dto.setCharacterJson("[]");
            if (dto.getEpisodeJson() == null) dto.setEpisodeJson("{\"data\": []}");

            return dto;

        } catch (Exception e) {
            log.error("SubjectID: {} 获取数据异常: {}", subjectId, e.getMessage());
            return null;
        }
    }

    // 辅助休眠方法
    private void smartSleep() {
        try {
            // 如果有令牌，间隔短一点；无令牌间隔长一点
            long sleepTime = hasToken ? 500 : 1500;
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

                log.debug("请求用户收藏URL: {}", url);

                // 请求间隔控制
                if (offset > 0) {
                    Thread.sleep(hasToken ? 1000 : 2000);
                }

                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(createHeaders()),
                        String.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode dataNode = root.path("data");

                    if (dataNode.isArray() && !dataNode.isEmpty()) {
                        for (JsonNode item : dataNode) {
                            JsonNode subjectNode = item.path("subject");
                            if (!subjectNode.isMissingNode()) {
                                Long subjectId = subjectNode.path("id").asLong();
                                if (subjectId != null && subjectId > 0) {
                                    subjectIds.add(subjectId);
                                }
                            }
                        }

                        // 检查是否还有更多数据
                        int total = root.path("total").asInt(0);
                        offset += limit;
                        if (offset >= total) {
                            hasMore = false;
                        }

                        log.debug("已获取 {}/{} 个条目", Math.min(offset, total), total);

                    } else {
                        hasMore = false;
                    }
                } else {
                    log.error("获取用户收藏失败: HTTP {}", response.getStatusCode());
                    hasMore = false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                hasMore = false;
            } catch (Exception e) {
                log.error("获取用户收藏异常: {}", e.getMessage());
                hasMore = false;
            }
        }

        log.info("共获取到 {} 个动漫条目", subjectIds.size());
        return subjectIds;
    }

    private String fetchJsonData(String url, HttpHeaders headers) {
        try {
            log.debug("请求数据: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("请求失败 {}: HTTP {}", url, response.getStatusCode());
                // 如果是429 Too Many Requests，等待更长时间
                if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("API请求过多，等待10秒后重试");
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return null;
            }
        } catch (Exception e) {
            log.error("请求失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String fetchJsonDataWithRetry(String url, HttpHeaders headers, int maxAttempts) {
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                if (Thread.currentThread().isInterrupted()) return null;

                String result = fetchJsonData(url, headers);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                attempt++;
                log.warn("请求失败 (第 {}/{} 次): {}", attempt, maxAttempts, url);

                if (attempt >= maxAttempts) {
                    log.error("请求 {} 重试{}次后失败", url, maxAttempts);
                    return null;
                }

                // 指数退避
                try {
                    long sleepTime = 1000L * (1 << (attempt - 1)); // 1, 2, 4秒...
                    log.debug("等待 {} 毫秒后重试", sleepTime);
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
        log.info("开始导入动漫数据，共 {} 个条目", requests.size());
        int successCount = 0;
        int errorCount = 0;

        for (ImportDTO request : requests) {
            try {
                importSingleAnimeData(request);
                successCount++;
                log.info("成功导入动漫: {}", request.getSubjectId());
            } catch (Exception e) {
                errorCount++;
                log.warn("导入失败 (ID: {}): {}", request.getSubjectId(), e.getMessage());
            }
        }
        log.info("导入完成！成功: {}, 失败: {}", successCount, errorCount);
    }

    @Transactional
    public void importSingleAnimeData(ImportDTO tar) throws Exception {
        long subjectId = tar.getSubjectId();
        JsonNode subjectNode = objectMapper.readTree(tar.getSubjectJson());

        if (subjectRepository.existsById(subjectId)) {
            log.info("动漫 {} 已存在，仅更新", subjectId);
            Subject subject = subjectRepository.findById(subjectId).orElse(null);
            if (subject == null) {
                throw new IllegalArgumentException("未找到目标条目");
            }

            // 更新评分
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

            subjectRepository.save(subject);
            return;
        }

        log.info("开始导入动漫ID: {}", subjectId);

        // 1. 批量解析并保存Person数据
        JsonNode personArray = objectMapper.readTree(tar.getPersonJson());
        List<Person> persons = parsePersons(personArray);

        // 2. 批量解析并保存Character数据
        JsonNode characterArray = objectMapper.readTree(tar.getCharacterJson());
        List<Character> characters = parseCharacters(characterArray, persons);

        // 3. 解析并保存Subject
        Subject subject = parseSubject(subjectNode, characters, persons);

        // 4. 批量解析并保存Episode
        JsonNode episodeData = objectMapper.readTree(tar.getEpisodeJson());
        saveEpisodes(episodeData.path("data"), subject);

        // 5. 批量保存Infobox
        saveInfoboxes(subjectNode.path("infobox"), subject);

        log.info("完成导入动漫ID: {}", subjectId);
    }

    private List<Person> parsePersons(JsonNode personsNode) {
        List<Person> persons = new ArrayList<>();
        if (personsNode.isArray()) {
            for (JsonNode personNode : personsNode) {
                Person person = parseSinglePerson(personNode);
                if (!personRepository.existsById(person.getId())) {
                    persons.add(person);
                }
            }
            // 批量保存
            if (!persons.isEmpty()) {
                synchronized (SHARED_ENTITY_LOCK) {
                    // 调用 self 代理对象的方法以触发事务
                    self.savePersonsSafe(persons);
                }
            }
        }
        return persons;
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
            // 在锁和独立事务内部再次检查
            if (!personRepository.existsById(p.getId())) {
                toSave.add(p);
            }
        }
        if (!toSave.isEmpty()) {
            personRepository.saveAll(toSave);
        }
    }

    private List<Character> parseCharacters(JsonNode charactersNode, List<Person> persons) throws ParseException {
        List<Character> characters = new ArrayList<>();
        if (charactersNode.isArray()) {
            Set<Long> existingPersonIds = persons.stream()
                    .map(Person::getId)
                    .collect(Collectors.toSet());

            List<Person> newPersonsToSave = new ArrayList<>();
            for (JsonNode characterNode : charactersNode) {
                Character character = new Character();
                character.setId(characterNode.path("id").asLong());
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

                JsonNode actorsNode = characterNode.path("actors");
                if (actorsNode.isArray()) {
                    List<Person> casts = new ArrayList<>();
                    for (JsonNode actorNode : actorsNode) {
                        long actorId = actorNode.path("id").asLong();
                        Person person = persons.stream()
                                .filter(p -> p.getId().equals(actorId))
                                .findFirst()
                                .orElse(null);

                        if (person == null && !existingPersonIds.contains(actorId)) {
                            person = parseSinglePerson(actorNode);
                            existingPersonIds.add(actorId);
                            persons.add(person);
                            newPersonsToSave.add(person);
                        }

                        if (person != null) {
                            casts.add(person);
                        }
                    }
                    character.setCasts(casts);
                }
                if (!characterRepository.existsById(character.getId())) {
                    characters.add(character);
                }
            }

            // 批量保存
            synchronized (SHARED_ENTITY_LOCK) {
                // 1. 先安全保存新发现的声优 (Person)
                if (!newPersonsToSave.isEmpty()) {
                    self.savePersonsSafe(newPersonsToSave);
                }
                // 2. 再安全保存角色 (Character)
                if (!characters.isEmpty()) {
                    self.saveCharactersSafe(characters);
                }
            }
        }
        return characters;
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

            // 批量保存
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

            // 批量保存
            if (!infoboxes.isEmpty()) {
                infoboxRepository.saveAll(infoboxes);
            }
        }
    }

    public void shutdown() {
        log.info("关闭数据导入服务...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("数据导入服务已关闭");
    }
}