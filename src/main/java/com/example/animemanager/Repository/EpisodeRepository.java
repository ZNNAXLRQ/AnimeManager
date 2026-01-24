package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {
}
