package com.argusiq.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "argusiq.web")
public class ArgusIqWebProperties {

    @NotEmpty
    private List<String> allowedOrigins = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins != null ? new ArrayList<>(allowedOrigins) : new ArrayList<>();
    }

    @AssertTrue(message = "argusiq.web.allowed-origins must not contain a bare wildcard")
    public boolean isOriginPolicyRestricted() {
        return allowedOrigins.stream().noneMatch(origin -> "*".equals(origin.trim()));
    }
}
