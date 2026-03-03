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

    // 辅助方法：处理纯关键词筛选
    private List<Subject> createQuery(String jpql, String keyword) {
        return entityManager.createQuery(jpql, Subject.class)
                .setParameter("keyword", "%" + (keyword != null ? keyword.trim() : "") + "%")
                .getResultList();
    }
}