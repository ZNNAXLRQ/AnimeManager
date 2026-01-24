package com.example.animemanager.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.util.Date;

@Data
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

    @Column(name = "airdate")
    private String airdate;

    @Column(name = "duration")
    private String duration;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // 自定义数据
    @Column(name = "attitude", nullable = false)
    @ColumnDefault("0")
    private Integer attitude;
}
