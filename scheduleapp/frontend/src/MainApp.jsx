import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import GanttChart from './components/GanttChart.jsx';
import WorkerChart from './components/WorkerChart.jsx';
import OperationForm from './components/OperationForm.jsx';
import OperationTable from './components/OperationTable.jsx';
import StatsPanel from './components/StatsPanel.jsx';
import CrashingPanel from './components/CrashingPanel.jsx';

const INITIAL_FORM = {
  name: '', startTime: '', endTime: '', asap: false,
  asapDurationValue: '', asapDurationUnit: 'hours',
  workerCount: 1, resources: '',
  totalCost: 0, crashingCostPerDay: 0, maxCrashingDays: 0, predecessorIds: ''
};

export default function MainApp({ user, onLogout }) {
  const [operations, setOperations] = useState([]);
  const [ganttData, setGanttData] = useState(null);
  const [ganttLateData, setGanttLateData] = useState(null);
  const [crashingDays, setCrashingDays] = useState({});
  const [formData, setFormData] = useState(INITIAL_FORM);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
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

  const updateForm = (field, value) => setFormData(f => ({ ...f, [field]: value }));

  const extractErrorMsg = (err, fallback) => {
    const msg = err.response?.data || fallback;
    return typeof msg === 'string' ? msg : JSON.stringify(msg);
  };

  const fetchData = async (url, onSuccess, label) => {
    try {
      const res = await axios.get(url);
      onSuccess(res.data);
    } catch (err) {
      console.error(`Błąd pobierania ${label}:`, err);
    }
  };

  const fetchOperations = () => fetchData('/api/operations', (data) => {
    if (!Array.isArray(data)) return;
    setOperations(data);
    const map = {};
    data.forEach(op => { map[op.id] = op.crashedDays || 0; });
    setCrashingDays(map);
  }, 'danych');

  const fetchGantt = () => fetchData('/api/operations/gantt', setGanttData, 'wykresu Gantta');
  const fetchGanttLate = () => fetchData('/api/operations/gantt-late', setGanttLateData, 'wykresu Gantta (późne starty)');

  const refreshAll = () => {
    fetchOperations();
    fetchGantt();
    fetchGanttLate();
  };

  useEffect(() => { refreshAll(); }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setNotice('');
    try {
      let dataToSend;
      if (formData.asap) {
        const val = parseFloat(formData.asapDurationValue);
        if (!val || val <= 0) { setError('Czas trwania musi być większy od 0.'); return; }
        const durationHours = formData.asapDurationUnit === 'days' ? val * 24 : val;
        dataToSend = {
          name: formData.name, asap: true, asapDurationHours: durationHours,
          workerCount: formData.workerCount, resources: formData.resources,
          totalCost: formData.totalCost, crashingCostPerDay: formData.crashingCostPerDay,
          maxCrashingDays: formData.maxCrashingDays, predecessorIds: selectedPredecessors.join(',')
        };
      } else {
        dataToSend = { ...formData, asap: false, predecessorIds: selectedPredecessors.join(',') };
      }
      await axios.post('/api/operations', dataToSend);
      setFormData(INITIAL_FORM);
      setSelectedPredecessors([]);
      refreshAll();
    } catch (err) {
      setError(extractErrorMsg(err, "Błąd zapisu — upewnij się, że backend działa."));
    }
  };

  const handleDelete = async (id) => {
    if (!confirm("Czy na pewno chcesz usunąć tę operację?")) return;
    try {
      await axios.delete(`/api/operations/${id}`);
      setNotice('');
      refreshAll();
    } catch (err) {
      setError("Błąd usuwania operacji.");
    }
  };

  const handleDeleteAll = async () => {
    if (!confirm("Czy na pewno chcesz usunąć WSZYSTKIE operacje?")) return;
    try {
      await axios.delete('/api/operations');
      setNotice('');
      refreshAll();
    } catch (err) {
      setError("Błąd usuwania operacji.");
    }
  };

  const handleExport = async () => {
    try {
      const res = await axios.get('/api/operations/export', { responseType: 'blob' });
      const blob = new Blob([res.data], { type: 'application/json' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'harmonogram.json';
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      setError('');
      setNotice('');
    } catch (err) {
      if (err.response?.status === 401) return;
      let msg = 'Błąd eksportu — spróbuj zalogować się ponownie.';
      if (err.response?.data) {
        if (err.response.data instanceof Blob) {
          msg = await err.response.data.text() || msg;
        } else {
          msg = typeof err.response.data === 'string' ? err.response.data : JSON.stringify(err.response.data);
        }
      }
      setError(msg);
    }
  };

  const handleApplyCrashing = async () => {
    try {
      await Promise.all(
        operations.map(op => axios.put(`/api/operations/${op.id}/crash/${crashingDays[op.id] || 0}`))
      );
      refreshAll();
    } catch (err) {
      setError(extractErrorMsg(err, 'Błąd zastosowania skracania.'));
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
      setNotice('');
    } catch (err) {
      setError(extractErrorMsg(err, "Błąd importu — sprawdź format pliku."));
    }
    fileInputRef.current.value = '';
  };

  const handleMerge = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    try {
      const form = new FormData();
      form.append('file', file);
      const res = await axios.post('/api/operations/import-merge', form);
      refreshAll();
      setError('');
      setNotice(res.data?.message || 'Dołączono operacje z pliku.');
    } catch (err) {
      setNotice('');
      setError(extractErrorMsg(err, 'Błąd scalania — sprawdź format pliku.'));
    }
    mergeFileInputRef.current.value = '';
  };

  const btnStyle = { padding: '8px 16px', cursor: 'pointer', border: 'none', fontWeight: 'bold', borderRadius: '4px' };

  return (
    <div style={{ padding: '20px', fontFamily: 'Arial', color: 'white', backgroundColor: '#1a1a1a', minHeight: '100vh', position: 'relative' }}>
      <div style={{ position: 'absolute', top: '20px', right: '20px', display: 'flex', alignItems: 'center', gap: '10px' }}>
        <span style={{ color: '#aaa', fontSize: '13px' }}>
          Zalogowany: <strong>{user?.name}</strong> <span style={{ color: '#666' }}>({user?.email})</span>
        </span>
        <button onClick={onLogout} style={{ ...btnStyle, background: '#444', color: '#ddd', fontSize: '12px', padding: '4px 12px' }}>
          Wyloguj
        </button>
      </div>
      <h1>Harmonogram Operacji</h1>

      {error && <div style={{ color: '#ff6b6b', marginBottom: '15px', padding: '10px', border: '1px solid #ff6b6b', borderRadius: '4px' }}>{error}</div>}
      {notice && <div style={{ color: '#8fd694', marginBottom: '15px', padding: '10px', border: '1px solid #2d7a34', borderRadius: '4px', background: '#142318' }}>{notice}</div>}

      <div style={{ marginBottom: '20px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        <button onClick={handleExport} style={{ ...btnStyle, background: '#17a2b8', color: 'white' }}>Zapisz do pliku (JSON)</button>
        <button onClick={() => fileInputRef.current.click()} style={{ ...btnStyle, background: '#ffc107', color: 'black' }}>Wczytaj z pliku (JSON)</button>
        <button onClick={() => mergeFileInputRef.current.click()} style={{ ...btnStyle, background: '#6f42c1', color: 'white' }}>Dołącz z pliku (JSON)</button>
        <input ref={fileInputRef} type="file" accept=".json" onChange={handleImport} style={{ display: 'none' }} />
        <input ref={mergeFileInputRef} type="file" accept=".json" onChange={handleMerge} style={{ display: 'none' }} />
        {operations.length > 0 && (
          <button onClick={handleDeleteAll} style={{ ...btnStyle, background: '#dc3545', color: 'white' }}>Usuń wszystkie</button>
        )}
      </div>

      <div style={{ display: 'flex', gap: '24px', alignItems: 'flex-start' }}>
        <div style={{ flexShrink: 0, width: '300px' }}>
          <OperationForm
            formData={formData}
            updateForm={updateForm}
            operations={operations}
            selectedPredecessors={selectedPredecessors}
            setSelectedPredecessors={setSelectedPredecessors}
            onSubmit={handleSubmit}
          />
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <h2 style={{ marginTop: 0 }}>Lista zadań</h2>
          <OperationTable operations={operations} onDelete={handleDelete} />
          <StatsPanel operations={operations} ganttData={ganttData} ganttLateData={ganttLateData} />
        </div>
      </div>

      {operations.some(op => (op.maxCrashingDays || 0) > 0) && (
        <>
          <h2 style={{ marginTop: '40px' }}>Skracanie operacji (Crashing)</h2>
          <CrashingPanel
            operations={operations}
            crashingDays={crashingDays}
            setCrashingDays={setCrashingDays}
            onApply={handleApplyCrashing}
          />
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
