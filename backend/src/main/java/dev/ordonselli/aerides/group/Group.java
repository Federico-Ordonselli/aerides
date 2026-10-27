package dev.ordonselli.aerides.group;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** URL-friendly name, fixed at creation so links keep working if the group is renamed. */
    @Column(nullable = false, unique = true, updatable = false)
    private String slug;

    private String description;

    @Column(name = "invite_code", nullable = false, unique = true)
    private String inviteCode;

    /** A secret: whoever knows it can post in the group's Discord channel. Only the OWNER can see it. */
    @Column(name = "discord_webhook_url")
    private String discordWebhookUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Group() {
    }

    public Group(String name, String slug, String description, String inviteCode) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.inviteCode = inviteCode;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public void setDiscordWebhookUrl(String discordWebhookUrl) {
        this.discordWebhookUrl = discordWebhookUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
