package com.example.reading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.UserBookshelf;
import com.example.reading.mapper.SysBookMapper;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.IBookRecommendationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.guoshiqiufeng.dify.chat.DifyChat;
import io.github.guoshiqiufeng.dify.chat.dto.request.ChatMessageSendRequest;
import io.github.guoshiqiufeng.dify.chat.dto.response.ChatMessageSendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BookRecommendationServiceImpl implements IBookRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(BookRecommendationServiceImpl.class);
    private static final int HOME_RECOMMEND_LIMIT = 8;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final String RECOMMEND_CACHE_KEY_PREFIX = "recommend:uid:";
    private static final long RECOMMEND_CACHE_EXPIRE_HOURS = 2L;
    private static final String CIRCUIT_BREAKER_KEY = "recommend:dify:circuit_breaker";
    private static final long CIRCUIT_BREAKER_EXPIRE_MINUTES = 5L;

    private final UserBookshelfMapper userBookshelfMapper;
    private final SysBookMapper sysBookMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final DifyChat difyChat;

    @Value("${ai.enable-recommendation:true}")
    private boolean recommendationEnabled;

    @Value("${dify.recommend.api-key:}")
    private String recommendDifyApiKey;

    public BookRecommendationServiceImpl(UserBookshelfMapper userBookshelfMapper,
                                         SysBookMapper sysBookMapper,
                                         ObjectMapper objectMapper,
                                         @Autowired(required = false) StringRedisTemplate redisTemplate,
                                         DifyChat difyChat) {
        this.userBookshelfMapper = userBookshelfMapper;
        this.sysBookMapper = sysBookMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.difyChat = difyChat;
    }

    private final Map<String, LocalCacheItem> localCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class LocalCacheItem {
        String value;
        long expireTime;
    }

    @Override
    public List<SysBook> recommendHomeBooks(Long userId, boolean refresh) {
        if (!recommendationEnabled || userId == null) {
            return randomFallback();
        }

        try {
            if (!refresh) {
                List<SysBook> cachedBooks = readRecommendCache(userId);
                if (!cachedBooks.isEmpty()) {
                    return cachedBooks;
                }
            }

            boolean isBreakerOpen = false;
            if (redisTemplate != null) {
                try {
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(CIRCUIT_BREAKER_KEY))) {
                        isBreakerOpen = true;
                    }
                } catch (Exception e) {}
            }
            if (!isBreakerOpen) {
                LocalCacheItem cb = localCache.get(CIRCUIT_BREAKER_KEY);
                if (cb != null && System.currentTimeMillis() < cb.expireTime) {
                    isBreakerOpen = true;
                }
            }

            if (isBreakerOpen) {
                log.info("AI recommendation circuit breaker is open, using random fallback directly for userId={}", userId);
                List<SysBook> fallbackBooks = randomFallback();
                if (!refresh) {
                    writeRecommendCache(userId, fallbackBooks);
                }
                return fallbackBooks;
            }

            Set<Long> targetBookIds = getUserBookIds(userId);
            if (targetBookIds.isEmpty()) {
                return randomFallback();
            }

            Map<Long, SysBook> targetBookMap = listBooksByIds(targetBookIds).stream()
                    .collect(Collectors.toMap(SysBook::getId, book -> book, (left, right) -> left, LinkedHashMap::new));
            List<SysBook> allPublicBooks = listRecommendableBooks();
            if (allPublicBooks.isEmpty()) {
                return Collections.emptyList();
            }

            Map<Long, SysBook> publicBookMap = allPublicBooks.stream()
                    .collect(Collectors.toMap(SysBook::getId, book -> book, (left, right) -> left, LinkedHashMap::new));

            List<CandidateScore> collaborativeCandidates =
                    buildCollaborativeCandidates(userId, targetBookIds, publicBookMap.keySet());
            List<Long> rankedIds =
                    rankWithDify(userId, targetBookMap, collaborativeCandidates, publicBookMap, HOME_RECOMMEND_LIMIT);
            List<SysBook> books =
                    assembleBooks(rankedIds, collaborativeCandidates, allPublicBooks, publicBookMap, targetBookIds);
            writeRecommendCache(userId, books);
            return books;
        } catch (Exception ex) {
            log.warn("AI recommendation failed, fallback to random recommendation. userId={}, reason={}", userId, ex.getMessage());

            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(CIRCUIT_BREAKER_KEY, "1", CIRCUIT_BREAKER_EXPIRE_MINUTES, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("Failed to set circuit breaker", e);
                }
            }
            LocalCacheItem lc = new LocalCacheItem();
            lc.value = "1";
            lc.expireTime = System.currentTimeMillis() + CIRCUIT_BREAKER_EXPIRE_MINUTES * 60 * 1000L;
            localCache.put(CIRCUIT_BREAKER_KEY, lc);

            List<SysBook> fallbackBooks = randomFallback();
            writeRecommendCache(userId, fallbackBooks);
            return fallbackBooks;
        }
    }

    private List<SysBook> readRecommendCache(Long userId) {
        try {
            String cacheKey = buildCacheKey(userId);
            String cacheValue = null;
            if (redisTemplate != null) {
                try {
                    cacheValue = redisTemplate.opsForValue().get(cacheKey);
                } catch (Exception e) {}
            }
            if (cacheValue == null) {
                LocalCacheItem item = localCache.get(cacheKey);
                if (item != null && System.currentTimeMillis() < item.expireTime) {
                    cacheValue = item.value;
                }
            }

            if (!StringUtils.hasText(cacheValue)) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(cacheValue);
            if (!root.isArray()) {
                return Collections.emptyList();
            }

            List<SysBook> books = new ArrayList<>();
            for (JsonNode node : root) {
                SysBook book = objectMapper.treeToValue(node, SysBook.class);
                if (book != null) {
                    books.add(book);
                }
            }
            return books;
        } catch (Exception ex) {
            log.warn("Failed to read recommendation cache. userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private void writeRecommendCache(Long userId, List<SysBook> books) {
        try {
            String cacheKey = buildCacheKey(userId);
            String json = objectMapper.writeValueAsString(books);

            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(cacheKey, json, RECOMMEND_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                    return;
                } catch (Exception e) {}
            }

            LocalCacheItem item = new LocalCacheItem();
            item.value = json;
            item.expireTime = System.currentTimeMillis() + RECOMMEND_CACHE_EXPIRE_HOURS * 3600 * 1000L;
            localCache.put(cacheKey, item);
        } catch (Exception ex) {
            log.warn("Failed to write recommendation cache. userId={}", userId, ex);
        }
    }

    private String buildCacheKey(Long userId) {
        return RECOMMEND_CACHE_KEY_PREFIX + userId;
    }

    private Set<Long> getUserBookIds(Long userId) {
        QueryWrapper<UserBookshelf> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        return userBookshelfMapper.selectList(query).stream()
                .map(UserBookshelf::getBookId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<SysBook> listRecommendableBooks() {
        QueryWrapper<SysBook> query = new QueryWrapper<>();
        query.and(wrapper -> wrapper.eq("status", 2).or().isNull("status"));
        query.orderByDesc("id");
        return sysBookMapper.selectList(query);
    }

    private List<SysBook> listBooksByIds(Set<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysBookMapper.selectBatchIds(bookIds);
    }

    private List<CandidateScore> buildCollaborativeCandidates(Long userId,
                                                              Set<Long> targetBookIds,
                                                              Set<Long> publicBookIds) {
        QueryWrapper<UserBookshelf> overlapQuery = new QueryWrapper<>();
        overlapQuery.in("book_id", targetBookIds).ne("user_id", userId);
        List<UserBookshelf> overlapShelves = userBookshelfMapper.selectList(overlapQuery);
        if (overlapShelves.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Set<Long>> overlapBooksByUser = new HashMap<>();
        for (UserBookshelf shelf : overlapShelves) {
            overlapBooksByUser.computeIfAbsent(shelf.getUserId(), key -> new LinkedHashSet<>()).add(shelf.getBookId());
        }

        QueryWrapper<UserBookshelf> similarUserQuery = new QueryWrapper<>();
        similarUserQuery.in("user_id", overlapBooksByUser.keySet());
        List<UserBookshelf> similarUserShelves = userBookshelfMapper.selectList(similarUserQuery);

        Map<Long, Set<Long>> allBooksBySimilarUser = new HashMap<>();
        for (UserBookshelf shelf : similarUserShelves) {
            allBooksBySimilarUser.computeIfAbsent(shelf.getUserId(), key -> new LinkedHashSet<>()).add(shelf.getBookId());
        }

        Map<Long, Double> weightByBook = new HashMap<>();
        Map<Long, Integer> supporterCountByBook = new HashMap<>();

        for (Map.Entry<Long, Set<Long>> entry : allBooksBySimilarUser.entrySet()) {
            Long similarUserId = entry.getKey();
            Set<Long> similarUserBookIds = entry.getValue();
            Set<Long> overlapBookIds = overlapBooksByUser.getOrDefault(similarUserId, Collections.emptySet());
            double similarity = calculateJaccardSimilarity(
                    targetBookIds.size(), similarUserBookIds.size(), overlapBookIds.size());
            if (similarity <= 0D) {
                continue;
            }

            for (Long bookId : similarUserBookIds) {
                if (targetBookIds.contains(bookId) || !publicBookIds.contains(bookId)) {
                    continue;
                }
                weightByBook.merge(bookId, similarity, Double::sum);
                supporterCountByBook.merge(bookId, 1, Integer::sum);
            }
        }

        return weightByBook.entrySet().stream()
                .map(entry -> new CandidateScore(
                        entry.getKey(),
                        entry.getValue(),
                        supporterCountByBook.getOrDefault(entry.getKey(), 0)))
                .sorted(Comparator
                        .comparingDouble(CandidateScore::weight).reversed()
                        .thenComparing(CandidateScore::supporterCount, Comparator.reverseOrder())
                        .thenComparing(CandidateScore::bookId, Comparator.reverseOrder()))
                .toList();
    }

    private double calculateJaccardSimilarity(int targetCount, int otherCount, int overlapCount) {
        int unionCount = targetCount + otherCount - overlapCount;
        if (unionCount <= 0) {
            return 0D;
        }
        return (double) overlapCount / unionCount;
    }

    private List<Long> rankWithDify(Long userId,
                                    Map<Long, SysBook> targetBookMap,
                                    List<CandidateScore> collaborativeCandidates,
                                    Map<Long, SysBook> publicBookMap,
                                    int limit) throws Exception {
        if (!StringUtils.hasText(recommendDifyApiKey)) {
            throw new IllegalStateException("Dify recommendation API key is missing");
        }

        List<Map<String, Object>> targetBooks = targetBookMap.values().stream()
                .map(this::toBookPayload)
                .toList();

        List<Map<String, Object>> candidateBooks = collaborativeCandidates.stream()
                .map(candidate -> {
                    SysBook book = publicBookMap.get(candidate.bookId());
                    if (book == null) {
                        return null;
                    }
                    Map<String, Object> payload = new LinkedHashMap<>(toBookPayload(book));
                    payload.put("weight", roundWeight(candidate.weight()));
                    payload.put("supporterCount", candidate.supporterCount());
                    return payload;
                })
                .filter(Objects::nonNull)
                .toList();

        List<Map<String, Object>> allBooks = publicBookMap.values().stream()
                .map(this::toBookPayload)
                .toList();

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("scene", "homepage_book_recommendation");
        inputs.put("target_user_books", objectMapper.writeValueAsString(targetBooks));
        inputs.put("collaborative_candidates", objectMapper.writeValueAsString(candidateBooks));
        inputs.put("all_books", objectMapper.writeValueAsString(allBooks));
        inputs.put("recommend_count", limit);

        ChatMessageSendRequest chatRequest = new ChatMessageSendRequest();
        chatRequest.setApiKey(recommendDifyApiKey);
        chatRequest.setUserId("recommend-user-" + userId);
        chatRequest.setContent("Generate homepage book recommendations from the provided inputs and return JSON only.");
        chatRequest.setInputs(inputs);

        ChatMessageSendResponse response = difyChat.send(chatRequest);

        if (response == null) {
            throw new IllegalStateException("Dify returned empty response");
        }

        Object result = response.getAnswer();

        if (result == null) {
            throw new IllegalStateException("Dify did not return a recommendation result");
        }

        List<Long> parsedIds = parseRecommendedIds(result, publicBookMap.keySet());
        if (parsedIds.isEmpty()) {
            throw new IllegalStateException("Cannot parse recommendation result from Dify");
        }
        return parsedIds;
    }

    private List<Long> parseRecommendedIds(Object rawResult, Collection<Long> validBookIds) throws Exception {
        List<Long> ids = new ArrayList<>();
        if (rawResult instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Number number) {
                    ids.add(number.longValue());
                } else if (item instanceof String str && str.matches("\\d+")) {
                    ids.add(Long.parseLong(str));
                }
            }
            return filterValidIds(ids, validBookIds);
        }

        String answer = String.valueOf(rawResult).trim();
        if (answer.startsWith("```")) {
            answer = answer.replace("```json", "").replace("```", "").trim();
        }

        try {
            JsonNode root = objectMapper.readTree(answer);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.canConvertToLong()) {
                        ids.add(node.asLong());
                    }
                }
            } else if (root.isObject()) {
                JsonNode bookIdsNode = root.get("bookIds");
                if (bookIdsNode != null && bookIdsNode.isArray()) {
                    for (JsonNode node : bookIdsNode) {
                        if (node.canConvertToLong()) {
                            ids.add(node.asLong());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (ids.isEmpty()) {
            Matcher matcher = NUMBER_PATTERN.matcher(answer);
            while (matcher.find()) {
                ids.add(Long.parseLong(matcher.group()));
            }
        }

        return filterValidIds(ids, validBookIds);
    }

    private List<Long> filterValidIds(List<Long> ids, Collection<Long> validBookIds) {
        Set<Long> validSet = new LinkedHashSet<>(validBookIds);
        return ids.stream()
                .filter(validSet::contains)
                .distinct()
                .toList();
    }

    private List<SysBook> assembleBooks(List<Long> rankedIds,
                                        List<CandidateScore> collaborativeCandidates,
                                        List<SysBook> allPublicBooks,
                                        Map<Long, SysBook> publicBookMap,
                                        Set<Long> targetBookIds) {
        LinkedHashSet<Long> finalIds = new LinkedHashSet<>();

        for (Long rankedId : rankedIds) {
            if (!targetBookIds.contains(rankedId) && publicBookMap.containsKey(rankedId)) {
                finalIds.add(rankedId);
            }
            if (finalIds.size() >= HOME_RECOMMEND_LIMIT) {
                break;
            }
        }

        for (CandidateScore candidate : collaborativeCandidates) {
            if (!targetBookIds.contains(candidate.bookId()) && publicBookMap.containsKey(candidate.bookId())) {
                finalIds.add(candidate.bookId());
            }
            if (finalIds.size() >= HOME_RECOMMEND_LIMIT) {
                break;
            }
        }

        List<SysBook> shuffledBooks = new ArrayList<>(allPublicBooks);
        Collections.shuffle(shuffledBooks, ThreadLocalRandom.current());
        for (SysBook book : shuffledBooks) {
            if (!targetBookIds.contains(book.getId())) {
                finalIds.add(book.getId());
            }
            if (finalIds.size() >= HOME_RECOMMEND_LIMIT) {
                break;
            }
        }

        return finalIds.stream()
                .map(publicBookMap::get)
                .filter(Objects::nonNull)
                .limit(HOME_RECOMMEND_LIMIT)
                .toList();
    }

    private Map<String, Object> toBookPayload(SysBook book) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookId", book.getId());
        payload.put("title", defaultString(book.getTitle()));
        payload.put("author", defaultString(book.getAuthor()));
        payload.put("category", defaultString(book.getCategory()));
        payload.put("tags", defaultString(book.getTags()));
        payload.put("description", abbreviate(defaultString(book.getDescription()), 300));
        return payload;
    }

    private double roundWeight(double weight) {
        return Math.round(weight * 1000D) / 1000D;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private List<SysBook> randomFallback() {
        List<SysBook> books = new ArrayList<>(sysBookMapper.selectRandomBooks());
        if (books.size() > HOME_RECOMMEND_LIMIT) {
            return books.subList(0, HOME_RECOMMEND_LIMIT);
        }
        return books;
    }

    private record CandidateScore(Long bookId, double weight, int supporterCount) {
    }
}
