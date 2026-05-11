import React from 'react';

export default function StatsPanel({ operations, ganttData, ganttLateData }) {
  if (operations.length === 0) return null;

  const baseCost = operations.reduce((s, op) => s + (op.totalCost || 0), 0);
  const appliedCrashCost = operations.reduce((s, op) => s + (op.crashingCostPerDay || 0) * (op.crashedDays || 0), 0);
  const maxCrashCost = operations.reduce((s, op) => s + (op.crashingCostPerDay || 0) * (op.maxCrashingDays || 0), 0);
  const finalCost = baseCost + appliedCrashCost;

  const earlyDays = ganttData ? Math.ceil(ganttData.totalDays) : null;
  const lateDays = ganttLateData ? Math.ceil(ganttLateData.totalDays) : null;
  const appliedCrashDays = operations.reduce((s, op) => s + (op.crashedDays || 0), 0);
  const maxCrashDays = operations.reduce((s, op) => s + (op.maxCrashingDays || 0), 0);

  const cell = { background: '#1e1e1e', borderRadius: '6px', padding: '10px 16px', border: '1px solid #333', minWidth: '140px' };
  const statLabel = { color: '#aaa', fontSize: '12px', display: 'block', marginBottom: '2px' };
  const sectionTitle = { color: '#888', fontSize: '11px', fontWeight: 'bold', textTransform: 'uppercase', letterSpacing: '1px', marginBottom: '8px' };

  const statVal = (color) => ({ color, fontWeight: 'bold', fontSize: '15px' });

  return (
    <div style={{ marginTop: '16px', background: '#2a2a2a', borderRadius: '8px', border: '1px solid #444', padding: '14px 20px' }}>
      <div style={{ display: 'flex', gap: '32px', flexWrap: 'wrap', alignItems: 'flex-start' }}>

        <div style={{ flexShrink: 0 }}>
          <div style={sectionTitle}>liczba operacji</div>
          <div style={{ ...cell, border: '1px solid #555' }}>
            <span style={statLabel}>Liczba operacji</span>
            <span style={statVal('#fff')}>{operations.length}</span>
          </div>
        </div>

        <div style={{ flex: 1, minWidth: '280px' }}>
          <div style={sectionTitle}>Koszty finansowe</div>
          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <div style={cell}>
              <span style={statLabel}>Koszt bazowy projektu</span>
              <span style={statVal('#4DC0E1')}>{baseCost.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN</span>
            </div>
            <div style={cell}>
              <span style={statLabel}>+ Koszt skracania</span>
              <span style={statVal('#ff6b6b')}>+ {appliedCrashCost.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN</span>
            </div>
            <div style={{ ...cell, border: '1px solid #4DC0E1' }}>
              <span style={statLabel}>= Koszt końcowy</span>
              <span style={statVal('#fff')}>{finalCost.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN</span>
            </div>
            <div style={cell}>
              <span style={statLabel}>Maks. koszt skracania</span>
              <span style={statVal('#ff9800')}>{maxCrashCost.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} PLN</span>
            </div>
          </div>
        </div>

        {earlyDays !== null && (
          <div style={{ flex: 1, minWidth: '280px' }}>
            <div style={sectionTitle}>Czas trwania projektu</div>
            <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
              <div style={cell}>
                <span style={statLabel}>Czas bazowy (early)</span>
                <span style={statVal('#4DC0E1')}>{earlyDays} dni</span>
              </div>
              {appliedCrashDays > 0 && (
                <div style={cell}>
                  <span style={statLabel}>− Skrócono łącznie</span>
                  <span style={statVal('#ff6b6b')}>− {appliedCrashDays} dni</span>
                </div>
              )}
              <div style={{ ...cell, border: '1px solid #4DC0E1' }}>
                <span style={statLabel}>= Czas końcowy (early)</span>
                <span style={statVal('#fff')}>{earlyDays - appliedCrashDays} dni</span>
              </div>
              {maxCrashDays > 0 && (
                <div style={cell}>
                  <span style={statLabel}>Maks. możliwe skrócenie</span>
                  <span style={statVal('#ff9800')}>− {maxCrashDays} dni</span>
                </div>
              )}
              {lateDays !== null && lateDays !== earlyDays && (
                <div style={cell}>
                  <span style={statLabel}>Czas (late)</span>
                  <span style={statVal('#a78bfa')}>{lateDays} dni</span>
                </div>
              )}
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
