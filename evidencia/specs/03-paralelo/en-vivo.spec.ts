import { test, expect, Page, BrowserContext } from '@playwright/test';

/**
 * Evidencia 4: la app y la consola del broker, en paralelo.
 *
 * Demuestra que la conexion es real: se crea un pedido en la aplicacion y, al
 * mismo tiempo, la consola de RabbitMQ registra el trafico que esa accion
 * genero. No es una captura preparada; son dos navegadores corriendo a la vez
 * contra el mismo despliegue.
 *
 * Playwright graba un video por pagina. Las dos se abren en el mismo instante,
 * asi que los dos videos quedan alineados y despues se componen lado a lado
 * con ffmpeg (ver montaje/montar-paralelo.sh).
 *
 * Requiere el tunel SSH al broker y la sesion de Azure AD guardada.
 */

const APP = 'https://3.83.237.65:8085';
const CONSOLA = 'http://localhost:15672';

async function abrirConsola(ctx: BrowserContext): Promise<Page> {
  const page = await ctx.newPage();
  await page.goto(CONSOLA);
  const usuario = page.locator('input[name="username"]');
  if (await usuario.isVisible({ timeout: 5000 }).catch(() => false)) {
    await usuario.fill('guest');
    await page.locator('input[name="password"]').fill('guest');
    await page.locator('input[type="submit"]').click();
  }
  await expect(page.locator('#menu').first()).toBeVisible({ timeout: 20000 });
  return page;
}

async function entrarAlaApp(page: Page) {
  await page.goto(APP);
  await page.waitForTimeout(2000);
  await page.locator('button:has-text("Iniciar sesion con Microsoft")').click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
}

test.describe('La app y el broker al mismo tiempo', () => {
  test('crear un pedido se refleja en vivo en la consola del broker', async ({ browser }) => {
    test.setTimeout(5 * 60 * 1000);

    // Dos contextos con grabacion propia. Se abren juntos para que los dos
    // videos arranquen en el mismo momento y la composicion quede sincronizada.
    const ctxConsola = await browser.newContext({
      viewport: { width: 1280, height: 720 },
      recordVideo: { dir: 'videos/paralelo-consola', size: { width: 1280, height: 720 } },
      httpCredentials: { username: 'guest', password: 'guest' },
    });
    const ctxApp = await browser.newContext({
      viewport: { width: 1280, height: 720 },
      recordVideo: { dir: 'videos/paralelo-app', size: { width: 1280, height: 720 } },
      storageState: '.auth/sesion.json',
      ignoreHTTPSErrors: true,
    });

    const consola = await abrirConsola(ctxConsola);
    const app = await ctxApp.newPage();

    // La consola se queda en Overview, donde el grafico de Message rates
    // conserva una ventana movil de un minuto: el pico sigue visible despues.
    await consola.goto(`${CONSOLA}/#/`);
    await consola.waitForTimeout(3000);

    // Mientras, la aplicacion inicia sesion.
    await entrarAlaApp(app);
    await app.waitForTimeout(2000);

    // Se arma el pedido. La consola sigue en cero: todavia no paso nada.
    await app.goto(`${APP}/orders/nuevo`);
    await app.waitForTimeout(2500);
    const producto = app.locator('select').nth(1);
    const cantidad = app.locator('input[type="number"]').first();

    await producto.selectOption({ index: 1 });
    await cantidad.fill('3');
    await app.locator('button:has-text("Agregar")').click();
    await app.waitForTimeout(2000);

    await producto.selectOption({ index: 2 });
    await cantidad.fill('2');
    await app.locator('button:has-text("Agregar")').click();
    await app.waitForTimeout(2500);

    // El momento: una sola accion publica un evento y tres comandos.
    await app.locator('button:has-text("Confirmar pedido")').click();
    await app.waitForTimeout(3000);

    // La consola registra el trafico. Se deja a la vista mientras la app
    // muestra el pedido ya creado.
    await consola.waitForTimeout(12000);

    // Y se baja al detalle: las tres colas de comando recibieron su mensaje.
    await consola.goto(`${CONSOLA}/#/queues`);
    await consola.waitForTimeout(10000);

    await expect(app.locator('text=CREADO').first()).toBeVisible({ timeout: 30000 });
    await app.waitForTimeout(5000);

    await ctxConsola.close();
    await ctxApp.close();
  });
});
