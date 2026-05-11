import React from 'react';

export default function OperationTable({ operations, onDelete }) {
  return (
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
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', fontSize: '12px' }}>
                {op.asap ? <span style={{ color: '#4DC0E1', fontWeight: 'bold' }}>ASAP</span> : (op.startTime ? new Date(op.startTime).toLocaleString() : '-')}
              </td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', fontSize: '12px' }}>
                {op.asap ? <span style={{ color: '#aaa' }}>—</span> : (op.endTime ? new Date(op.endTime).toLocaleString() : '-')}
              </td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap' }}>
                {op.asap
                  ? (op.asapDurationHours != null
                      ? (op.asapDurationHours % 24 === 0
                          ? (op.asapDurationHours / 24).toFixed(1) + ' d'
                          : parseFloat(op.asapDurationHours.toFixed(1)) + ' h')
                      : '-')
                  : (op.durationInDays ? op.durationInDays + ' d' : '-')}
              </td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap' }}>{op.workerCount}</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap' }}>{op.resources || '-'}</td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', fontSize: '12px' }}>
                {op.totalCost != null ? op.totalCost.toLocaleString('pl-PL', { maximumFractionDigits: 1 }) + ' PLN' : '0 PLN'}
              </td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', fontSize: '12px', color: '#ff6b6b' }}>
                {op.crashingCostPerDay != null ? op.crashingCostPerDay.toLocaleString('pl-PL', { maximumFractionDigits: 1 }) + ' PLN' : '0 PLN'}
              </td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap', color: '#4da3ff' }}>
                {op.maxCrashingDays != null ? op.maxCrashingDays + ' d' : '0 d'}
              </td>
              <td style={{ padding: '6px 8px', color: '#ff9800', fontSize: '12px', whiteSpace: 'nowrap' }}>
                {op.predecessorIds ? op.predecessorIds.split(',').map(uid => {
                  const pred = operations.find(o => o.uuid === uid.trim());
                  return pred ? pred.name : uid.trim().substring(0, 8) + '...';
                }).join(', ') : '-'}
              </td>
              <td style={{ padding: '6px 8px', whiteSpace: 'nowrap' }}>
                <button onClick={() => onDelete(op.id)}
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
  );
}
