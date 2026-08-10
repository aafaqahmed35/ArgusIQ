package com.argusiq.tracing.dto;

import java.util.List;

public class TraceDetailResponseDto {

    private final TraceResponseDto summary;
    private final List<SpanDto> spans;
    private final TraceMetadataDto metadata;

    public TraceDetailResponseDto(
            TraceResponseDto summary,
            List<SpanDto> spans,
            TraceMetadataDto metadata
    ) {
        this.summary = summary;
        this.spans = spans != null ? spans : List.of();
        this.metadata = metadata != null ? metadata : new TraceMetadataDto("production", "1.0.0", "java", null);
    }

    public TraceResponseDto getSummary() {
        return summary;
    }

    public List<SpanDto> getSpans() {
        return spans;
    }

    public TraceMetadataDto getMetadata() {
        return metadata;
    }
}
