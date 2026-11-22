package com.claire.claims.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "claim_attachment_audit")
public class ClaimAttachmentAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachment_id", nullable = false)
    private ClaimAttachment attachment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(nullable = false, length = 60)
    private String actor;

    @Column(name = "acted_at", nullable = false)
    private OffsetDateTime actedAt = OffsetDateTime.now();

    @Column(length = 500)
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ClaimAttachment getAttachment() { return attachment; }
    public void setAttachment(ClaimAttachment attachment) { this.attachment = attachment; }

    public Claim getClaim() { return claim; }
    public void setClaim(Claim claim) { this.claim = claim; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public OffsetDateTime getActedAt() { return actedAt; }
    public void setActedAt(OffsetDateTime actedAt) { this.actedAt = actedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

