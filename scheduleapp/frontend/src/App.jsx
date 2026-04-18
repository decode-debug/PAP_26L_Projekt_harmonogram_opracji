import { useState, useEffect, useRef } from 'react'
import axios from 'axios'

function App() {
  const [operations, setOperations] = useState([]);
  const [formData, setFormData] = useState({
    name: '',
    startTime: '',
    endTime: '',
    workerCount: 1,
    resources: '',
    totalCost: 0,
    crashingCostPerDay: 0,
    maxCrashingDays: 0
  });
  const [error, setError] = useState('');
  const fileInputRef = useRef(null);

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

  useEffect(() => {
    fetchOperations();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    try {
      await axios.post('/api/operations', formData);
      setFormData({
        name: '', startTime: '', endTime: '', workerCount: 1, resources: '',
        totalCost: 0, crashingCostPerDay: 0, maxCrashingDays: 0
      });
      fetchOperations();
    } catch (err) {
      const msg = err.response?.data || "Błąd zapisu — upewnij się, że backend działa.";
      setError(typeof msg === 'string' ? msg : JSON.stringify(msg));
    }
  };

  const handleDelete = async (id) => {
    if (!confirm("Czy na pewno chcesz usunąć tę operację?")) return;
    try {
      await axios.delete(`/api/operations/${id}`);
      fetchOperations();
    } catch (err) {
      setError("Błąd usuwania operacji.");
    }
  };

  const handleDeleteAll = async () => {
    if (!confirm("Czy na pewno chcesz usunąć WSZYSTKIE operacje?")) return;
    try {
      await axios.delete('/api/operations');
      fetchOperations();
    } catch (err) {
      setError("Błąd usuwania operacji.");
    }
  };

  const handleExport = async () => {
    try {
      const res = await axios.get('/api/operations/export');
      const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `harmonogram_${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError("Błąd eksportu danych.");
    }
  };

  const handleImport = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    try {
      const text = await file.text();
      const data = JSON.parse(text);
      if (!Array.isArray(data)) {
        setError("Plik musi zawierać tablicę operacji.");
        return;
      }
      if (!confirm(`Wczytać ${data.length} operacji z pliku? Obecne dane zostaną zastąpione.`)) return;
      await axios.post('/api/operations/import', data);
      fetchOperations();
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
              <td>
                <button onClick={() => handleDelete(op.id)}
                  style={{ background: '#dc3545', color: 'white', border: 'none', padding: '4px 10px', cursor: 'pointer', borderRadius: '3px' }}>
                  Usuń
                </button>
              </td>
            </tr>
          )) : (
            <tr>
              <td colSpan="10" style={{ padding: '20px', textAlign: 'center' }}>Brak operacji w bazie danych.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export default App;