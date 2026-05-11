import React from 'react';

export default function GanttChart({ ganttData, scrollRef, onScroll }) {
  if (!ganttData || !ganttData.bars || ganttData.bars.length === 0) {
    return <p style={{ color: '#888' }}>Brak operacji do wyświetlenia na wykresie.</p>;
  }

  const { projectStart, totalDays, bars } = ganttData;
  const startDate = new Date(projectStart);

  const timeLabels = [];
  const fullDays = Math.ceil(totalDays);
  for (let i = 0; i <= fullDays; i++) {
    const d = new Date(startDate);
    d.setDate(d.getDate() + i);
    timeLabels.push(d);
  }

  const barHeight = 36;
  const barGap = 6;
  const labelWidth = 160;
  const chartPadding = 20;
  const headerHeight = 40;
  const chartHeight = headerHeight + bars.length * (barHeight + barGap) + chartPadding;
  const chartContentWidth = Math.max(600, fullDays * 80 + labelWidth + chartPadding * 2);

  const idToIndex = {};
  bars.forEach((b, i) => { idToIndex[b.operationId] = i; });

  const arrows = [];
  bars.forEach((bar, i) => {
    if (bar.predecessorIds && bar.predecessorIds.length > 0) {
      bar.predecessorIds.forEach(predId => {
        const predIdx = idToIndex[predId];
        if (predIdx === undefined) return;
        const pred = bars[predIdx];
        const fromXPercent = ((pred.startOffsetDays + pred.durationDays) / fullDays) * 100;
        const toXPercent = (bar.startOffsetDays / fullDays) * 100;
        const fromY = headerHeight + predIdx * (barHeight + barGap) + barGap + barHeight / 2;
        const toY = headerHeight + i * (barHeight + barGap) + barGap + barHeight / 2;
        arrows.push({ fromXPercent, toXPercent, fromY, toY, key: `${predId}-${bar.operationId}` });
      });
    }
  });

  return (
    <div ref={scrollRef} onScroll={onScroll} style={{ overflowX: 'auto', marginTop: '10px' }}>
      <div style={{
        position: 'relative',
        minWidth: chartContentWidth + 'px',
        height: chartHeight + 'px',
        background: '#222',
        borderRadius: '8px',
        border: '1px solid #444',
        padding: `0 ${chartPadding}px`
      }}>
        {/* Oś czasu - nagłówek */}
        <div style={{ display: 'flex', marginLeft: labelWidth + 'px', height: headerHeight + 'px', alignItems: 'flex-end', paddingBottom: '4px', borderBottom: '1px solid #555' }}>
          {timeLabels.map((d, i) => (
            <div key={i} style={{ flex: i < fullDays ? 1 : 0, fontSize: '11px', color: '#aaa', textAlign: 'left', whiteSpace: 'nowrap' }}>
              {d.toLocaleDateString('pl-PL', { day: '2-digit', month: '2-digit' })}
            </div>
          ))}
        </div>

        {/* Linie siatki pionowe */}
        {timeLabels.map((_, i) => (
          <div key={'grid-' + i} style={{
            position: 'absolute',
            left: `calc(${labelWidth + chartPadding}px + ${(i / fullDays) * 100}% - ${(i / fullDays) * (labelWidth + chartPadding * 2)}px)`,
            top: headerHeight + 'px',
            bottom: '0',
            width: '1px',
            background: i === 0 ? 'transparent' : '#333',
            pointerEvents: 'none'
          }} />
        ))}

        {/* Strzałki zależności (SVG) */}
        <svg style={{
          position: 'absolute', top: 0,
          left: (labelWidth + chartPadding) + 'px',
          width: `calc(100% - ${labelWidth + 2 * chartPadding}px)`,
          height: '100%', pointerEvents: 'none', overflow: 'visible'
        }}>
          {arrows.map(a => (
            <g key={a.key}>
              <line x1={`${a.fromXPercent}%`} y1={a.fromY} x2={`${a.toXPercent}%`} y2={a.toY}
                stroke="#ff9800" strokeWidth="2" strokeDasharray="6,3" opacity="0.7" />
              <circle cx={`${a.toXPercent}%`} cy={a.toY} r="4" fill="#ff9800" opacity="0.8" />
            </g>
          ))}
        </svg>

        {/* Paski operacji */}
        {bars.map((bar, i) => {
          const leftPercent = (bar.startOffsetDays / fullDays) * 100;
          const widthPercent = Math.max((bar.durationDays / fullDays) * 100, 1);
          const top = headerHeight + i * (barHeight + barGap) + barGap;
          const barStart = new Date(bar.startTime);
          const barEnd = new Date(bar.endTime);
          const tooltip = `${bar.name}\n${barStart.toLocaleString('pl-PL')} — ${barEnd.toLocaleString('pl-PL')}\nPracownicy: ${bar.workerCount}${bar.resources ? '\nZasoby: ' + bar.resources : ''}`;

          return (
            <div key={bar.operationId} style={{ position: 'absolute', top: top + 'px', left: chartPadding + 'px', right: chartPadding + 'px', height: barHeight + 'px', display: 'flex', alignItems: 'center' }}>
              <div style={{ width: labelWidth + 'px', paddingRight: '10px', fontSize: '13px', color: '#ddd', textAlign: 'right', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flexShrink: 0 }}>
                {bar.name}
              </div>
              <div style={{ position: 'relative', flex: 1, height: '100%' }}>
                <div title={tooltip} style={{
                  position: 'absolute', left: leftPercent + '%', width: widthPercent + '%', height: '100%',
                  background: bar.color, borderRadius: '4px', display: 'flex', alignItems: 'center',
                  justifyContent: 'center', fontSize: '11px', color: '#000', fontWeight: 'bold',
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', padding: '0 6px',
                  cursor: 'default', boxShadow: '0 1px 3px rgba(0,0,0,0.4)', transition: 'opacity 0.2s'
                }}>
                  {widthPercent > 8 ? `${Math.round(bar.durationDays * 10) / 10}d` : ''}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
