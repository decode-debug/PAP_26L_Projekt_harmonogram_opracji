import React from 'react';

export default function CrashingPanel({ operations, crashingDays, setCrashingDays, onApply }) {
  const btnStyle = { padding: '8px 16px', cursor: 'pointer', border: 'none', fontWeight: 'bold', borderRadius: '4px' };

  const handleReset = () => {
    const reset = {};
    operations.forEach(op => { reset[op.id] = 0; });
    setCrashingDays(reset);
  };

  return (
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
                      type="range" min={0} max={op.maxCrashingDays} value={days}
                      onChange={e => setCrashingDays({ ...crashingDays, [op.id]: parseInt(e.target.value) })}
                      style={{ width: '120px', accentColor: '#ff6b6b' }}
                    />
                    <input
                      type="number" min={0} max={op.maxCrashingDays} value={days}
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
        <button onClick={onApply} style={{ ...btnStyle, background: '#e53935', color: 'white', fontSize: '14px' }}>
          Zastosuj skracanie
        </button>
        <button onClick={handleReset} style={{ ...btnStyle, background: '#555', color: 'white', fontSize: '14px' }}>
          Resetuj
        </button>
        <span style={{ color: '#aaa', fontSize: '12px' }}>
          Przesuwaj suwaki i kliknij „Zastosuj skracanie", by zaktualizować wykresy.
        </span>
      </div>
    </div>
  );
}
