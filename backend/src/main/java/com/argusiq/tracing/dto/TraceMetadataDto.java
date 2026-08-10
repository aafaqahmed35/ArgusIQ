package com.argusiq.tracing.dto;

import java.util.Map;

public class TraceMetadataDto {

    private final String environment;
    private final String serviceVersion;
    private final String sdkLanguage;
    private final Map<String, String> resourceAttributes;

    public TraceMetadataDto(
            String environment,
            String serviceVersion,
            String sdkLanguage,
            Map<String, String> resourceAttributes
    ) {
        this.environment = environment != null ? environment : "production";
        this.serviceVersion = serviceVersion != null ? serviceVersion : "1.0.0";
        this.sdkLanguage = sdkLanguage != null ? sdkLanguage : "java";
        this.resourceAttributes = resourceAttributes != null ? resourceAttributes : Map.of();
    }

    public String getEnvironment() {
        return environment;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public String getSdkLanguage() {
        return sdkLanguage;
    }

    public Map<String, String> getResourceAttributes() {
        return resourceAttributes;
    }
}
