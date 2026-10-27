package com.soulspace.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "soul_board_posts", indexes = @Index(columnList = "created_at"))
public class SoulBoardPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "alias_name", length = 60)
    public String alias;
    @Column(length = 30)
    public String category;
    @Column(length = 600)
    public String content;
    public int hugsCount;
    public int teaCount;
    public int peaceCount;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
