package com.argusiq.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "argusiq.security")
public class ArgusIqSecurityProperties {

    @NotBlank
    private String investigationUsername;

    @NotBlank
    @Size(min = 16)
    private String investigationPassword;

    @NotBlank
    private String ingestionUsername;

    @NotBlank
    @Size(min = 16)
    private String ingestionPassword;

    public String getInvestigationUsername() {
        return investigationUsername;
    }

    public void setInvestigationUsername(String investigationUsername) {
        this.investigationUsername = investigationUsername;
    }

    public String getInvestigationPassword() {
        return investigationPassword;
    }

    public void setInvestigationPassword(String investigationPassword) {
        this.investigationPassword = investigationPassword;
    }

    public String getIngestionUsername() {
        return ingestionUsername;
    }

    public void setIngestionUsername(String ingestionUsername) {
        this.ingestionUsername = ingestionUsername;
    }

    public String getIngestionPassword() {
        return ingestionPassword;
    }

    public void setIngestionPassword(String ingestionPassword) {
        this.ingestionPassword = ingestionPassword;
    }
}
