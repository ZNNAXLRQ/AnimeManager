package com.example.animemanager.Entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "characters")
public class Character {
    @Id
    @Column(name = "character_id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Lob
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "relation")
    private String relation;

    @Column(name = "character_type")
    private Integer type;

    @Embedded
    private Images images;

    @ManyToMany
    @JoinTable(
            name = "character_cast",
            joinColumns = @JoinColumn(name = "character_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id")
    )
    private List<Person> casts;

    @ManyToMany(mappedBy = "characters")
    private List<Subject> subjects;
}
