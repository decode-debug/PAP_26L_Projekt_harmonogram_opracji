import { useState, useEffect } from 'react'
import axios from 'axios'

function App() {
  const [operations, setOperations] = useState([]); // Inicjalizacja pustą tablicą
  const [formData, setFormData] = useState({
    name: '', startTime: '', endTime: '', workerCount: 1, resources: '', totalCost: 0, crashingCostPerDay: 0
  });

  const fetchOperations = async () => {
    try {
      const res = await axios.get('/api/operations');
      // Sprawdzamy czy to co przyszło to na pewno tablica
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
    try {
      await axios.post('/api/operations', formData);
      alert("Dodano pomyślnie!");
      fetchOperations();
    } catch (err) {
      alert("Błąd zapisu! Sprawdź czy backend działa.");
    }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'Arial', color: 'white' }}>
      <h1>Harmonogram Operacji</h1>

      <form onSubmit={handleSubmit} style={{ marginBottom: '40px', display: 'grid', gap: '10px', maxWidth: '400px' }}>
        <input type="text" placeholder="Nazwa" onChange={e => setFormData({...formData, name: e.target.value})} />
        <input type="datetime-local" onChange={e => setFormData({...formData, startTime: e.target.value})} />
        <input type="datetime-local" onChange={e => setFormData({...formData, endTime: e.target.value})} />
        <select style={{color: 'black'}} onChange={e => setFormData({...formData, workerCount: parseInt(e.target.value)})}>
          <option value="1">1 Pracownik</option>
          <option value="5">5 Pracowników</option>
          <option value="10">10 Pracowników</option>
        </select>
        <input type="text" placeholder="Zasoby" onChange={e => setFormData({...formData, resources: e.target.value})} />
        <button type="submit" style={{ background: 'green', color: 'white', padding: '10px', cursor: 'pointer' }}>Zapisz</button>
      </form>

      <h2>Lista zadań</h2>
      <table border="1" style={{ width: '100%', borderCollapse: 'collapse', color: 'white', borderColor: '#444' }}>
        <thead>
          <tr style={{ background: '#333' }}>
            <th>Nazwa</th><th>Start</th><th>Koniec</th><th>Pracownicy</th><th>Zasoby</th>
          </tr>
        </thead>
        <tbody>
          {/* BEZPIECZNIK: Renderujemy tylko jeśli operations to tablica */}
          {Array.isArray(operations) && operations.map((op, index) => (
            <tr key={op.id || index}>
              <td>{op.name}</td>
              <td>{op.startTime ? new Date(op.startTime).toLocaleString() : '-'}</td>
              <td>{op.endTime ? new Date(op.endTime).toLocaleString() : '-'}</td>
              <td>{op.workerCount}</td>
              <td>{op.resources}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {operations.length === 0 && <p>Brak operacji w bazie danych.</p>}
    </div>
  );
}

export default App;