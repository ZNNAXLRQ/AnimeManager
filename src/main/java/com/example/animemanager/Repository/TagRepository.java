package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
}
