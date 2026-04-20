import React, { useState, useEffect, useRef } from 'react'
import axios from 'axios'

function GanttChart({ ganttData, scrollRef, onScroll }) {
  if (!ganttData || !ganttData.bars || ganttData.bars.length === 0) {
    return <p style={{ color: '#888' }}>Brak operacji do wyświetlenia na wykresie.</p>;
  }

  const { projectStart, projectEnd, totalDays, bars } = ganttData;
  const startDate = new Date(projectStart);
  const endDate = new Date(projectEnd);

  // Generuj etykiety osi czasu (daty)
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

  // Mapa operationId -> indeks w tablicy bars
  const idToIndex = {};
  bars.forEach((b, i) => { idToIndex[b.operationId] = i; });

  // Generuj strzałki zależności
  const arrows = [];
  bars.forEach((bar, i) => {
    if (bar.predecessorIds && bar.predecessorIds.length > 0) {
      bar.predecessorIds.forEach(predId => {
        const predIdx = idToIndex[predId];
        if (predIdx === undefined) return;
        const pred = bars[predIdx];
        // Strzałka: koniec paska poprzednika → początek paska bieżącego
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
            <div key={i} style={{
              flex: i < fullDays ? 1 : 0,
              fontSize: '11px',
              color: '#aaa',
              textAlign: 'left',
              whiteSpace: 'nowrap'
            }}>
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
          position: 'absolute',
          top: 0,
          left: (labelWidth + chartPadding) + 'px',
          width: `calc(100% - ${labelWidth + 2 * chartPadding}px)`,
          height: '100%',
          pointerEvents: 'none',
          overflow: 'visible'
        }}>
          {arrows.map(a => {
            const x1 = `${a.fromXPercent}%`;
            const x2 = `${a.toXPercent}%`;
            const y1 = a.fromY;
            const y2 = a.toY;
            return (
              <g key={a.key}>
                <line x1={x1} y1={y1} x2={x2} y2={y2}
                  stroke="#ff9800" strokeWidth="2" strokeDasharray="6,3" opacity="0.7" />
                {/* Grot strzałki */}
                <circle cx={x2} cy={y2} r="4" fill="#ff9800" opacity="0.8" />
              </g>
            );
          })}
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
              {/* Etykieta */}
              <div style={{
                width: labelWidth + 'px',
                paddingRight: '10px',
                fontSize: '13px',
                color: '#ddd',
                textAlign: 'right',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                flexShrink: 0
              }}>
                {bar.name}
              </div>

              {/* Obszar wykresu */}
              <div style={{ position: 'relative', flex: 1, height: '100%' }}>
                <div title={tooltip} style={{
                  position: 'absolute',
                  left: leftPercent + '%',
                  width: widthPercent + '%',
                  height: '100%',
                  background: bar.color,
                  borderRadius: '4px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '11px',
                  color: '#000',
                  fontWeight: 'bold',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  padding: '0 6px',
                  cursor: 'default',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.4)',
                  transition: 'opacity 0.2s'
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

function WorkerChart({ ganttData, title, scrollRef, onScroll }) {
  if (!ganttData || !ganttData.bars || ganttData.bars.length === 0) {
    return <p style={{ color: '#888' }}>Brak danych pracowników.</p>;
  }

  const { projectStart, totalDays, bars } = ganttData;
  const startDate = new Date(projectStart);
  const fullDays = Math.ceil(totalDays);

  // Oblicz liczbę pracowników dla każdej doby (0.5-dniowe kroki dla dokładności)
  const step = 0.5;
  const slots = Math.ceil(fullDays / step);
  const workerCounts = new Array(slots).fill(0);

  bars.forEach(bar => {
    for (let s = 0; s < slots; s++) {
      const slotStart = s * step;
      const slotEnd = slotStart + step;
      // Pasek jest aktywny jeśli jego zakres nakłada się ze slotem
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

  // Generuj etykiety osi czasu (co 1 dzień)
  const timeLabels = [];
  for (let i = 0; i <= fullDays; i++) {
    const d = new Date(startDate);
    d.setDate(d.getDate() + i);
    timeLabels.push(d);
  }

  // Buduj SVG path (wykres schodkowy/stairs)
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
        position: 'relative',
        minWidth: chartContentWidth + 'px',
        height: (chartHeight + 30) + 'px',
        background: '#1a1a2e',
        borderRadius: '6px',
        border: '1px solid #444',
        padding: `0 ${chartPadding}px`
      }}>
        {/* Etykieta osi Y */}
        <div style={{
          position: 'absolute',
          left: 0,
          top: headerHeight + 'px',
          width: labelWidth + 'px',
          height: plotHeight + 'px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          paddingRight: '8px',
          boxSizing: 'border-box'
        }}>
          <span style={{ fontSize: '10px', color: '#aaa', textAlign: 'right' }}>{maxWorkers}</span>
          <span style={{ fontSize: '10px', color: '#aaa', textAlign: 'right' }}>{Math.round(maxWorkers / 2)}</span>
          <span style={{ fontSize: '10px', color: '#aaa', textAlign: 'right' }}>0</span>
        </div>

        {/* Oś czasu — etykiety */}
        <div style={{ display: 'flex', marginLeft: labelWidth + 'px', height: headerHeight + 'px', alignItems: 'flex-end', paddingBottom: '2px', borderBottom: '1px solid #555' }}>
          {timeLabels.map((d, i) => (
            <div key={i} style={{
              flex: i < fullDays ? 1 : 0,
              fontSize: '10px',
              color: '#aaa',
              textAlign: 'left',
              whiteSpace: 'nowrap'
            }}>
              {d.toLocaleDateString('pl-PL', { day: '2-digit', month: '2-digit' })}
            </div>
          ))}
        </div>

        {/* SVG wykres */}
        <svg style={{
          position: 'absolute',
          top: headerHeight + 'px',
          left: (labelWidth + chartPadding) + 'px',
          width: plotWidth + 'px',
          height: plotHeight + 'px',
          overflow: 'visible'
        }} viewBox={`0 0 ${plotWidth} ${plotHeight}`} preserveAspectRatio="none">
          {/* Linie siatki poziome */}
          {[0, 0.25, 0.5, 0.75, 1].map(frac => (
            <line key={frac}
              x1="0" y1={plotHeight * (1 - frac)}
              x2={plotWidth} y2={plotHeight * (1 - frac)}
              stroke="#333" strokeWidth="1" />
          ))}
          {/* Linie siatki pionowe */}
          {timeLabels.map((_, i) => (
            <line key={i}
              x1={(i / fullDays) * plotWidth} y1="0"
              x2={(i / fullDays) * plotWidth} y2={plotHeight}
              stroke="#333" strokeWidth="1" />
          ))}
          {/* Obszar wypełniony */}
          <path d={areaPath} fill="rgba(77,192,225,0.25)" />
          {/* Linia */}
          <path d={linePath} fill="none" stroke="#4DC0E1" strokeWidth="2" />
        </svg>

        {/* Słupki z liczbą pracowników — tooltip */}
        <svg style={{
          position: 'absolute',
          top: headerHeight + 'px',
          left: (labelWidth + chartPadding) + 'px',
          width: plotWidth + 'px',
          height: plotHeight + 'px',
          overflow: 'visible'
        }} viewBox={`0 0 ${plotWidth} ${plotHeight}`} preserveAspectRatio="none">
          {workerCounts.map((count, s) => {
            const x = ((s * step) / fullDays) * plotWidth;
            const w = Math.max((step / fullDays) * plotWidth - 1, 1);
            const barH = (count / maxWorkers) * plotHeight;
            return (
              <rect key={s}
                x={x} y={plotHeight - barH} width={w} height={barH}
                fill="rgba(77,192,225,0.0)"
                style={{ cursor: 'default' }}>
                <title>{`${(s * step).toFixed(1)}–${(s * step + step).toFixed(1)} d: ${count} pracowników`}</title>
              </rect>
            );
          })}
        </svg>

        {/* Etykieta wykresu */}
        <div style={{ position: 'absolute', bottom: '4px', left: labelWidth + chartPadding + 'px', fontSize: '11px', color: '#888' }}>
          {title ? title : 'Liczba pracowników w czasie'}
        </div>
      </div>
    </div>
  );
}

function App() {
  const [operations, setOperations] = useState([]);
  const [ganttData, setGanttData] = useState(null);
  const [ganttLateData, setGanttLateData] = useState(null);
  const [crashingDays, setCrashingDays] = useState({});
  const [formData, setFormData] = useState({
    name: '',
    startTime: '',
    endTime: '',
    asap: false,
    asapDurationValue: '',
    asapDurationUnit: 'hours',
    workerCount: 1,
    resources: '',
    totalCost: 0,
    crashingCostPerDay: 0,
    maxCrashingDays: 0,
    predecessorIds: ''
  });
  const [error, setError] = useState('');
  const fileInputRef = useRef(null);
  const mergeFileInputRef = useRef(null);
  const [selectedPredecessors, setSelectedPredecessors] = useState([]);
  const ganttEarlyRef = useRef(null);
  const workerEarlyRef = useRef(null);
  const ganttLateRef = useRef(null);
  const workerLateRef = useRef(null);
  const syncScroll = (targetRef) => (e) => {
    if (targetRef.current) targetRef.current.scrollLeft = e.currentTarget.scrollLeft;
  };

  const fetchOperations = async () => {
    try {
      const res = await axios.get('/api/operations');
      if (Array.isArray(res.data)) {
        setOperations(res.data);
        // Zsynchronizuj crashingDays ze stanem zapisanym w bazie
        const map = {};
        res.data.forEach(op => { map[op.id] = op.crashedDays || 0; });
        setCrashingDays(map);
      }
    } catch (err) {
      console.error("Błąd pobierania danych:", err);
    }
  };

  const fetchGantt = async () => {
    try {
      const res = await axios.get('/api/operations/gantt');
      setGanttData(res.data);
    } catch (err) {
      console.error("Błąd pobierania wykresu Gantta:", err);
    }
  };

  const fetchGanttLate = async () => {
    try {
      const res = await axios.get('/api/operations/gantt-late');
      setGanttLateData(res.data);
    } catch (err) {
      console.error("Błąd pobierania wykresu Gantta (późne starty):", err);
    }
  };

  const refreshAll = () => {
    fetchOperations();
    fetchGantt();
    fetchGanttLate();
  };

  useEffect(() => {
    refreshAll();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    try {
      let dataToSend;
      if (formData.asap) {
        const val = parseFloat(formData.asapDurationValue);
        if (!val || val <= 0) { setError('Czas trwania musi być większy od 0.'); return; }
        const durationHours = formData.asapDurationUnit === 'days' ? val * 24 : val;
        dataToSend = {
          name: formData.name,
          asap: true,
          asapDurationHours: durationHours,
          workerCount: formData.workerCount,
          resources: formData.resources,
          totalCost: formData.totalCost,
          crashingCostPerDay: formData.crashingCostPerDay,
          maxCrashingDays: formData.maxCrashingDays,
          predecessorIds: selectedPredecessors.join(',')
        };
      } else {
        dataToSend = {
          ...formData,
          asap: false,
          predecessorIds: selectedPredecessors.join(',')
        };
      }
      await axios.post('/api/operations', dataToSend);
      setFormData({
        name: '', startTime: '', endTime: '', asap: false,
        asapDurationValue: '', asapDurationUnit: 'hours',
        workerCount: 1, resources: '',
        totalCost: 0, crashingCostPerDay: 0, maxCrashingDays: 0, predecessorIds: ''
      });
      setSelectedPredecessors([]);
      refreshAll();
    } catch (err) {
      const msg = err.response?.data || "Błąd zapisu — upewnij się, że backend działa.";
      setError(typeof msg === 'string' ? msg : JSON.stringify(msg));
    }
  };

  const handleDelete = async (id) => {
    if (!confirm("Czy na pewno chcesz usunąć tę operację?")) return;
    try {
      await axios.delete(`/api/operations/${id}`);
      refreshAll();
    } catch (err) {
      setError("Błąd usuwania operacji.");
    }
  };

  const handleDeleteAll = async () => {
    if (!confirm("Czy na pewno chcesz usunąć WSZYSTKIE operacje?")) return;
    try {
      await axios.delete('/api/operations');
      refreshAll();
    } catch (err) {
      setError("Błąd usuwania operacji.");
    }
  };

  const handleExport = () => {
    window.location.href = '/api/operations/export';
  };

  const handleApplyCrashing = async () => {
    try {
      await Promise.all(
        operations.map(op =>
          axios.put(`/api/operations/${op.id}/crash/${crashingDays[op.id] || 0}`)
        )
      );
      refreshAll();
    } catch (err) {
      const msg = err.response?.data || 'Błąd zastosowania skracania.';
      setError(typeof msg === 'string' ? msg : JSON.stringify(msg));
    }
  };

  const handleImport = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (!confirm(`Wczytać operacje z pliku "${file.name}"? Obecne dane zostaną zastąpione.`)) {
      fileInputRef.current.value = '';
      return;
    }

    try {
      const form = new FormData();
      form.append('file', file);
      await axios.post('/api/operations/import', form);
      refreshAll();
      setError('');
    } catch (err) {
      const msg = err.response?.data || "Błąd importu — sprawdź format pliku.";
      setError(typeof msg === 'string' ? msg : JSON.stringify(msg));
    }
    fileInputRef.current.value = '';
  };

  const handleMerge = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    try {
      const form = new FormData();
      form.append('file', file);
      await axios.post('/api/operations/import-merge', form);
      refreshAll();
      setError('');
    } catch (err) {
      const msg = err.response?.data || 'Błąd scalania — sprawdź format pliku.';
      setError(typeof msg === 'string' ? msg : JSON.stringify(msg));
    }
    mergeFileInputRef.current.value = '';
  };

  const btnStyle = { padding: '8px 16px', cursor: 'pointer', border: 'none', fontWeight: 'bold', borderRadius: '4px' };

  return (
    <div style={{ padding: '20px', fontFamily: 'Arial', color: 'white', backgroundColor: '#1a1a1a', minHeight: '100vh' }}>
      <h1>Harmonogram Operacji</h1>

      {error && <div style={{ color: '#ff6b6b', marginBottom: '15px', padding: '10px', border: '1px solid #ff6b6b', borderRadius: '4px' }}>{error}</div>}

      <div style={{ marginBottom: '20px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        <button onClick={handleExport} style={{ ...btnStyle, background: '#17a2b8', color: 'white' }}>
          Zapisz do pliku (JSON)
        </button>
        <button onClick={() => fileInputRef.current.click()} style={{ ...btnStyle, background: '#ffc107', color: 'black' }}>
          Wczytaj z pliku (JSON)
        </button>
        <button onClick={() => mergeFileInputRef.current.click()} style={{ ...btnStyle, background: '#6f42c1', color: 'white' }}>
          Dołącz z pliku (JSON)
        </button>
        <input ref={fileInputRef} type="file" accept=".json" onChange={handleImport} style={{ display: 'none' }} />
        <input ref={mergeFileInputRef} type="file" accept=".json" onChange={handleMerge} style={{ display: 'none' }} />
        {operations.length > 0 && (
          <button onClick={handleDeleteAll} style={{ ...btnStyle, background: '#dc3545', color: 'white' }}>
            Usuń wszystkie
          </button>
        )}
      </div>

      <div style={{ display: 'flex', gap: '24px', alignItems: 'flex-start' }}>

      {/* LEWA KOLUMNA: formularz */}
      <div style={{ flexShrink: 0, width: '300px' }}>
      <form onSubmit={handleSubmit} style={{ display: 'grid', gap: '8px' }}>
        <label>Nazwa operacji:</label>
        <input type="text" placeholder="np. Montaż" required value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} />

        <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', userSelect: 'none' }}>
          <input
            type="checkbox"
            checked={formData.asap}
            onChange={e => setFormData({...formData, asap: e.target.checked})}
            style={{ width: '16px', height: '16px', accentColor: '#28a745', cursor: 'pointer' }}
          />
          <span>Rozpocznij jak najwcześniej (ASAP)</span>
        </label>

        {!formData.asap && (
          <>
            <label>Czas rozpoczęcia:</label>
            <input type="datetime-local" required value={formData.startTime} onChange={e => setFormData({...formData, startTime: e.target.value})} />

            <label>Czas zakończenia:</label>
            <input type="datetime-local" required value={formData.endTime} onChange={e => setFormData({...formData, endTime: e.target.value})} />
          </>
        )}

        {formData.asap && (
          <>
            <label>Czas trwania operacji:</label>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="number"
                min="0.5"
                step="0.5"
                placeholder="np. 8"
                required
                value={formData.asapDurationValue}
                onChange={e => setFormData({...formData, asapDurationValue: e.target.value})}
                style={{ flex: 1 }}
              />
              <select
                value={formData.asapDurationUnit}
                onChange={e => setFormData({...formData, asapDurationUnit: e.target.value})}
                style={{ background: '#333', color: 'white', border: '1px solid #555', borderRadius: '4px', padding: '6px 10px', cursor: 'pointer' }}
              >
                <option value="hours">godziny</option>
                <option value="days">dni</option>
              </select>
            </div>
          </>
        )}

        <label>Liczba pracowników:</label>
        <input type="number" min="1" required value={formData.workerCount}
          onChange={e => setFormData({...formData, workerCount: parseInt(e.target.value) || 1})} />

        <label>Zasoby:</label>
        <input type="text" placeholder="np. Tokarka" value={formData.resources} onChange={e => setFormData({...formData, resources: e.target.value})} />

        <label>Koszt całkowity (PLN):</label>
        <input type="number" step="0.01" value={formData.totalCost}
          onChange={e => setFormData({...formData, totalCost: parseFloat(e.target.value) || 0})} />

        <label>Koszt skracania za 1 dzień (PLN):</label>
        <input type="number" step="0.01" value={formData.crashingCostPerDay}
          onChange={e => setFormData({...formData, crashingCostPerDay: parseFloat(e.target.value) || 0})} />

        <label>Maksymalne skrócenie (dni):</label>
        <input type="number" min="0" value={formData.maxCrashingDays}
          onChange={e => setFormData({...formData, maxCrashingDays: parseInt(e.target.value) || 0})} />

        {operations.length > 0 && (
          <>
            <label>Operacje poprzedzające:</label>
            <div style={{
              border: '1px solid #555', borderRadius: '4px', padding: '8px',
              background: '#2a2a2a', maxHeight: '150px', overflowY: 'auto'
            }}>
              {operations.map(op => (
                <label key={op.id} style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '3px 0', cursor: 'pointer', fontSize: '14px' }}>
                  <input
                    type="checkbox"
                    checked={selectedPredecessors.includes(op.uuid)}
                    onChange={e => {
                      if (e.target.checked) {
                        setSelectedPredecessors([...selectedPredecessors, op.uuid]);
                      } else {
                        setSelectedPredecessors(selectedPredecessors.filter(uid => uid !== op.uuid));
                      }
                    }}
                  />
                  {op.name} (ID: {op.id})
                </label>
              ))}
            </div>
          </>
        )}

        <button type="submit" style={{ background: '#28a745', color: 'white', padding: '12px', cursor: 'pointer', border: 'none', fontWeight: 'bold', marginTop: '6px' }}>
          ZAPISZ OPERACJĘ
        </button>
      </form>
      </div>

      {/* PRAWA KOLUMNA: tabela + statystyki */}
      <div style={{ flex: 1, minWidth: 0 }}>
      <h2 style={{ marginTop: 0 }}>Lista zadań</h2>
      <div style={{ overflowX: 'auto' }}>
      <table border="1" style={{ width: '100%', borderCollapse: 'collapse', color: 'white', borderColor: '#444' }}>
        <thead>
          <tr style={{ background: '#333' }}>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Nazwa</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Start</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Koniec</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Czas trwania</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Pracownicy</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Zasoby</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Koszt całk.</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Skracanie/d</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Maks. skr.</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Poprzedniki</th>
            <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Akcje</th>
          </tr>
        </thead>
        <tbody>
          {operations.length > 0 ? operations.map((op, index) => (
            <tr key={op.id || index} style={{ textAlign: 'center' }}>
              <td style={{ padding: '8px', whiteSpace: 'nowrap' }}>{op.name}</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', fontSize: '12px' }}>{op.asap ? <span style={{ color: '#4DC0E1', fontWeight: 'bold' }}>ASAP</span> : (op.startTime ? new Date(op.startTime).toLocaleString() : '-')}</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', fontSize: '12px' }}>{op.asap ? <span style={{ color: '#aaa' }}>—</span> : (op.endTime ? new Date(op.endTime).toLocaleString() : '-')}</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap' }}>{op.asap
                ? (op.asapDurationHours != null
                    ? (op.asapDurationHours % 24 === 0
                        ? (op.asapDurationHours / 24).toFixed(1) + ' d'
                        : parseFloat(op.asapDurationHours.toFixed(1)) + ' h')
                    : '-')
                : (op.durationInDays ? op.durationInDays + ' d' : '-')
              }</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap' }}>{op.workerCount}</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap' }}>{op.resources || '-'}</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', fontSize: '12px' }}>{op.totalCost != null ? op.totalCost.toLocaleString('pl-PL', { maximumFractionDigits: 1 }) + ' PLN' : '0 PLN'}</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', fontSize: '12px', color: '#ff6b6b' }}>{op.crashingCostPerDay != null ? op.crashingCostPerDay.toLocaleString('pl-PL', { maximumFractionDigits: 1 }) + ' PLN' : '0 PLN'}</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', color: '#4da3ff' }}>{op.maxCrashingDays != null ? op.maxCrashingDays + ' d' : '0 d'}</td>
              <td style={{ padding: '6px 8px', color: '#ff9800', fontSize: '12px', whiteSpace: 'nowrap' }}>
                {op.predecessorIds ? op.predecessorIds.split(',').map(uid => {
                  const pred = operations.find(o => o.uuid === uid.trim());
                  return pred ? pred.name : uid.trim().substring(0, 8) + '...';
                }).join(', ') : '-'}
              </td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap' }}>
                <button onClick={() => handleDelete(op.id)}
                  style={{ background: '#dc3545', color: 'white', border: 'none', padding: '4px 8px', cursor: 'pointer', borderRadius: '3px', fontSize: '12px' }}>
                  Usuń
                </button>
              </td>
            </tr>
          )) : (
            <tr>
              <td colSpan="11" style={{ padding: '20px', textAlign: 'center' }}>Brak operacji w bazie danych.</td>
            </tr>
          )}
        </tbody>
      </table>
      </div>

      {operations.length > 0 && (() => {
        const baseCost = operations.reduce((s, op) => s + (op.totalCost || 0), 0);
        const appliedCrashCost = operations.reduce((s, op) => s + (op.crashingCostPerDay || 0) * (op.crashedDays || 0), 0);
        const maxCrashCost = operations.reduce((s, op) => s + (op.crashingCostPerDay || 0) * (op.maxCrashingDays || 0), 0);
        const finalCost = baseCost + appliedCrashCost;

        const earlyDays = ganttData ? Math.ceil(ganttData.totalDays) : null;
        const lateDays = ganttLateData ? Math.ceil(ganttLateData.totalDays) : null;
        const appliedCrashDays = operations.reduce((s, op) => s + (op.crashedDays || 0), 0);
        const maxCrashDays = operations.reduce((s, op) => s + (op.maxCrashingDays || 0), 0);

        const statLabel = (txt) => ({ color: '#aaa', fontSize: '12px', display: 'block', marginBottom: '2px' });
        const statVal = (color) => ({ color, fontWeight: 'bold', fontSize: '15px' });
        const cell = { background: '#1e1e1e', borderRadius: '6px', padding: '10px 16px', border: '1px solid #333', minWidth: '140px' };
        const sectionTitle = { color: '#888', fontSize: '11px', fontWeight: 'bold', textTransform: 'uppercase', letterSpacing: '1px', marginBottom: '8px' };

        return (
          <div style={{ marginTop: '16px', background: '#2a2a2a', borderRadius: '8px', border: '1px solid #444', padding: '14px 20px' }}>
            <div style={{ display: 'flex', gap: '32px', flexWrap: 'wrap', alignItems: 'flex-start' }}>

              {/* Liczba operacji*/}
              <div style={{ flexShrink: 0 }}>
                <div style={sectionTitle}>liczba operacji</div>
                <div style={{ ...cell, border: '1px solid #555' }}>
                  <span style={statLabel()}>Liczba operacji</span>
                  <span style={statVal('#fff')}>{operations.length}</span>
                </div>
              </div>

              {/* SEKCJA: Koszty finansowe */}
              <div style={{ flex: 1, minWidth: '280px' }}>
                <div style={sectionTitle}>Koszty finansowe</div>
                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                  <div style={cell}>
                    <span style={statLabel()}>Koszt bazowy projektu</span>
                    <span style={statVal('#4DC0E1')}>{baseCost.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN</span>
                  </div>
                  <div style={cell}>
                    <span style={statLabel()}>+ Koszt skracania</span>
                    <span style={statVal('#ff6b6b')}>+ {appliedCrashCost.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN</span>
                  </div>
                  <div style={{ ...cell, border: '1px solid #4DC0E1' }}>
                    <span style={statLabel()}>= Koszt końcowy</span>
                    <span style={statVal('#fff')}>{finalCost.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN</span>
                  </div>
                  <div style={cell}>
                    <span style={statLabel()}>Maks. koszt skracania</span>
                    <span style={statVal('#ff9800')}>{maxCrashCost.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN</span>
                  </div>
                </div>
              </div>

              {/* SEKCJA: Czas trwania projektu */}
              {earlyDays !== null && (
                <div style={{ flex: 1, minWidth: '280px' }}>
                  <div style={sectionTitle}>Czas trwania projektu</div>
                  <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                    <div style={cell}>
                      <span style={statLabel()}>Czas bazowy (early)</span>
                      <span style={statVal('#4DC0E1')}>{earlyDays} dni</span>
                    </div>
                    {appliedCrashDays > 0 && (
                      <div style={cell}>
                        <span style={statLabel()}>− Skrócono łącznie</span>
                        <span style={statVal('#ff6b6b')}>− {appliedCrashDays} dni</span>
                      </div>
                    )}
                    <div style={{ ...cell, border: '1px solid #4DC0E1' }}>
                      <span style={statLabel()}>= Czas końcowy (early)</span>
                      <span style={statVal('#fff')}>{earlyDays - appliedCrashDays} dni</span>
                    </div>
                    {maxCrashDays > 0 && (
                      <div style={cell}>
                        <span style={statLabel()}>Maks. możliwe skrócenie</span>
                        <span style={statVal('#ff9800')}>− {maxCrashDays} dni</span>
                      </div>
                    )}
                    {lateDays !== null && lateDays !== earlyDays && (
                      <div style={cell}>
                        <span style={statLabel()}>Czas (late)</span>
                        <span style={statVal('#a78bfa')}>{lateDays} dni</span>
                      </div>
                    )}
                  </div>
                </div>
              )}

            </div>
          </div>
        );
      })()}

      </div>{/* koniec prawej kolumny */}
      </div>{/* koniec flex row */}

      {operations.some(op => (op.maxCrashingDays || 0) > 0) && (
        <>
          <h2 style={{ marginTop: '40px' }}>Skracanie operacji (Crashing)</h2>
          <div style={{ background: '#1e1e2e', border: '1px solid #444', borderRadius: '8px', padding: '20px', marginBottom: '10px' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', color: 'white' }}>
              <thead>
                <tr style={{ background: '#2a2a3a', borderBottom: '1px solid #555' }}>
                  <th style={{ padding: '10px', textAlign: 'left' }}>Operacja</th>
                  <th style={{ padding: '10px', textAlign: 'center' }}>Koszt skracania / dzień</th>
                  <th style={{ padding: '10px', textAlign: 'center' }}>Maks. skrócenie</th>
                  <th style={{ padding: '10px', textAlign: 'center', minWidth: '220px' }}>Liczba dni skrócenia</th>
                  <th style={{ padding: '10px', textAlign: 'right' }}>Koszt skrócenia</th>
                </tr>
              </thead>
              <tbody>
                {operations.filter(op => (op.maxCrashingDays || 0) > 0).map(op => {
                  const days = crashingDays[op.id] || 0;
                  const cost = days * (op.crashingCostPerDay || 0);
                  return (
                    <tr key={op.id} style={{ borderBottom: '1px solid #333' }}>
                      <td style={{ padding: '10px' }}>{op.name}</td>
                      <td style={{ padding: '10px', textAlign: 'center', color: '#ff6b6b' }}>
                        {(op.crashingCostPerDay || 0).toLocaleString('pl-PL')} PLN
                      </td>
                      <td style={{ padding: '10px', textAlign: 'center', color: '#4da3ff' }}>
                        {op.maxCrashingDays} dni
                      </td>
                      <td style={{ padding: '10px', textAlign: 'center' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', justifyContent: 'center' }}>
                          <input
                            type="range"
                            min={0}
                            max={op.maxCrashingDays}
                            value={days}
                            onChange={e => setCrashingDays({ ...crashingDays, [op.id]: parseInt(e.target.value) })}
                            style={{ width: '120px', accentColor: '#ff6b6b' }}
                          />
                          <input
                            type="number"
                            min={0}
                            max={op.maxCrashingDays}
                            value={days}
                            onChange={e => {
                              const v = Math.min(Math.max(parseInt(e.target.value) || 0, 0), op.maxCrashingDays);
                              setCrashingDays({ ...crashingDays, [op.id]: v });
                            }}
                            style={{ width: '52px', background: '#333', color: 'white', border: '1px solid #555', borderRadius: '4px', padding: '4px', textAlign: 'center' }}
                          />
                          <span style={{ color: '#aaa', fontSize: '12px' }}>dni</span>
                        </div>
                      </td>
                      <td style={{ padding: '10px', textAlign: 'right', color: cost > 0 ? '#ff6b6b' : '#aaa', fontWeight: cost > 0 ? 'bold' : 'normal' }}>
                        {cost.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN
                      </td>
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr style={{ borderTop: '2px solid #555', background: '#2a2a3a' }}>
                  <td colSpan={4} style={{ padding: '10px', textAlign: 'right', color: '#aaa', fontSize: '13px' }}>Sumaryczny koszt skracania:</td>
                  <td style={{ padding: '10px', textAlign: 'right', color: '#ff6b6b', fontWeight: 'bold', fontSize: '16px' }}>
                    {operations.reduce((sum, op) => sum + ((crashingDays[op.id] || 0) * (op.crashingCostPerDay || 0)), 0)
                      .toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN
                  </td>
                </tr>
              </tfoot>
            </table>
            <div style={{ marginTop: '14px', display: 'flex', gap: '12px', alignItems: 'center' }}>
              <button onClick={handleApplyCrashing} style={{ ...btnStyle, background: '#e53935', color: 'white', fontSize: '14px' }}>
                Zastosuj skracanie
              </button>
              <button onClick={() => {
                const reset = {};
                operations.forEach(op => { reset[op.id] = 0; });
                setCrashingDays(reset);
              }} style={{ ...btnStyle, background: '#555', color: 'white', fontSize: '14px' }}>
                Resetuj
              </button>
              <span style={{ color: '#aaa', fontSize: '12px' }}>
                Przesuwaj suwaki i kliknij „Zastosuj skracanie", by zaktualizować wykresy.
              </span>
            </div>
          </div>
        </>
      )}

      <h2 style={{ marginTop: '40px' }}>Wykres Gantta — Najwcześniejsze terminy rozpoczęcia</h2>
      <GanttChart ganttData={ganttData} scrollRef={ganttEarlyRef} onScroll={syncScroll(workerEarlyRef)} />
      <WorkerChart ganttData={ganttData} title="Wykres pracowników — Najwcześniejsze terminy" scrollRef={workerEarlyRef} onScroll={syncScroll(ganttEarlyRef)} />

      <h2 style={{ marginTop: '40px' }}>Wykres Gantta — Najpóźniejsze terminy rozpoczęcia</h2>
      <GanttChart ganttData={ganttLateData} scrollRef={ganttLateRef} onScroll={syncScroll(workerLateRef)} />
      <WorkerChart ganttData={ganttLateData} title="Wykres pracowników — Najpóźniejsze terminy" scrollRef={workerLateRef} onScroll={syncScroll(ganttLateRef)} />
    </div>
  );
}

export default App;
