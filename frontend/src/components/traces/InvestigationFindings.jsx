function evidenceKind(finding) {
  if (finding.evidence?.some((evidence) => evidence.relationship === 'VALIDATED_CHILD_OF')) {
    return 'Validated Structural Evidence'
  }
  if (finding.category === 'ERROR') return 'Observed Evidence'
  if (finding.category === 'LIMITATION') return 'Limited by incomplete telemetry'
  return 'Structural Evidence'
}

const STATUS_MEANING = {
  COMPLETE: 'Available valid structural snapshot evaluated without graph-integrity limitations; this is not complete causal or root-cause knowledge.',
  PARTIAL: 'A valid canonical structural component was evaluated with the listed graph-integrity limitations.',
  INSUFFICIENT_EVIDENCE: 'No valid canonical structural path was available for evaluation.',
}

const EVIDENCE_STRENGTH_MEANING = {
  HIGH: 'Strong or direct support for the stated observation',
  MEDIUM: 'Support from a validated structural relationship',
  LOW: 'Interpretation constrained by incomplete structural evidence',
}

function evidenceDetails(evidence) {
  const metrics = []
  if (evidence.contributionDurationMs !== null && evidence.contributionDurationMs !== undefined) {
    metrics.push(`${evidence.contributionDurationMs} ms contribution`)
  }
  if (evidence.selfTimeMs !== null && evidence.selfTimeMs !== undefined) {
    metrics.push(`${evidence.selfTimeMs} ms self-time`)
  }
  if (evidence.contributionPercentage !== null && evidence.contributionPercentage !== undefined) {
    metrics.push(`${evidence.contributionPercentage}% of structural path`)
  }
  if (evidence.overlapDurationMs !== null && evidence.overlapDurationMs !== undefined) {
    metrics.push(`${evidence.overlapDurationMs} ms overlap`)
  }
  if (evidence.statusCode) metrics.push(`status ${evidence.statusCode}`)
  if (evidence.httpStatusCode !== null && evidence.httpStatusCode !== undefined) {
    metrics.push(`HTTP ${evidence.httpStatusCode}`)
  }
  return metrics.join(' · ')
}

function InvestigationFindings({ explanation }) {
  if (!explanation?.status) return null

  const findings = Array.isArray(explanation.findings) ? explanation.findings : []
  const limitations = Array.isArray(explanation.limitations) ? explanation.limitations : []
  const qualityColor = explanation.status === 'COMPLETE' ? '#86EFAC' : '#FBBF24'

  return (
    <section
      aria-label="Investigation findings"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: '0.75rem',
        padding: '1rem',
        background: 'rgba(15, 23, 42, 0.72)',
        border: '1px solid rgba(56, 189, 248, 0.28)',
        borderRadius: '8px',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', alignItems: 'center' }}>
        <div>
          <span style={{ color: '#94A3B8', fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Worth Investigating
          </span>
          <h3 style={{ margin: 0, color: '#F5F7FA', fontSize: '1rem' }}>Investigation Findings</h3>
        </div>
        <span
          style={{
            color: qualityColor,
            border: '1px solid currentColor',
            borderRadius: '12px',
            padding: '2px 8px',
            fontSize: '0.7rem',
            fontWeight: 700,
          }}
        >
          {explanation.status}
        </span>
      </div>

      <p style={{ margin: 0, color: '#E2E8F0', fontSize: '0.85rem', lineHeight: 1.5 }}>
        {explanation.summary}
      </p>
      <p style={{ margin: 0, color: '#94A3B8', fontSize: '0.72rem', lineHeight: 1.45 }}>
        {STATUS_MEANING[explanation.status] || 'Status describes structural graph evaluability.'}
        {' '}Evidence strength describes support for the stated observation, not incident causation.
      </p>

      {findings.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
          {findings.map((finding, index) => (
            <article
              key={`${finding.code}-${finding.evidence?.[0]?.spanId || index}`}
              style={{
                padding: '0.75rem',
                borderRadius: '6px',
                background: 'rgba(7, 19, 31, 0.72)',
                border: '1px solid rgba(255,255,255,0.08)',
              }}
            >
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', alignItems: 'center' }}>
                <strong style={{ color: '#F5F7FA', fontSize: '0.85rem' }}>{finding.title}</strong>
                <span style={{ color: '#38BDF8', fontSize: '0.68rem' }}>{evidenceKind(finding)}</span>
                <span
                  title={EVIDENCE_STRENGTH_MEANING[finding.evidenceStrength]}
                  style={{ color: '#94A3B8', fontSize: '0.68rem' }}
                >
                  {finding.evidenceStrength} evidence · {finding.significance}
                </span>
              </div>
              <p style={{ margin: '0.35rem 0 0', color: '#CBD5E1', fontSize: '0.8rem', lineHeight: 1.45 }}>
                {finding.description}
              </p>
              {finding.evidence?.map((evidence, evidenceIndex) => (
                <div
                  key={`${evidence.spanId || 'trace'}-${evidenceIndex}`}
                  style={{ marginTop: '0.4rem', color: '#94A3B8', fontSize: '0.72rem' }}
                >
                  {evidence.spanId && (
                    <span>
                      <code>{evidence.spanId}</code> · {evidence.serviceName || 'unknown-service'} / {evidence.operationName || 'unnamed-span'}
                    </span>
                  )}
                  {evidenceDetails(evidence) && <span> · {evidenceDetails(evidence)}</span>}
                </div>
              ))}
            </article>
          ))}
        </div>
      ) : (
        <div style={{ color: '#94A3B8', fontSize: '0.78rem' }}>
          No high-value error or structural-latency finding crossed the documented thresholds.
        </div>
      )}

      {limitations.length > 0 && (
        <div style={{ borderTop: '1px solid rgba(255,255,255,0.08)', paddingTop: '0.6rem' }}>
          <strong style={{ color: '#FBBF24', fontSize: '0.75rem' }}>Evidence Limitations</strong>
          <ul style={{ margin: '0.35rem 0 0', paddingLeft: '1.1rem', color: '#94A3B8', fontSize: '0.72rem' }}>
            {limitations.map((limitation) => (
              <li key={limitation.code}>{limitation.description}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

export default InvestigationFindings
