package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    @Query("SELECT s FROM Subject s LEFT JOIN FETCH s.tags WHERE s.id = :id")
    Optional<Subject> findByIdWithTags(@Param("id") Long id);
}