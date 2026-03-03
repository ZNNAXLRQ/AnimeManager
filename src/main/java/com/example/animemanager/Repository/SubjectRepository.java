package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    @Query("SELECT s FROM Subject s LEFT JOIN FETCH s.tags WHERE s.id = :id")
    Optional<Subject> findByIdWithTags(@Param("id") Long id);
    @Query("SELECT DISTINCT s FROM Subject s JOIN s.persons p WHERE p.name LIKE %:keyword%")
    List<Subject> findByPersonNameContaining(@Param("keyword") String keyword);
    @Query("SELECT DISTINCT s FROM Subject s JOIN s.characters c WHERE c.attitude = :attitude")
    List<Subject> findByCharacterAttitude(@Param("attitude") int attitude);
    @Query("SELECT DISTINCT s FROM Subject s JOIN s.episodes e WHERE e.attitude = :attitude")
    List<Subject> findByEpisodeAttitude(@Param("attitude") int attitude);
    @Query("SELECT DISTINCT s FROM Subject s JOIN s.tags t WHERE t.name LIKE %:keyword%")
    List<Subject> findByTagNameContaining(@Param("keyword") String keyword);
}