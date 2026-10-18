import { defineConfig } from '@playwright/test';

/**
 * Configuracion de grabacion de la evidencia.
 *
 * Cada spec produce un video propio en evidencia/videos/, pensado para
 * reproducirlo delante del docente mientras se narra el guion que esta en
 * GUION.md.
 *
 * Los tiempos son deliberadamente lentos: un video que avanza a la velocidad
 * de la maquina no se puede narrar. slowMo da el respiro necesario entre
 * acciones para que se vea que ocurrio cada una.
 */
export default defineConfig({
  testDir: './specs',
  // Secuencial a proposito: los escenarios comparten el estado del broker, y
  // uno que baje un consumidor mientras otro publica haria ruido en el video.
  workers: 1,
  fullyParallel: false,
  timeout: 5 * 60 * 1000,
  reporter: [['list'], ['html', { outputFolder: 'reporte', open: 'never' }]],
  outputDir: './videos',

  use: {
    video: { mode: 'on', size: { width: 1280, height: 720 } },
    viewport: { width: 1280, height: 720 },
    screenshot: 'only-on-failure',
    actionTimeout: 30 * 1000,
    // El frontend se sirve con un certificado autofirmado, requisito de Azure
    // AD para el redirect URI de una SPA.
    ignoreHTTPSErrors: true,
    launchOptions: { slowMo: 600 },
  },

  projects: [
    {
      name: 'broker',
      testMatch: /01-broker\/.*\.spec\.ts/,
      // La consola de RabbitMQ usa autenticacion basica. Se pasa por contexto
      // en vez de rellenar el formulario: son las credenciales por defecto del
      // broker de desarrollo, no credenciales personales.
      use: { httpCredentials: { username: 'guest', password: 'guest' } },
    },
    {
      name: 'app',
      testMatch: /02-app\/.*\.spec\.ts/,
      // Sesion de Azure AD guardada por `npm run sesion`. El login lo hace la
      // persona una vez; aqui solo se reutiliza.
      use: { storageState: '.auth/sesion.json' },
    },
  ],
});
