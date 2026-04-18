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

  // Funkcja pomocnicza obliczająca czas trwania w dniach
  const calculateDurationInDays = () => {
    if (!formData.startTime || !formData.endTime) return 0;
    const start = new Date(formData.startTime);
    const end = new Date(formData.endTime);
    const diffTime = end - start;
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays > 0 ? diffDays : 0;
  };

  const fetchOperations = async () => {
    try {
      const res = await axios.get('/api/operations');
      if (Array.isArray(res.data)) {
        setOperations(res.data);
      }
    } catch (err) {
      console.error("Błąd pobierania danych z Javy:", err);
    }
  };

  useEffect(() => {
    fetchOperations();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();

    // Dodatkowe zabezpieczenie w logice przed wysłaniem
    const duration = calculateDurationInDays();
    if (formData.maxCrashingDays >= duration && duration > 0) {
      alert(`Błąd: Maksymalne skrócenie (${formData.maxCrashingDays} dni) musi być mniejsze niż czas trwania operacji (${duration} dni).`);
      return;
    }

    try {
      await axios.post('/api/operations', formData);
      alert("Dodano pomyślnie!");

      setFormData({
        name: '', startTime: '', endTime: '', workerCount: 1, resources: '',
        totalCost: 0, crashingCostPerDay: 0, maxCrashingDays: 0
      });

      fetchOperations();
    } catch (err) {
      alert("Błąd zapisu! Upewnij się, że backend działa i posiada pole maxCrashingDays.");
    }
  };

  const currentDuration = calculateDurationInDays();

  return (
    <div style={{ padding: '20px', fontFamily: 'Arial', color: 'white', backgroundColor: '#1a1a1a', minHeight: '100vh' }}>
      <h1>Harmonogram Operacji</h1>

      <form onSubmit={handleSubmit} style={{ marginBottom: '40px', display: 'grid', gap: '10px', maxWidth: '450px' }}>
        <label>Nazwa operacji:</label>
        <input type="text" placeholder="np. Montaż" required value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} />

        <label>Czas rozpoczęcia:</label>
        <input type="datetime-local" required value={formData.startTime} onChange={e => setFormData({...formData, startTime: e.target.value})} />

        <label>Czas zakończenia:</label>
        <input type="datetime-local" required value={formData.endTime} onChange={e => setFormData({...formData, endTime: e.target.value})} />

        <label>Liczba pracowników:</label>
        <input
          type="number"
          min="1"
          required
          value={formData.workerCount}
          onChange={e => setFormData({...formData, workerCount: parseInt(e.target.value) || 1})}
        />

        <label>Zasoby:</label>
        <input type="text" placeholder="np. Tokarka" value={formData.resources} onChange={e => setFormData({...formData, resources: e.target.value})} />

        <label>Koszt całkowity (PLN):</label>
        <input type="number" step="0.01" value={formData.totalCost} onChange={e => setFormData({...formData, totalCost: parseFloat(e.target.value) || 0})} />

        <label>Koszt skracania za 1 dzień (PLN):</label>
        <input type="number" step="0.01" value={formData.crashingCostPerDay} onChange={e => setFormData({...formData, crashingCostPerDay: parseFloat(e.target.value) || 0})} />

        <label>Maksymalne skrócenie (dni):</label>
        <input
          type="number"
          min="0"
          // Ustawiamy atrybut max, aby zablokować suwak w przeglądarce
          max={currentDuration > 0 ? currentDuration - 1 : 0}
          value={formData.maxCrashingDays}
          onChange={e => {
            const val = parseInt(e.target.value) || 0;
            const maxAllowed = currentDuration > 0 ? currentDuration - 1 : 0;
            // Jeśli użytkownik wpisze ręcznie zbyt dużą liczbę, korygujemy ją do dozwolonego max
            setFormData({...formData, maxCrashingDays: val > maxAllowed ? maxAllowed : val});
          }}
        />
        <small style={{ color: currentDuration > 0 ? '#4da3ff' : '#ff6b6b' }}>
          {currentDuration > 0
            ? `Aktualny czas trwania: ${currentDuration} dni (Maks. skrócenie: ${currentDuration - 1})`
            : "Wybierz poprawne daty, aby określić limit skracania."}
        </small>

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
              <td>{op.workerCount}</td>
              <td>{op.resources || '-'}</td>
              <td>{op.totalCost ? op.totalCost.toLocaleString() + ' PLN' : '0 PLN'}</td>
              <td style={{ color: '#ff6b6b' }}>{op.crashingCostPerDay ? op.crashingCostPerDay.toLocaleString() + ' PLN' : '0 PLN'}</td>
              <td style={{ color: '#4da3ff' }}>{op.maxCrashingDays ? op.maxCrashingDays + ' dni' : '0 dni'}</td>
            </tr>
          )) : (
            <tr>
              <td colSpan="8" style={{ padding: '20px', textAlign: 'center' }}>Brak operacji w bazie danych.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export default App;