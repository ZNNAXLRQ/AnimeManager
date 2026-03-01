package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Episode;
import com.example.animemanager.Entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    List<Episode> findBySubject(Subject subject);
}
