package com.example.animemanager.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.util.List;

@Data
@Entity
@Table(name = "tags")
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tagId;

    @Column(name = "name", nullable = false, unique = true)  // 标签名全局唯一
    private String name;

    @Column(name = "count", nullable = false)
    @ColumnDefault("0")
    private Integer count;

    @ManyToMany(mappedBy = "tags")
    private List<Subject> subjects;  // 反向引用（可选）
}