import { useEffect, useRef, useState } from 'react'

const ANIMATION_DURATION_MS = 500

function parseDisplayValue(value) {
  const valueText = String(value)
  const match = valueText.match(/^([^0-9-]*)(-?[\d,]+(?:\.\d+)?)(.*)$/)

  if (!match) {
    return null
  }

  const numericValue = Number(match[2].replaceAll(',', ''))

  if (!Number.isFinite(numericValue)) {
    return null
  }

  return {
    prefix: match[1],
    number: numericValue,
    suffix: match[3],
  }
}

function formatInterpolatedValue(parsedValue, currentNumber) {
  const decimals = parsedValue.number % 1 === 0 ? 0 : 1
  const formattedNumber = currentNumber.toLocaleString(undefined, {
    maximumFractionDigits: decimals,
    minimumFractionDigits: decimals,
  })

  return `${parsedValue.prefix}${formattedNumber}${parsedValue.suffix}`
}

function useAnimatedMetricValue(value) {
  const [displayValue, setDisplayValue] = useState(value)
  const previousParsedValueRef = useRef(parseDisplayValue(value))

  useEffect(() => {
    const nextParsedValue = parseDisplayValue(value)
    const previousParsedValue = previousParsedValueRef.current

    if (!nextParsedValue || !previousParsedValue) {
      previousParsedValueRef.current = nextParsedValue
      setDisplayValue(value)
      return undefined
    }

    if (
      nextParsedValue.number === previousParsedValue.number &&
      nextParsedValue.prefix === previousParsedValue.prefix &&
      nextParsedValue.suffix === previousParsedValue.suffix
    ) {
      setDisplayValue(value)
      return undefined
    }

    let animationFrameId
    const startTime = performance.now()
    const startNumber = previousParsedValue.number
    const delta = nextParsedValue.number - startNumber

    function updateDisplayValue(timestamp) {
      const progress = Math.min((timestamp - startTime) / ANIMATION_DURATION_MS, 1)
      const easedProgress = 1 - Math.pow(1 - progress, 3)

      setDisplayValue(formatInterpolatedValue(nextParsedValue, startNumber + delta * easedProgress))

      if (progress < 1) {
        animationFrameId = requestAnimationFrame(updateDisplayValue)
      } else {
        previousParsedValueRef.current = nextParsedValue
        setDisplayValue(value)
      }
    }

    animationFrameId = requestAnimationFrame(updateDisplayValue)

    return () => {
      cancelAnimationFrame(animationFrameId)
    }
  }, [value])

  return displayValue
}

function MetricCard({ label, value, detail, tone = 'default', isLoading = false }) {
  const displayValue = useAnimatedMetricValue(value)
  const compactValue = String(displayValue).replace(' ', '')

  if (isLoading) {
    return (
      <article className={`metric-card metric-card--${tone} metric-card--loading`} aria-busy="true">
        <span className="metric-card__icon" aria-hidden="true" />
        <span className="metric-card__skeleton metric-card__skeleton--label" />
        <span className="metric-card__skeleton metric-card__skeleton--value" />
        <span className="metric-card__skeleton metric-card__skeleton--detail" />
      </article>
    )
  }

  return (
    <article className={`metric-card metric-card--${tone}`}>
      <span className="metric-card__marker" aria-hidden="true" />
      <span className="metric-card__icon" aria-hidden="true" />
      <div className="metric-card__content">
        <span className="metric-card__label">{label}</span>
        <strong className="metric-card__value" title={String(displayValue)}>
          {compactValue}
        </strong>
        {detail ? <span className="metric-card__detail">{detail}</span> : null}
      </div>
    </article>
  )
}

export default MetricCard
