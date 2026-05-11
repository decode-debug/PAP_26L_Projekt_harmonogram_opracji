import React, { useState, useEffect } from 'react'
import { useAuth } from './context/AuthContext.jsx'
import LoginPage from './components/LoginPage.jsx'
import RegisterPage from './components/RegisterPage.jsx'
import MainApp from './MainApp.jsx'

function App() {
  const { isLoggedIn, user, logout } = useAuth();
  const [authView, setAuthView] = useState('login');

  useEffect(() => {
    const handler = () => logout();
    window.addEventListener('auth:logout', handler);
    return () => window.removeEventListener('auth:logout', handler);
  }, [logout]);

  if (!isLoggedIn) {
    if (authView === 'register') {
      return <RegisterPage onGoLogin={() => setAuthView('login')} />;
    }
    return <LoginPage onGoRegister={() => setAuthView('register')} />;
  }

  return <MainApp user={user} onLogout={logout} />;
}

export default App;