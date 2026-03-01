package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Character;
import com.example.animemanager.Entity.Subject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRepository extends JpaRepository<Character, Long> {
    @EntityGraph(attributePaths = {"casts"})
    List<Character> findBySubjectsContaining(Subject subject);
}
