import { useState, useEffect } from 'react'
import axios from 'axios'

function App() {
  const [operations, setOperations] = useState([]);
  const [formData, setFormData] = useState({
    name: '', startTime: '', endTime: '', workerCount: 1, resources: '', totalCost: 0, crashingCostPerDay: 0
  });

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
    try {
      await axios.post('/api/operations', formData);
      alert("Dodano pomyślnie!");
      fetchOperations(); // Odświeża tabelę po dodaniu
    } catch (err) {
      alert("Błąd zapisu! Sprawdź czy backend działa.");
    }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'Arial', color: 'white' }}>
      <h1>Harmonogram Operacji</h1>

      <form onSubmit={handleSubmit} style={{ marginBottom: '40px', display: 'grid', gap: '10px', maxWidth: '400px' }}>
        <input type="text" placeholder="Nazwa" required onChange={e => setFormData({...formData, name: e.target.value})} />
        <input type="datetime-local" required onChange={e => setFormData({...formData, startTime: e.target.value})} />
        <input type="datetime-local" required onChange={e => setFormData({...formData, endTime: e.target.value})} />

        {/* ZMIANA: Dowolna liczba pracowników */}
        <input
          type="number"
          placeholder="Liczba pracowników"
          min="1"
          required
          onChange={e => setFormData({...formData, workerCount: parseInt(e.target.value) || 1})}
        />

        <input type="text" placeholder="Zasoby (np. Tokarka)" onChange={e => setFormData({...formData, resources: e.target.value})} />

        {/* DODANE: Pola na koszty, które miałeś w state */}
        <input type="number" placeholder="Koszt całkowity (PLN)" onChange={e => setFormData({...formData, totalCost: parseFloat(e.target.value) || 0})} />
        <input type="number" placeholder="Koszt skracania/dzień (PLN)" onChange={e => setFormData({...formData, crashingCostPerDay: parseFloat(e.target.value) || 0})} />

        <button type="submit" style={{ background: 'green', color: 'white', padding: '10px', cursor: 'pointer' }}>Zapisz</button>
      </form>

      <h2>Lista zadań</h2>
      <table border="1" style={{ width: '100%', borderCollapse: 'collapse', color: 'white', borderColor: '#444' }}>
        <thead>
          <tr style={{ background: '#333' }}>
            <th>Nazwa</th><th>Start</th><th>Koniec</th><th>Pracownicy</th><th>Zasoby</th><th>Koszt</th>
          </tr>
        </thead>
        <tbody>
          {Array.isArray(operations) && operations.map((op, index) => (
            <tr key={op.id || index}>
              <td>{op.name}</td>
              <td>{op.startTime ? new Date(op.startTime).toLocaleString() : '-'}</td>
              <td>{op.endTime ? new Date(op.endTime).toLocaleString() : '-'}</td>
              <td>{op.workerCount}</td>
              <td>{op.resources}</td>
              <td>{op.totalCost ? op.totalCost + ' PLN' : '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {operations.length === 0 && <p>Brak operacji w bazie danych.</p>}
    </div>
  );
}

export default App;