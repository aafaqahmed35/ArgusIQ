package com.argusiq.tracing.dto;

import com.argusiq.tracing.criticalpath.CriticalPathResult;
import com.argusiq.tracing.explanation.TraceExplanation;

import java.util.List;

public class TraceDetailResponseDto {

    private final TraceResponseDto summary;
    private final List<SpanDto> spans;
    private final TraceMetadataDto metadata;
    private final CriticalPathResult criticalPath;
    private final TraceExplanation explanation;

    public TraceDetailResponseDto(
            TraceResponseDto summary,
            List<SpanDto> spans,
            TraceMetadataDto metadata,
            CriticalPathResult criticalPath,
            TraceExplanation explanation
    ) {
        this.summary = summary;
        this.spans = spans != null ? spans : List.of();
        this.metadata = metadata != null ? metadata : new TraceMetadataDto(null, null, null, null);
        this.criticalPath = criticalPath;
        this.explanation = explanation;
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

    public CriticalPathResult getCriticalPath() {
        return criticalPath;
    }

    public TraceExplanation getExplanation() {
        return explanation;
    }
}
