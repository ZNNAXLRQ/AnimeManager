package com.example.animemanager.Entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "episodes")
public class Episode {
    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "subect_id", nullable = false)
    private Subject subject;

    @Column(name = "ep", nullable = false)
    private Integer ep;

    @Column(name = "name")
    private String name;

    @Column(name = "name_cn")
    private String nameCn;

    @Temporal(TemporalType.DATE)
    @Column(name = "airdate")
    private Date airdate;

    @Column(name = "duration")
    private String duration;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
