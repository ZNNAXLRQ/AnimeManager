package com.example.animemanager.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Formula;

@Data
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

    // 自定义数据
    @Column(name = "information", nullable = false)
    @ColumnDefault("0.0")
    private Double information;

    @Column(name = "story", nullable = false)
    @ColumnDefault("0.0")
    private Double story;

    @Column(name = "character", nullable = false)
    @ColumnDefault("0.0")
    private Double character;

    @Column(name = "quality", nullable = false)
    @ColumnDefault("0.0")
    private Double quality;

    @Column(name = "atmosphere", nullable = false)
    @ColumnDefault("0.0")
    private Double atmosphere;

    @Column(name = "love", nullable = false)
    @ColumnDefault("0.0")
    private Double love;

    @Column(name = "totalscore", nullable = false)
    @ColumnDefault("0.0")
    private Double totalscore;

    @Formula("totalscore - score")
    private Double distance;
}
