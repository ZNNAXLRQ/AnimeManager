package com.example.animemanager.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.util.List;

@Data
@Entity
@Table(name = "persons")
public class Person {
    @Id
    @Column(name = "person_id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Lob
    @Column(name = "short_summary", columnDefinition = "TEXT")
    private String shortSummary;

    @Column(name = "person_type", nullable = false)
    @ColumnDefault("0")
    private Integer type;

    @Column(name = "locked", nullable = false)
    @ColumnDefault("false")
    private Boolean locked;

    @Embedded
    private Images images;

    @ElementCollection
    @CollectionTable(
            name = "person_careers",
            joinColumns = @JoinColumn(name = "person_id")
    )
    @Column(name = "career")
    private List<String> careers;

    @ManyToMany(mappedBy = "casts")
    private List<Character> characters;

    @ManyToMany(mappedBy = "persons")
    private List<Subject> subjects;
}
