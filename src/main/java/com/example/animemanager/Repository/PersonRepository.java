package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Person;
import com.example.animemanager.Entity.Subject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {
    @EntityGraph(attributePaths = {"careers"})
    List<Person> findBySubjectsContaining(Subject subject);
}
