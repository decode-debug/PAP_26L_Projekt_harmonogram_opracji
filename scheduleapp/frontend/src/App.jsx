import { useState, useEffect } from 'react'
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

  return (
    <div style={{ padding: '20px', fontFamily: 'Arial', color: 'white', backgroundColor: '#1a1a1a', minHeight: '100vh' }}>
      <h1>Harmonogram Operacji</h1>

      {error && <div style={{ color: '#ff6b6b', marginBottom: '15px', padding: '10px', border: '1px solid #ff6b6b', borderRadius: '4px' }}>{error}</div>}

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
            </tr>
          )) : (
            <tr>
              <td colSpan="9" style={{ padding: '20px', textAlign: 'center' }}>Brak operacji w bazie danych.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export default App;