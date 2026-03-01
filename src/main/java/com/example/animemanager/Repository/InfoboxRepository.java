package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Infobox;
import com.example.animemanager.Entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InfoboxRepository extends JpaRepository<Infobox, Long> {
    List<Infobox> findBySubject(Subject subject);
}
