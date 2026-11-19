package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Persisted record of an appeal filed against a denied claim.
 * Represents a multi-level dispute lifecycle (Level 1: Reconsideration, Level 2: Formal, Level 3: External).
 */
@Entity
@Table(name = "claim_appeal")
public class ClaimAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "denial_id")
    private ClaimDenial denial;

    @Column(nullable = false)
    private Integer level = 1;

    @Column(name = "filed_on", nullable = false)
    private LocalDate filedOn;

    @Column(nullable = false)
    private LocalDate deadline;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String narrative;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AppealOutcome outcome;

    @Column(name = "decided_on")
    private LocalDate decidedOn;

    @Column(name = "recovered_amount", precision = 12, scale = 2)
    private BigDecimal recoveredAmount = BigDecimal.ZERO;

    @Column(name = "decision_notes", length = 1000)
    private String decisionNotes;

    @Column(name = "filed_by", nullable = false, length = 60)
    private String filedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public ClaimAppeal() {
    }

    public ClaimAppeal(Claim claim,
                       ClaimDenial denial,
                       Integer level,
                       LocalDate filedOn,
                       LocalDate deadline,
                       String narrative,
                       String filedBy) {
        this.claim = claim;
        this.denial = denial;
        this.level = level != null ? level : 1;
        this.filedOn = filedOn;
        this.deadline = deadline;
        this.narrative = narrative;
        this.filedBy = filedBy;
        this.recoveredAmount = BigDecimal.ZERO;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Claim getClaim() {
        return claim;
    }

    public void setClaim(Claim claim) {
        this.claim = claim;
    }

    public ClaimDenial getDenial() {
        return denial;
    }

    public void setDenial(ClaimDenial denial) {
        this.denial = denial;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public LocalDate getFiledOn() {
        return filedOn;
    }

    public void setFiledOn(LocalDate filedOn) {
        this.filedOn = filedOn;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public AppealOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AppealOutcome outcome) {
        this.outcome = outcome;
    }

    public LocalDate getDecidedOn() {
        return decidedOn;
    }

    public void setDecidedOn(LocalDate decidedOn) {
        this.decidedOn = decidedOn;
    }

    public BigDecimal getRecoveredAmount() {
        return recoveredAmount;
    }

    public void setRecoveredAmount(BigDecimal recoveredAmount) {
        this.recoveredAmount = recoveredAmount;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }

    public String getFiledBy() {
        return filedBy;
    }

    public void setFiledBy(String filedBy) {
        this.filedBy = filedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

