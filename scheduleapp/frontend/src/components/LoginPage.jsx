import React, { useState } from 'react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';

/**
 * Encrypts plaintext with an RSA public key (OAEP-SHA256) using the Web Crypto API.
 * Returns a Base64-encoded ciphertext string.
 */
async function rsaEncrypt(plaintext, publicKeyBase64) {
  const binaryDer = Uint8Array.from(atob(publicKeyBase64), c => c.charCodeAt(0));
  const publicKey = await window.crypto.subtle.importKey(
    'spki',
    binaryDer.buffer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt']
  );
  const encoded = new TextEncoder().encode(plaintext);
  const encrypted = await window.crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, encoded);
  return btoa(String.fromCharCode(...new Uint8Array(encrypted)));
}

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
      setError(err.response?.data || 'Błąd logowania. Sprawdź email i hasło.');
    } finally {
      setLoading(false);
    }
  };

  const inputStyle = {
    width: '100%',
    padding: '10px 12px',
    background: '#2a2a2a',
    border: '1px solid #555',
    borderRadius: '4px',
    color: 'white',
    fontSize: '14px',
    boxSizing: 'border-box'
  };

  const labelStyle = { fontSize: '13px', color: '#aaa', marginBottom: '4px', display: 'block' };

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
          Logowanie
        </h2>
        <p style={{ color: '#888', textAlign: 'center', marginTop: 0, marginBottom: '28px', fontSize: '13px' }}>
          Harmonogram Operacji
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

        <p style={{ textAlign: 'center', color: '#888', marginTop: '20px', fontSize: '13px' }}>
          Nie masz konta?{' '}
          <button
            onClick={onGoRegister}
            style={{ background: 'none', border: 'none', color: '#4FC3F7', cursor: 'pointer', padding: 0, fontSize: '13px', textDecoration: 'underline' }}
          >
            Zarejestruj się
          </button>
        </p>
      </div>
    </div>
  );
}
