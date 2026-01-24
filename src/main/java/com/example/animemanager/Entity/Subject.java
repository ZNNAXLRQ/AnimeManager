package com.example.animemanager.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.util.Date;
import java.util.List;

@Data
@Entity
@Table(name = "subject")
public class Subject {
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_cn", nullable = false)
    private String nameCn;

    @Column(name = "date")
    private String date;

    @Column(name = "platform")
    private String platform;

    @Lob
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "eps", nullable = false)
    @ColumnDefault("0")
    private Integer eps;

    @Column(name = "volumes", nullable = false)
    @ColumnDefault("0")
    private Integer volumes;

    @Column(name = "series", nullable = false)
    @ColumnDefault("false")
    private Boolean series;

    @Column(name = "locked", nullable = false)
    @ColumnDefault("false")
    private Boolean locked;

    @Column(name = "nsfw", nullable = false)
    @ColumnDefault("false")
    private Boolean nsfw;

    @Column(name = "type", nullable = false)
    @ColumnDefault("0")
    private Integer type;

    @Embedded
    private Images images;

    @Embedded
    private Rating rating;
    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Tag> tags;

    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Infobox> infobox;

    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Episode> episodes;

    @ManyToMany
    @JoinTable(
            name = "subject_character",
            joinColumns = @JoinColumn(name = "subject_id"),
            inverseJoinColumns = @JoinColumn(name = "character_id")
    )
    private List<Character> characters;

    @ManyToMany
    @JoinTable(
            name = "subject_person",
            joinColumns = @JoinColumn(name = "subject_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id")
    )
    private List<Person> persons;
}
