import { test, expect, Page, BrowserContext } from '@playwright/test';
import { enApps } from '../entorno';

/**
 * Evidencia 4: la app y la consola del broker, en paralelo.
 *
 * Demuestra que la conexion es real: una accion en la aplicacion produce
 * mensajes que se ven aparecer en las colas, y despues desaparecer cuando el
 * consumidor los procesa.
 *
 * Por que se pausa el consumidor: con los tres consumidores andando, los
 * mensajes se procesan en milisegundos. La consola muestrea cada cinco
 * segundos, asi que para cuando refresca ya no queda nada y los contadores
 * nunca se mueven. Pausando ms-notify los mensajes se acumulan y quedan a la
 * vista; al reanudarlo se ve como se drenan. Es el mismo trafico, solo que
 * visible.
 *
 * Se usa `docker compose pause` y no `stop`: pause suspende el proceso sin
 * cerrar la conexion AMQP, asi que al reanudar consume de inmediato. Un stop
 * obligaria a reiniciar el servicio, que tarda mas que el TTL de 30 segundos
 * y los mensajes venceran antes de poder mostrar como se procesan.
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

test.describe('La app y el broker al mismo tiempo', () => {
  test.afterAll(() => {
    try { enApps('cd ~/infra/apps && sudo docker compose unpause ms-notify'); } catch { /* ya estaba */ }
  });

  test('crear un pedido llena las tres colas y el consumidor las vacia', async ({ browser }) => {
    test.setTimeout(6 * 60 * 1000);

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

    // La consola se queda en la lista de colas, que es donde se ve el contador
    // de cada una. Arranca en cero.
    await consola.goto(`${CONSOLA}/#/queues`);
    await consola.waitForTimeout(3000);

    // La app inicia sesion y deja el pedido armado, sin confirmar todavia.
    await app.goto(APP);
    await app.waitForTimeout(2000);
    await app.locator('button:has-text("Iniciar sesion con Microsoft")').click();
    await app.waitForURL(/\/dashboard/, { timeout: 60000 });
    await app.goto(`${APP}/orders/nuevo`);
    await app.waitForTimeout(2500);

    const producto = app.locator('select').nth(1);
    const cantidad = app.locator('input[type="number"]').first();
    await producto.selectOption({ index: 1 });
    await cantidad.fill('3');
    await app.locator('button:has-text("Agregar")').click();
    await app.waitForTimeout(1500);
    await producto.selectOption({ index: 2 });
    await cantidad.fill('2');
    await app.locator('button:has-text("Agregar")').click();
    await app.waitForTimeout(2500);

    // Se pausa el consumidor justo antes de confirmar, para que los mensajes
    // se queden a la vista en vez de procesarse al instante.
    enApps('cd ~/infra/apps && sudo docker compose pause ms-notify');
    await consola.waitForTimeout(6000);

    // El momento: una sola accion publica tres comandos.
    await app.locator('button:has-text("Confirmar pedido")').click();
    await app.waitForTimeout(2000);

    // Las tres colas de comando pasan a tener su mensaje. Esto si se ve.
    await consola.waitForTimeout(14000);

    // Y al reanudar el consumidor, se vacian.
    enApps('cd ~/infra/apps && sudo docker compose unpause ms-notify');
    await consola.waitForTimeout(16000);

    await expect(app.locator('text=CREADO').first()).toBeVisible({ timeout: 30000 });
    await app.waitForTimeout(3000);

    await ctxConsola.close();
    await ctxApp.close();
  });
});
