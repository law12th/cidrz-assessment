import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { BenchPage } from './bench/BenchPage';
import './styles.css';

const root = document.getElementById('root');
if (!root) throw new Error('Missing #root element');

// /?bench opens the 10,000-row measurement page described in the README.
const showBench = new URLSearchParams(window.location.search).has('bench');

createRoot(root).render(<StrictMode>{showBench ? <BenchPage /> : <App />}</StrictMode>);
