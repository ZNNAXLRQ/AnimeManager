package com.example.animemanager.Repository;

import com.example.animemanager.Entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
