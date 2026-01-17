package com.example.animemanager.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Images {
    @Column(name = "small_image_url")
    private String small;

    @Column(name = "grid_image_url")
    private String grid;

    @Column(name = "large_image_url")
    private String large;

    @Column(name = "medium_image_url")
    private String medium;

    @Column(name = "common_image_url")
    private String common;
}
