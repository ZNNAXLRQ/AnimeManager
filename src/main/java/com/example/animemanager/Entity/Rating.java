package com.example.animemanager.Entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.Map;

@Embeddable
public class Rating {
    @Column(name = "rank")
    private Integer rank;

    @Column(name = "total_count", nullable = false)
    @ColumnDefault("0")
    private Integer total;

    @Column(name = "score", nullable = false)
    @ColumnDefault("0.0")
    private Double score;

    // Getters and setters
}
