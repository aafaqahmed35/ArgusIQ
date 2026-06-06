function TraceMetadataGrid({ items }) {
  return (
    <dl className="trace-metadata-grid">
      {items.map((item) => (
        <div className="trace-metadata-grid__item" key={item.label}>
          <dt>{item.label}</dt>
          <dd>{item.value}</dd>
        </div>
      ))}
    </dl>
  )
}

export default TraceMetadataGrid
