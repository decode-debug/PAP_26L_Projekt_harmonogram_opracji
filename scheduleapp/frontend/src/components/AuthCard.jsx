export const inputStyle = {
  width: '100%',
  padding: '10px 12px',
  background: '#2a2a2a',
  border: '1px solid #555',
  borderRadius: '4px',
  color: 'white',
  fontSize: '14px',
  boxSizing: 'border-box'
};

export const labelStyle = { fontSize: '13px', color: '#aaa', marginBottom: '4px', display: 'block' };

export default function AuthCard({ title, subtitle, error, children, footer }) {
  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: '#1a1a1a',
      fontFamily: 'Arial, sans-serif'
    }}>
      <div style={{
        background: '#222',
        border: '1px solid #444',
        borderRadius: '8px',
        padding: '40px',
        width: '360px',
        boxShadow: '0 4px 24px rgba(0,0,0,0.5)'
      }}>
        <h2 style={{ color: 'white', marginTop: 0, marginBottom: '8px', textAlign: 'center' }}>
          {title}
        </h2>
        <p style={{ color: '#888', textAlign: 'center', marginTop: 0, marginBottom: '28px', fontSize: '13px' }}>
          {subtitle}
        </p>
        {error && (
          <div style={{
            color: '#ff6b6b',
            background: 'rgba(255,107,107,0.1)',
            border: '1px solid #ff6b6b',
            borderRadius: '4px',
            padding: '10px',
            marginBottom: '16px',
            fontSize: '13px'
          }}>
            {error}
          </div>
        )}
        {children}
        {footer}
      </div>
    </div>
  );
}
