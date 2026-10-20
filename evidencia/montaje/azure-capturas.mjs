/**
 * Capturas del portal de Azure para la defensa.
 *
 *   node montaje/azure-capturas.mjs
 *
 * Reutiliza la sesion que guardo `npm run sesion`: el portal comparte las
 * cookies de login.microsoftonline.com, asi que normalmente entra sin pedir
 * nada. Si pide MFA, el script lo detecta y lo dice en vez de quedarse colgado.
 *
 * Solo navega y toma fotos. No cambia ninguna configuracion.
 */
import { chromium } from '@playwright/test';
import { mkdirSync, existsSync as existsSyncTop } from 'node:fs';

const SALIDA = 'entregables/azure';
const PORTAL = 'https://portal.azure.com';

const APP_ID = 'd0120ca1-1d51-493b-9eaa-1121b5b7303a';

const PANTALLAS = [
  {
    id: '1-inquilino',
    titulo: 'Entra ID > Overview (el inquilino y su Tenant ID)',
    url: `${PORTAL}/#view/Microsoft_AAD_IAM/ActiveDirectoryMenuBlade/~/Overview`,
  },
  {
    id: '2-registro-autenticacion',
    titulo: 'Pedidos360 > Authentication (plataforma SPA y redirect URI)',
    url: `${PORTAL}/#view/Microsoft_AAD_RegisteredApps/ApplicationMenuBlade/~/Authentication/appId/${APP_ID}`,
  },
  {
    id: '3-aplicaciones-empresa',
    titulo: 'Enterprise applications (la aplicacion y su Application ID)',
    url: `${PORTAL}/#view/Microsoft_AAD_IAM/StartboardApplicationsMenuBlade/~/AppAppsPreview`,
  },
  {
    id: '4-usuarios',
    titulo: 'Entra ID > Users (los usuarios del directorio)',
    url: `${PORTAL}/#view/Microsoft_AAD_IAM/UsersManagementMenuBlade/~/AllUsers`,
  },
];


const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

const navegador = await chromium.launch({ headless: false });
const ctx = await navegador.newContext({
  storageState: existsSyncTop('.auth/azure.json') ? '.auth/azure.json' : '.auth/sesion.json',
  viewport: { width: 1600, height: 900 },
  ignoreHTTPSErrors: true,
});
const pagina = await ctx.newPage();
mkdirSync(SALIDA, { recursive: true });

// Primera entrada: aca se resuelve el inicio de sesion unico, o no se resuelve.
await pagina.goto(PANTALLAS[0].url, { waitUntil: 'domcontentloaded' });
await esperar(12000);

// El portal puede pedir usuario, contrasena y un codigo por mensaje. En vez de
// adivinar cuando termino —el portal es una aplicacion de pagina unica y no hay
// un evento fiable—, se espera a que la persona avise creando un archivo.
//
//   touch evidencia/.azure-listo
//
console.log('\n  Inicia sesion en la ventana que se abrio.');
console.log('  Cuando veas el portal cargado, en otra terminal:\n');
console.log('      touch /Users/gourarodriguez/Desktop/CloudNative/evidencia/.azure-listo\n');

const { existsSync, unlinkSync } = await import('node:fs');
const SENAL = '.azure-listo';
if (existsSync(SENAL)) unlinkSync(SENAL);

let listo = false;
for (let i = 0; i < 240; i++) {
  if (existsSync(SENAL)) { listo = true; unlinkSync(SENAL); break; }
  await esperar(5000);
}
if (!listo) {
  console.log('  No llego la senal. No se capturo nada.');
  await navegador.close();
  process.exit(1);
}
console.log('  capturando\n');

// Se guarda la sesion del portal aparte: la de la aplicacion no sirve aca, y
// repetir el codigo por mensaje en cada corrida es una perdida de tiempo.
await ctx.storageState({ path: '.auth/azure.json' });
console.log('  sesion del portal guardada en .auth/azure.json\n');

for (const p of PANTALLAS) {
  await pagina.goto(p.url, { waitUntil: 'domcontentloaded' });
  // El portal arma la vista despues de cargar; no hay un evento fiable al que
  // engancharse, asi que se le da tiempo y se comprueba que no quedo en blanco.
  await esperar(11000);
  const ruta = `${SALIDA}/${p.id}.png`;
  await pagina.screenshot({ path: ruta, fullPage: false });
  const texto = (await pagina.innerText('body').catch(() => '')).trim();
  console.log(`  ${p.id.padEnd(26)} ${texto.length > 400 ? 'con contenido' : 'POSIBLEMENTE VACIA'}  -> ${ruta}`);
}

// Los roles asignados cuelgan de un identificador interno que no conviene
// escribir a mano, asi que se llega por clic desde la lista de aplicaciones.
try {
  await pagina.goto(PANTALLAS[2].url, { waitUntil: 'domcontentloaded' });
  await esperar(11000);
  await pagina.getByRole('link', { name: 'Pedidos360', exact: true }).first().click({ timeout: 20000 });
  await esperar(9000);
  await pagina.getByRole('link', { name: /Users and groups/i }).first().click({ timeout: 20000 });
  await esperar(9000);
  await pagina.screenshot({ path: `${SALIDA}/5-roles-asignados.png` });
  console.log(`  5-roles-asignados          -> ${SALIDA}/5-roles-asignados.png`);
} catch (e) {
  console.log(`  5-roles-asignados          no se pudo: ${e.message.split('\n')[0]}`);
}

await navegador.close();
console.log(`\n  Capturas en evidencia/${SALIDA}\n`);
