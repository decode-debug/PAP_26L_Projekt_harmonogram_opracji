import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import axios from 'axios'
import './index.css'
import App from './App.jsx'
import { AuthProvider } from './context/AuthContext.jsx'

// Global axios defaults — backend is served on port 8080 during development
axios.defaults.baseURL = 'http://localhost:8080'

// Attach JWT token to every outgoing request
axios.interceptors.request.use(config => {
  const token = sessionStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Pick up refreshed sliding-window token from every response
axios.interceptors.response.use(
  response => {
    const newToken = response.headers['x-auth-token']
    if (newToken) {
      sessionStorage.setItem('token', newToken)
    }
    return response
  },
  error => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('user')
      // Force re-render — App will show the login page
      window.dispatchEvent(new Event('auth:logout'))
    }
    return Promise.reject(error)
  }
)

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </StrictMode>,
)

