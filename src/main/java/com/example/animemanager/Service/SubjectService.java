package com.example.animemanager.Service;

import com.example.animemanager.Entity.Rating;
import com.example.animemanager.Entity.Subject;
import com.example.animemanager.Repository.SubjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubjectService {
    private final SubjectRepository subjectRepository;
    private List<Subject> cachedSubjects = null; // 缓存基本信息

    @Autowired
    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
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
}