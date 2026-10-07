package com.github.lechandonga.clinicalearlywarningjava.web.dto;

import java.time.Instant;
import java.util.List;

public class RulePublishRequest {
    private String versionId;
    private String description;
    private Instant effectiveFrom;
    private List<RuleConditionDto> conditions;
    private int mediumScore;
    private int highScore;

    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public List<RuleConditionDto> getConditions() { return conditions; }
    public void setConditions(List<RuleConditionDto> conditions) { this.conditions = conditions; }
    public int getMediumScore() { return mediumScore; }
    public void setMediumScore(int mediumScore) { this.mediumScore = mediumScore; }
    public int getHighScore() { return highScore; }
    public void setHighScore(int highScore) { this.highScore = highScore; }
}
