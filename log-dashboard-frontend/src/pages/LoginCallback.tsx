import { useEffect } from 'react';

export default function LoginCallback() {
  useEffect(() => {
    const hash = window.location.hash;
    const searchString = hash.includes('?') ? hash.split('?')[1] : '';
    const params = new URLSearchParams(searchString);
    const token = params.get('token');

    if (token) {
      localStorage.setItem('token', token);
      console.log('TOKEN SAVED:', token);
      window.location.replace('/');
    } else {
      window.location.replace('/');
    }
  }, []);

  return (
    <div style={{
      display: 'flex',
      height: '100vh',
      alignItems: 'center',
      justifyContent: 'center',
      fontSize: '1.2rem',
      color: 'var(--text-color, #ffffff)',
      backgroundColor: 'var(--bg-color, #0f172a)'
    }}>
      Signing you in...
    </div>
  );
}
