package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);
    List<Tag> findByNameContainingIgnoreCase(String keyword);

    @Modifying
    @Query("UPDATE Tag t SET t.count = (SELECT COUNT(s) FROM Subject s JOIN s.tags st WHERE st.id = :tagId) WHERE t.id = :tagId")
    int updateTagCount(@Param("tagId") Long tagId);
}