package dev.shubforge.platform.application;

public record ApplicationReadiness(
    int readyReplicas,
    boolean ready,
    String reason,
    String message) {
}
