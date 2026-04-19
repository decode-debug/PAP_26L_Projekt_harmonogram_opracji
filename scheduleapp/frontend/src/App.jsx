import { useState, useEffect, useRef } from 'react'
import axios from 'axios'

function GanttChart({ ganttData }) {
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
        const fromXPercent = ((pred.startOffsetDays + pred.durationDays) / totalDays) * 100;
        const toXPercent = (bar.startOffsetDays / totalDays) * 100;
        const fromY = headerHeight + predIdx * (barHeight + barGap) + barGap + barHeight / 2;
        const toY = headerHeight + i * (barHeight + barGap) + barGap + barHeight / 2;
        arrows.push({ fromXPercent, toXPercent, fromY, toY, key: `${predId}-${bar.operationId}` });
      });
    }
  });

  return (
    <div style={{ overflowX: 'auto', marginTop: '10px' }}>
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
            left: `calc(${labelWidth}px + ${(i / fullDays) * 100}% - ${(i / fullDays) * (labelWidth + chartPadding * 2)}px)`,
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
          left: labelWidth + chartPadding + 'px',
          width: `calc(100% - ${labelWidth + chartPadding * 2}px)`,
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
          const leftPercent = (bar.startOffsetDays / totalDays) * 100;
          const widthPercent = Math.max((bar.durationDays / totalDays) * 100, 1);
          const top = headerHeight + i * (barHeight + barGap) + barGap;

          const barStart = new Date(bar.startTime);
          const barEnd = new Date(bar.endTime);
          const tooltip = `${bar.name}\n${barStart.toLocaleString('pl-PL')} — ${barEnd.toLocaleString('pl-PL')}\nPracownicy: ${bar.workerCount}${bar.resources ? '\nZasoby: ' + bar.resources : ''}`;

          return (
            <div key={bar.operationId} style={{ position: 'absolute', top: top + 'px', left: '0', right: '0', height: barHeight + 'px', display: 'flex', alignItems: 'center' }}>
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

function WorkerChart({ ganttData }) {
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

  // Buduj SVG path (wykres warstwowy/fill)
  const plotWidth = chartContentWidth - labelWidth - chartPadding * 2;
  const plotHeight = chartHeight - headerHeight - 10;

  const points = workerCounts.map((count, s) => {
    const x = ((s * step) / totalDays) * plotWidth;
    const y = plotHeight - (count / maxWorkers) * plotHeight;
    return `${x},${y}`;
  });
  // Zamknij obszar w dół
  const lastX = ((slots * step) / totalDays) * plotWidth;
  const areaPath = `M ${points.join(' L ')} L ${lastX},${plotHeight} L 0,${plotHeight} Z`;
  const linePath = `M ${points.join(' L ')}`;

  return (
    <div style={{ overflowX: 'auto', marginTop: '6px', marginBottom: '30px' }}>
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
            const x = ((s * step) / totalDays) * plotWidth;
            const w = Math.max((step / totalDays) * plotWidth - 1, 1);
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
          Liczba pracowników w czasie
        </div>
      </div>
    </div>
  );
}

function App() {
  const [operations, setOperations] = useState([]);
  const [ganttData, setGanttData] = useState(null);
  const [ganttLateData, setGanttLateData] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    startTime: '',
    endTime: '',
    workerCount: 1,
    resources: '',
    totalCost: 0,
    crashingCostPerDay: 0,
    maxCrashingDays: 0,
    predecessorIds: ''
  });
  const [error, setError] = useState('');
  const fileInputRef = useRef(null);
  const [selectedPredecessors, setSelectedPredecessors] = useState([]);

  const fetchOperations = async () => {
    try {
      const res = await axios.get('/api/operations');
      if (Array.isArray(res.data)) {
        setOperations(res.data);
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
      const dataToSend = {
        ...formData,
        predecessorIds: selectedPredecessors.join(',')
      };
      await axios.post('/api/operations', dataToSend);
      setFormData({
        name: '', startTime: '', endTime: '', workerCount: 1, resources: '',
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
        <input ref={fileInputRef} type="file" accept=".json" onChange={handleImport} style={{ display: 'none' }} />
        {operations.length > 0 && (
          <button onClick={handleDeleteAll} style={{ ...btnStyle, background: '#dc3545', color: 'white' }}>
            Usuń wszystkie
          </button>
        )}
      </div>

      <form onSubmit={handleSubmit} style={{ marginBottom: '40px', display: 'grid', gap: '10px', maxWidth: '450px' }}>
        <label>Nazwa operacji:</label>
        <input type="text" placeholder="np. Montaż" required value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} />

        <label>Czas rozpoczęcia:</label>
        <input type="datetime-local" required value={formData.startTime} onChange={e => setFormData({...formData, startTime: e.target.value})} />

        <label>Czas zakończenia:</label>
        <input type="datetime-local" required value={formData.endTime} onChange={e => setFormData({...formData, endTime: e.target.value})} />

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

        <button type="submit" style={{ background: '#28a745', color: 'white', padding: '12px', cursor: 'pointer', border: 'none', fontWeight: 'bold', marginTop: '10px' }}>
          ZAPISZ OPERACJĘ
        </button>
      </form>

      <h2>Lista zadań</h2>
      <table border="1" style={{ width: '100%', borderCollapse: 'collapse', color: 'white', borderColor: '#444' }}>
        <thead>
          <tr style={{ background: '#333' }}>
            <th style={{ padding: '10px' }}>Nazwa</th>
            <th>Start</th>
            <th>Koniec</th>
            <th>Czas trwania</th>
            <th>Pracownicy</th>
            <th>Zasoby</th>
            <th>Koszt całkowity</th>
            <th>Koszt skracania/doba</th>
            <th>Maks. skrócenie</th>
            <th>Poprzedniki</th>
            <th>Akcje</th>
          </tr>
        </thead>
        <tbody>
          {operations.length > 0 ? operations.map((op, index) => (
            <tr key={op.id || index} style={{ textAlign: 'center' }}>
              <td style={{ padding: '8px' }}>{op.name}</td>
              <td>{op.startTime ? new Date(op.startTime).toLocaleString() : '-'}</td>
              <td>{op.endTime ? new Date(op.endTime).toLocaleString() : '-'}</td>
              <td>{op.durationInDays ? op.durationInDays + ' dni' : '-'}</td>
              <td>{op.workerCount}</td>
              <td>{op.resources || '-'}</td>
              <td>{op.totalCost != null ? op.totalCost.toLocaleString() + ' PLN' : '0 PLN'}</td>
              <td style={{ color: '#ff6b6b' }}>{op.crashingCostPerDay != null ? op.crashingCostPerDay.toLocaleString() + ' PLN' : '0 PLN'}</td>
              <td style={{ color: '#4da3ff' }}>{op.maxCrashingDays != null ? op.maxCrashingDays + ' dni' : '0 dni'}</td>
              <td style={{ color: '#ff9800', fontSize: '12px' }}>
                {op.predecessorIds ? op.predecessorIds.split(',').map(uid => {
                  const pred = operations.find(o => o.uuid === uid.trim());
                  return pred ? pred.name : uid.trim().substring(0, 8) + '...';
                }).join(', ') : '-'}
              </td>
              <td>
                <button onClick={() => handleDelete(op.id)}
                  style={{ background: '#dc3545', color: 'white', border: 'none', padding: '4px 10px', cursor: 'pointer', borderRadius: '3px' }}>
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

      <h2 style={{ marginTop: '40px' }}>Wykres Gantta</h2>
      <GanttChart ganttData={ganttData} />
      <WorkerChart ganttData={ganttData} />

      <h2 style={{ marginTop: '40px' }}>Wykres Gantta — Najpóźniejsze terminy rozpoczęcia</h2>
      <GanttChart ganttData={ganttLateData} />
      <WorkerChart ganttData={ganttLateData} />
    </div>
  );
}

export default App;