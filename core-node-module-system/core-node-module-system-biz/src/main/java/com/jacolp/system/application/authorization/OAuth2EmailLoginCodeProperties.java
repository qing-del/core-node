package com.jacolp.system.application.authorization;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/** Single policy source for email-code issuance and verification limits. */
@Component
@ConfigurationProperties(prefix = "jacolp.oauth2.email-code")
public class OAuth2EmailLoginCodeProperties {
    private static final Duration MAX_CODE_TTL = Duration.ofMinutes(10);
    private static final Duration MIN_ISSUE_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration MIN_ISSUE_WINDOW = Duration.ofHours(1);

    private Duration codeTtl = MAX_CODE_TTL;
    private Duration issueCooldown = MIN_ISSUE_COOLDOWN;
    private Duration issueWindow = MIN_ISSUE_WINDOW;
    private Integer maxIssuesPerWindow = 5;
    private Integer maxFailedAttempts = 5;

    public Duration getCodeTtl() {
        return codeTtl;
    }

    public void setCodeTtl(Duration codeTtl) {
        this.codeTtl = require(codeTtl, "codeTtl");
    }

    public Duration getIssueCooldown() {
        return issueCooldown;
    }

    public void setIssueCooldown(Duration issueCooldown) {
        this.issueCooldown = require(issueCooldown, "issueCooldown");
    }

    public Duration getIssueWindow() {
        return issueWindow;
    }

    public void setIssueWindow(Duration issueWindow) {
        this.issueWindow = require(issueWindow, "issueWindow");
    }

    public Integer getMaxIssuesPerWindow() {
        return maxIssuesPerWindow;
    }

    public void setMaxIssuesPerWindow(Integer maxIssuesPerWindow) {
        this.maxIssuesPerWindow = require(maxIssuesPerWindow, "maxIssuesPerWindow");
    }

    public Integer getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(Integer maxFailedAttempts) {
        this.maxFailedAttempts = require(maxFailedAttempts, "maxFailedAttempts");
    }

    @PostConstruct
    void validate() {
        if (!isValidCodeTtl(codeTtl)
                || issueCooldown.compareTo(MIN_ISSUE_COOLDOWN) < 0 || issueWindow.compareTo(MIN_ISSUE_WINDOW) < 0
                || issueWindow.compareTo(issueCooldown) < 0 || maxIssuesPerWindow < 1 || maxIssuesPerWindow > 5
                || maxFailedAttempts < 1 || maxFailedAttempts > 5) {
            throw new IllegalArgumentException("Invalid OAuth2 email-code policy");
        }
    }

    private static boolean isValidCodeTtl(Duration ttl) {
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_CODE_TTL) > 0) {
            return false;
        }
        try {
            long milliseconds = ttl.toMillis();
            return milliseconds > 0 && milliseconds % 60_000L == 0;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static <T> T require(T value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
