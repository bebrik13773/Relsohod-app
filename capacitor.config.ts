import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'ru.relsokhod.spb',
  appName: 'Рельсоход',
  webDir: 'dist',
  android: {
    allowMixedContent: false,
    backgroundColor: '#f5f7fa',
  },
};

export default config;
