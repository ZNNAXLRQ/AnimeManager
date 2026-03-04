package com.example.animemanager.Service;

import com.example.animemanager.Entity.Subject;
import com.example.animemanager.Repository.SubjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FilterService {

    @Autowired
    private SubjectRepository subjectRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public List<Subject> filterByTag(String keyword) {
        return subjectRepository.findByTagNameContaining(keyword.trim());
    }

    public List<Subject> filterByPerson(String keyword) {
        return subjectRepository.findByPersonNameContaining(keyword.trim());
    }

    public List<Subject> filterByCharacterAttitude(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            // 空关键词：返回态度为1或-1的所有条目（去重）
            List<Subject> result = new ArrayList<>();
            result.addAll(subjectRepository.findByCharacterAttitude(1));
            result.addAll(subjectRepository.findByCharacterAttitude(-1));
            return result.stream().distinct().collect(Collectors.toList());
        }
        String trimmed = keyword.trim();
        if ("+1".equals(trimmed) || "1".equals(trimmed)) {
            return subjectRepository.findByCharacterAttitude(1);
        } else if ("-1".equals(trimmed)) {
            return subjectRepository.findByCharacterAttitude(-1);
        } else {
            // 其他输入，返回空列表（可根据业务调整）
            return Collections.emptyList();
        }
    }

    public List<Subject> filterByEpisodeAttitude(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            List<Subject> result = new ArrayList<>();
            result.addAll(subjectRepository.findByEpisodeAttitude(1));
            result.addAll(subjectRepository.findByEpisodeAttitude(-1));
            return result.stream().distinct().collect(Collectors.toList());
        }
        String trimmed = keyword.trim();
        if ("+1".equals(trimmed) || "1".equals(trimmed)) {
            return subjectRepository.findByEpisodeAttitude(1);
        } else if ("-1".equals(trimmed)) {
            return subjectRepository.findByEpisodeAttitude(-1);
        } else {
            return Collections.emptyList();
        }
    }

    public List<Subject> filterSubjectsByDateRange(List<Subject> subjects,
                                                   String startDate,
                                                   String endDate) {
        if (subjects == null || subjects.isEmpty()) return subjects;

        String start = normalizeDate(startDate);
        String end = normalizeDate(endDate);

        // 无有效日期条件，直接返回原列表
        if (start == null && end == null) return subjects;

        return subjects.stream()
                .filter(s -> {
                    String date = s.getDate();
                    if (date == null) return false;               // 无日期的条目排除
                    if (start != null && date.compareTo(start) < 0) return false;
                    if (end != null && date.compareTo(end) > 0) return false;
                    return true;
                })
                .collect(Collectors.toList());
    }

    // 辅助方法：处理纯关键词筛选
    private List<Subject> createQuery(String jpql, String keyword) {
        return entityManager.createQuery(jpql, Subject.class)
                .setParameter("keyword", "%" + (keyword != null ? keyword.trim() : "") + "%")
                .getResultList();
    }

    private String normalizeDate(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String trimmed = input.trim();
        // 将 '.' 替换为 '-'
        String normalized = trimmed.replace('.', '-');
        // 简单验证是否为 yyyy-MM-dd 格式
        if (normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return normalized;
        }
        return null; // 无法解析则忽略该条件
    }

}