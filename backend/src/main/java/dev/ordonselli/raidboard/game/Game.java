package dev.ordonselli.raidboard.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import dev.ordonselli.raidboard.group.Group;

/** A game in a group's catalog: a local copy of the RAWG data, so pages don't depend on RAWG being up. */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "rawg_id", nullable = false)
    private Long rawgId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(name = "cover_url")
    private String coverUrl;

    protected Game() {
    }

    public Game(Group group, Long rawgId, String name, String slug, String coverUrl) {
        this.group = group;
        this.rawgId = rawgId;
        this.name = name;
        this.slug = slug;
        this.coverUrl = coverUrl;
    }

    public Long getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public Long getRawgId() {
        return rawgId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getCoverUrl() {
        return coverUrl;
    }
}
