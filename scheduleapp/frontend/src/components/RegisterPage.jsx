import React, { useState } from 'react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import AuthCard, { inputStyle, labelStyle } from './AuthCard';
import { rsaEncrypt } from '../utils/crypto';

export default function RegisterPage({ onGoLogin }) {
  const { login } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (password.length < 6) {
      setError('Hasło musi mieć co najmniej 6 znaków.');
      return;
    }
    if (password !== passwordConfirm) {
      setError('Hasła nie są identyczne.');
      return;
    }
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

      // 3. Send registration request
      const res = await axios.post('/api/auth/register', { name, email, encryptedPassword });
      const { token, uuid } = res.data;
      login(token, { uuid, name: res.data.name, email: res.data.email });
    } catch (err) {
      if (err.response?.data) {
        const data = err.response.data;
        setError(typeof data === 'string' ? data : JSON.stringify(data));
      } else {
        setError('Błąd rejestracji: ' + (err.message || 'Spróbuj ponownie.'));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthCard
      title="Rejestracja"
      subtitle="Stwórz nowe konto"
      error={error}
      footer={
        <p style={{ textAlign: 'center', color: '#888', marginTop: '20px', fontSize: '13px' }}>
          Masz już konto?{' '}
          <button
            onClick={onGoLogin}
            style={{ background: 'none', border: 'none', color: '#4FC3F7', cursor: 'pointer', padding: 0, fontSize: '13px', textDecoration: 'underline' }}
          >
            Zaloguj się
          </button>
        </p>
      }
    >
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div>
          <label style={labelStyle}>Imię i nazwisko</label>
          <input
            type="text"
            required
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="Jan Kowalski"
            style={inputStyle}
            autoComplete="name"
          />
        </div>
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
          <label style={labelStyle}>Hasło <span style={{ color: '#666' }}>(min. 6 znaków)</span></label>
          <input
            type="password"
            required
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="••••••••"
            style={inputStyle}
            autoComplete="new-password"
          />
        </div>
        <div>
          <label style={labelStyle}>Powtórz hasło</label>
          <input
            type="password"
            required
            value={passwordConfirm}
            onChange={e => setPasswordConfirm(e.target.value)}
            placeholder="••••••••"
            style={inputStyle}
            autoComplete="new-password"
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
          {loading ? 'Rejestrowanie...' : 'Zarejestruj się'}
        </button>
      </form>
    </AuthCard>
  );
}
