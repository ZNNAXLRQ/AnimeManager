package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository extends JpaRepository<Character, Long> {
}
