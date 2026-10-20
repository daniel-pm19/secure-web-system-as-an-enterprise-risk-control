import React, { useState } from 'react';
import { useAuth0 } from '@auth0/auth0-react';
export default function App() {
  const { loginWithRedirect, logout, isAuthenticated, isLoading, user, error, getAccessTokenSilently } = useAuth0();
  const [result, setResult] = useState('');
  const [busy, setBusy] = useState(false);
  async function callApi() {
    setBusy(true); setResult('');
    try {
      const token = await getAccessTokenSilently();
      const response = await fetch(`${import.meta.env.VITE_API_URL.replace(/\/$/, '')}/api/risk-status`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error(`API: HTTP ${response.status}${response.status === 403 ? ' — falta el permiso read:risk' : ''}`);
      setResult(`HTTP ${response.status}\n${JSON.stringify(await response.json(), null, 2)}`);
    } catch (err) { setResult(`Solicitud fallida: ${err.message}`); }
    finally { setBusy(false); }
  }
  if (isLoading) return <main><p>Cargando sesión…</p></main>;
  return <main><span className="label">SEGURIDAD EMPRESARIAL</span><h1>Enterprise Risk Lab</h1>
    <p>Identidad verificada y acceso protegido a la API.</p>
    {error && <p role="alert">{error.message}</p>}
    {isAuthenticated ? <><p>Sesión de <strong>{user?.name}</strong></p><div className="actions">
      <button disabled={busy} onClick={callApi}>{busy ? 'Consultando…' : 'Consultar API protegida'}</button>
      <button className="secondary" onClick={() => logout({ logoutParams: { returnTo: window.location.origin } })}>Cerrar sesión</button></div></>
      : <button onClick={() => loginWithRedirect().catch(err => setResult(err.message))}>Iniciar sesión</button>}
    <pre aria-live="polite">{result || 'Sin consultas realizadas.'}</pre></main>;
}
