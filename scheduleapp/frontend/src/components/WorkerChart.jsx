import React from 'react';

export default function WorkerChart({ ganttData, title, scrollRef, onScroll }) {
  if (!ganttData || !ganttData.bars || ganttData.bars.length === 0) {
    return <p style={{ color: '#888' }}>Brak danych pracowników.</p>;
  }

  const { projectStart, totalDays, bars } = ganttData;
  const startDate = new Date(projectStart);
  const fullDays = Math.ceil(totalDays);

  const step = 0.5;
  const slots = Math.ceil(fullDays / step);
  const workerCounts = new Array(slots).fill(0);

  bars.forEach(bar => {
    for (let s = 0; s < slots; s++) {
      const slotStart = s * step;
      const slotEnd = slotStart + step;
      if (bar.startOffsetDays < slotEnd && (bar.startOffsetDays + bar.durationDays) > slotStart) {
        workerCounts[s] += bar.workerCount;
      }
    }
  });

  const maxWorkers = Math.max(...workerCounts, 1);

  const labelWidth = 160;
  const chartPadding = 20;
  const chartHeight = 120;
  const headerHeight = 20;
  const chartContentWidth = Math.max(600, fullDays * 80 + labelWidth + chartPadding * 2);

  const timeLabels = [];
  for (let i = 0; i <= fullDays; i++) {
    const d = new Date(startDate);
    d.setDate(d.getDate() + i);
    timeLabels.push(d);
  }

  const plotWidth = chartContentWidth - labelWidth - chartPadding * 2;
  const plotHeight = chartHeight - headerHeight - 10;

  const stairParts = [];
  workerCounts.forEach((count, s) => {
    const x1 = ((s * step) / fullDays) * plotWidth;
    const x2 = (((s + 1) * step) / fullDays) * plotWidth;
    const y = plotHeight - (count / maxWorkers) * plotHeight;
    if (s === 0) stairParts.push(`M ${x1},${y}`);
    else stairParts.push(`V ${y}`);
    stairParts.push(`H ${x2}`);
  });
  const lastX = ((slots * step) / fullDays) * plotWidth;
  const linePath = stairParts.join(' ');
  const areaPath = `${linePath} L ${lastX},${plotHeight} L 0,${plotHeight} Z`;

  return (
    <div ref={scrollRef} onScroll={onScroll} style={{ overflowX: 'auto', marginTop: '6px', marginBottom: '30px' }}>
      <div style={{
        position: 'relative', minWidth: chartContentWidth + 'px', height: (chartHeight + 30) + 'px',
        background: '#1a1a2e', borderRadius: '6px', border: '1px solid #444', padding: `0 ${chartPadding}px`
      }}>
        {/* Etykieta osi Y */}
        <div style={{
          position: 'absolute', left: 0, top: headerHeight + 'px', width: labelWidth + 'px', height: plotHeight + 'px',
          display: 'flex', flexDirection: 'column', justifyContent: 'space-between', paddingRight: '8px', boxSizing: 'border-box'
        }}>
          <span style={{ fontSize: '10px', color: '#aaa', textAlign: 'right' }}>{maxWorkers}</span>
          <span style={{ fontSize: '10px', color: '#aaa', textAlign: 'right' }}>{Math.round(maxWorkers / 2)}</span>
          <span style={{ fontSize: '10px', color: '#aaa', textAlign: 'right' }}>0</span>
        </div>

        {/* Oś czasu — etykiety */}
        <div style={{ display: 'flex', marginLeft: labelWidth + 'px', height: headerHeight + 'px', alignItems: 'flex-end', paddingBottom: '2px', borderBottom: '1px solid #555' }}>
          {timeLabels.map((d, i) => (
            <div key={i} style={{ flex: i < fullDays ? 1 : 0, fontSize: '10px', color: '#aaa', textAlign: 'left', whiteSpace: 'nowrap' }}>
              {d.toLocaleDateString('pl-PL', { day: '2-digit', month: '2-digit' })}
            </div>
          ))}
        </div>

        {/* SVG wykres */}
        <svg style={{ position: 'absolute', top: headerHeight + 'px', left: (labelWidth + chartPadding) + 'px', width: plotWidth + 'px', height: plotHeight + 'px', overflow: 'visible' }}
          viewBox={`0 0 ${plotWidth} ${plotHeight}`} preserveAspectRatio="none">
          {[0, 0.25, 0.5, 0.75, 1].map(frac => (
            <line key={frac} x1="0" y1={plotHeight * (1 - frac)} x2={plotWidth} y2={plotHeight * (1 - frac)} stroke="#333" strokeWidth="1" />
          ))}
          {timeLabels.map((_, i) => (
            <line key={i} x1={(i / fullDays) * plotWidth} y1="0" x2={(i / fullDays) * plotWidth} y2={plotHeight} stroke="#333" strokeWidth="1" />
          ))}
          <path d={areaPath} fill="rgba(77,192,225,0.25)" />
          <path d={linePath} fill="none" stroke="#4DC0E1" strokeWidth="2" />
        </svg>

        {/* Słupki tooltipów */}
        <svg style={{ position: 'absolute', top: headerHeight + 'px', left: (labelWidth + chartPadding) + 'px', width: plotWidth + 'px', height: plotHeight + 'px', overflow: 'visible' }}
          viewBox={`0 0 ${plotWidth} ${plotHeight}`} preserveAspectRatio="none">
          {workerCounts.map((count, s) => {
            const x = ((s * step) / fullDays) * plotWidth;
            const w = Math.max((step / fullDays) * plotWidth - 1, 1);
            const barH = (count / maxWorkers) * plotHeight;
            return (
              <rect key={s} x={x} y={plotHeight - barH} width={w} height={barH} fill="rgba(77,192,225,0.0)" style={{ cursor: 'default' }}>
                <title>{`${(s * step).toFixed(1)}–${(s * step + step).toFixed(1)} d: ${count} pracowników`}</title>
              </rect>
            );
          })}
        </svg>

        <div style={{ position: 'absolute', bottom: '4px', left: labelWidth + chartPadding + 'px', fontSize: '11px', color: '#888' }}>
          {title ? title : 'Liczba pracowników w czasie'}
        </div>
      </div>
    </div>
  );
}
