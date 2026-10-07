package com.github.lechandonga.clinicalearlywarningjava.domain;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean higherThan(RiskLevel other) {
        return this.ordinal() > other.ordinal();
    }
}
