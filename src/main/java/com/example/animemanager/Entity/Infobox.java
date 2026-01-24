package com.example.animemanager.Entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "infobox")
public class Infobox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long infoboxId;

    @ManyToOne
    @JoinColumn(name = "subect_id", nullable = false)
    private Subject subject;

    @Column(name = "item_key")
    private String key;

    @Lob
    @Column(name = "item_value", columnDefinition = "TEXT")
    private String value;

    // Getters and setters
}