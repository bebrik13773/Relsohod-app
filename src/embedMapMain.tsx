import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { MotionConfig } from 'motion/react';
import { EmbedMapView } from './components/EmbedMapView';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MotionConfig reducedMotion="user">
      <EmbedMapView />
    </MotionConfig>
  </StrictMode>
);
