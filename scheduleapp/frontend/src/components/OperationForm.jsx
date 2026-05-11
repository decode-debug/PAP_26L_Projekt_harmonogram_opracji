import React from 'react';

export default function OperationForm({ formData, updateForm, operations, selectedPredecessors, setSelectedPredecessors, onSubmit }) {
  return (
    <form onSubmit={onSubmit} style={{ display: 'grid', gap: '8px' }}>
      <label>Nazwa operacji:</label>
      <input type="text" placeholder="np. Montaż" required value={formData.name} onChange={e => updateForm('name', e.target.value)} />

      <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', userSelect: 'none' }}>
        <input
          type="checkbox"
          checked={formData.asap}
          onChange={e => updateForm('asap', e.target.checked)}
          style={{ width: '16px', height: '16px', accentColor: '#28a745', cursor: 'pointer' }}
        />
        <span>Rozpocznij jak najwcześniej (ASAP)</span>
      </label>

      {!formData.asap && (
        <>
          <label>Czas rozpoczęcia:</label>
          <input type="datetime-local" required value={formData.startTime} onChange={e => updateForm('startTime', e.target.value)} />
          <label>Czas zakończenia:</label>
          <input type="datetime-local" required value={formData.endTime} onChange={e => updateForm('endTime', e.target.value)} />
        </>
      )}

      {formData.asap && (
        <>
          <label>Czas trwania operacji:</label>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <input
              type="number" min="0.5" step="0.5" placeholder="np. 8" required
              value={formData.asapDurationValue}
              onChange={e => updateForm('asapDurationValue', e.target.value)}
              style={{ flex: 1 }}
            />
            <select
              value={formData.asapDurationUnit}
              onChange={e => updateForm('asapDurationUnit', e.target.value)}
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
        onChange={e => updateForm('workerCount', parseInt(e.target.value) || 1)} />

      <label>Zasoby:</label>
      <input type="text" placeholder="np. Tokarka" value={formData.resources} onChange={e => updateForm('resources', e.target.value)} />

      <label>Koszt całkowity (PLN):</label>
      <input type="number" step="0.01" value={formData.totalCost}
        onChange={e => updateForm('totalCost', parseFloat(e.target.value) || 0)} />

      <label>Koszt skracania za 1 dzień (PLN):</label>
      <input type="number" step="0.01" value={formData.crashingCostPerDay}
        onChange={e => updateForm('crashingCostPerDay', parseFloat(e.target.value) || 0)} />

      <label>Maksymalne skrócenie (dni):</label>
      <input type="number" min="0" value={formData.maxCrashingDays}
        onChange={e => updateForm('maxCrashingDays', parseInt(e.target.value) || 0)} />

      {operations.length > 0 && (
        <>
          <label>Operacje poprzedzające:</label>
          <div style={{ border: '1px solid #555', borderRadius: '4px', padding: '8px', background: '#2a2a2a', maxHeight: '150px', overflowY: 'auto' }}>
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
  );
}
