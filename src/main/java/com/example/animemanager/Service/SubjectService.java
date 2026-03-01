package com.example.animemanager.Service;

import com.example.animemanager.Entity.Rating;
import com.example.animemanager.Entity.Subject;
import com.example.animemanager.Entity.Tag;
import com.example.animemanager.Repository.SubjectRepository;
import com.example.animemanager.Repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SubjectService {
    private final SubjectRepository subjectRepository;
    private final TagRepository tagRepository;
    private List<Subject> cachedSubjects = null; // 缓存基本信息

    @Autowired
    public SubjectService(SubjectRepository subjectRepository, TagRepository tagRepository) {
        this.subjectRepository = subjectRepository;
        this.tagRepository = tagRepository;
    }

    public List<Subject> getAllSubjects() {
        if (cachedSubjects == null) {
            cachedSubjects = subjectRepository.findAll();
        }
        return cachedSubjects;
    }

    public void clearCache() {
        this.cachedSubjects = null;
    }

    @Transactional
    public Subject UpdateSubject(long id, double info, double story, double character, double quality, double atmos, double love, double total) {
        Subject subject = subjectRepository.findById(id).orElse(null);
        if (subject != null) {
            Rating rating = subject.getRating();
            if (rating != null) {
                rating.setInformation(info);
                rating.setStory(story);
                rating.setCharacter(character);
                rating.setQuality(quality);
                rating.setAtmosphere(atmos);
                rating.setLove(love);
                rating.setTotalscore(total);
                subject.setRating(rating);
            } else {
                throw new RuntimeException("未找到条目评分");
            }
            Subject saved = subjectRepository.save(subject);
            clearCache(); // 数据有更新，清除缓存以便下次重新拉取
            return saved;
        } else {
            throw new RuntimeException("未找到条目");
        }
    }

    @Transactional
    public void updateSubjectTags(Long subjectId, List<Long> newTagIds) {
        // 1. 加载 subject（同时预加载 tags 集合）
        Subject subject = subjectRepository.findByIdWithTags(subjectId)
                .orElseThrow(() -> new RuntimeException("Subject not found with id: " + subjectId));

        // 2. 获取原来的标签 ID
        Set<Long> oldTagIds = subject.getTags().stream()
                .map(Tag::getTagId)
                .collect(Collectors.toSet());

        // 3. 清空原有 tags
        subject.getTags().clear();

        // 4. 加载新的标签对象（托管的）
        List<Tag> newTags = tagRepository.findAllById(newTagIds);
        subject.getTags().addAll(newTags);

        // 5. 保存 subject（自动更新中间表）
        subjectRepository.save(subject);

        // 6. 计算所有受影响的标签 ID（旧 ∪ 新）
        Set<Long> allAffectedTagIds = new HashSet<>(oldTagIds);
        allAffectedTagIds.addAll(newTagIds);

        // 7. 为每个受影响的标签更新 count
        for (Long tagId : allAffectedTagIds) {
            tagRepository.updateTagCount(tagId);
        }
    }

    @Transactional
    public void deleteTagAndRelations(Long tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new RuntimeException("标签不存在，ID: " + tagId));

        // 从所有关联的 subject 中移除该标签
        // 注意：tag.getSubjects() 会在事务内懒加载，无需额外处理
        for (Subject subject : tag.getSubjects()) {
            subject.getTags().remove(tag);
        }
        // 清空 tag 的反向引用（可选）
        tag.getSubjects().clear();

        // 删除标签（外键关联已解除）
        tagRepository.delete(tag);
    }
}