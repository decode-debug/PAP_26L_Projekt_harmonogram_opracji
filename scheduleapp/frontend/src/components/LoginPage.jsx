import React, { useState } from 'react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import AuthCard, { inputStyle, labelStyle } from './AuthCard';
import { rsaEncrypt } from '../utils/crypto';

export default function LoginPage({ onGoRegister }) {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (!window.crypto?.subtle) {
        setError('Przeglądarka nie obsługuje szyfrowania (wymagane HTTPS). Spróbuj przez https://.');
        setLoading(false);
        return;
      }

      // 1. Fetch the server's RSA public key
      const pkRes = await axios.get('/api/auth/public-key');
      const publicKeyBase64 = pkRes.data.publicKey;

      // 2. Encrypt the password with RSA-OAEP
      const encryptedPassword = await rsaEncrypt(password, publicKeyBase64);

      // 3. Send login request with encrypted password
      const res = await axios.post('/api/auth/login', { email, encryptedPassword });
      const { token, uuid, name } = res.data;
      login(token, { uuid, name, email: res.data.email });
    } catch (err) {
      if (err.response?.data) {
        const data = err.response.data;
        setError(typeof data === 'string' ? data : JSON.stringify(data));
      } else {
        setError('Błąd logowania: ' + (err.message || 'Sprawdź email i hasło.'));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthCard
      title="Logowanie"
      subtitle="Harmonogram Operacji"
      error={error}
      footer={
        <p style={{ textAlign: 'center', color: '#888', marginTop: '20px', fontSize: '13px' }}>
          Nie masz konta?{' '}
          <button
            onClick={onGoRegister}
            style={{ background: 'none', border: 'none', color: '#4FC3F7', cursor: 'pointer', padding: 0, fontSize: '13px', textDecoration: 'underline' }}
          >
            Zarejestruj się
          </button>
        </p>
      }
    >
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div>
          <label style={labelStyle}>Adres e-mail</label>
          <input
            type="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            placeholder="jan@example.com"
            style={inputStyle}
            autoComplete="username"
          />
        </div>
        <div>
          <label style={labelStyle}>Hasło</label>
          <input
            type="password"
            required
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="••••••••"
            style={inputStyle}
            autoComplete="current-password"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          style={{
            background: loading ? '#555' : '#28a745',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            padding: '12px',
            fontWeight: 'bold',
            fontSize: '15px',
            cursor: loading ? 'default' : 'pointer',
            marginTop: '4px'
          }}
        >
          {loading ? 'Logowanie...' : 'Zaloguj się'}
        </button>
      </form>
    </AuthCard>
  );
}
