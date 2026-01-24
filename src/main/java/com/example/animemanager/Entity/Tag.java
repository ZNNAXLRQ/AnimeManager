package com.example.animemanager.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

@Data
@Entity
@Table(name = "tags")
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tagId;

    @ManyToOne
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "count", nullable = false)
    @ColumnDefault("0")
    private Integer count;
}