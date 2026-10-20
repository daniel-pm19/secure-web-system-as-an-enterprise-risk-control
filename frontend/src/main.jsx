import React from 'react';
import { createRoot } from 'react-dom/client';
import { Auth0Provider } from '@auth0/auth0-react';
import App from './App.jsx';
import './style.css';
const env = import.meta.env;
const ready = [env.VITE_AUTH0_DOMAIN, env.VITE_AUTH0_CLIENT_ID, env.VITE_AUTH0_AUDIENCE, env.VITE_API_URL].every(value => value && !value.includes('YOUR'));
createRoot(document.getElementById('root')).render(ready ?
  <Auth0Provider domain={env.VITE_AUTH0_DOMAIN} clientId={env.VITE_AUTH0_CLIENT_ID}
    authorizationParams={{ redirect_uri: window.location.origin, audience: env.VITE_AUTH0_AUDIENCE, scope: 'openid profile email read:risk' }}>
    <App />
  </Auth0Provider> : <main><h1>Enterprise Risk Lab</h1><p>Configuración pendiente</p><p>Completa los valores de Auth0 y la URL de la API en .env.local y reinicia el frontend.</p></main>);
