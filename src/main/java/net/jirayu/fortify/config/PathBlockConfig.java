package net.jirayu.fortify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@ConfigurationProperties(prefix = "plugins.fortify.pathblock")
@Component
public class PathBlockConfig {
    private boolean enabled = false;
    private String[] blockedPaths = new String[0];
    private String[] blockedPathPatterns = new String[0];
    private final Set<Pattern> compiledPatterns = new HashSet<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String[] getBlockedPaths() {
        return blockedPaths;
    }

    public void setBlockedPaths(String[] blockedPaths) {
        this.blockedPaths = blockedPaths;
    }

    public String[] getBlockedPathPatterns() {
        return blockedPathPatterns;
    }

    public void setBlockedPathPatterns(String[] blockedPathPatterns) {
        this.blockedPathPatterns = blockedPathPatterns;
        compilePatterns();
    }

    private void compilePatterns() {
        compiledPatterns.clear();
        Arrays.stream(blockedPathPatterns).forEach(pattern -> {
            try {
                compiledPatterns.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex pattern: " + pattern, e);
            }
        });
    }

    public boolean isPathBlocked(String path) {
        if (!enabled) {
            return false;
        }

        for (String blockedPath : blockedPaths) {
            if (path.equals(blockedPath)) {
                return true;
            }
        }

        for (Pattern pattern : compiledPatterns) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }

        return false;
    }
}