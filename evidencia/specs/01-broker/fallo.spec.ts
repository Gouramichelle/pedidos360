import { test, expect, Page } from '@playwright/test';
import { CONSOLA, mensajesEn, vaciar, Marcador,
         detenerConsumidor, levantarConsumidor } from '../entorno';

/**
 * Evidencia 2: que pasa cuando se cae un consumidor.
 *
 * Es el escenario que pidio el docente: "si bajas un consumidor, el mensaje se
 * queda en la cola. Ese tiempo que pasa en la cola es lo que tu configuras. Si
 * despues de un tiempo se da cuenta de que no existe, ahi lo pasa a la cola
 * muerta."
 *
 * El recorrido es: baja el consumidor, se publica un comando perfectamente
 * valido, se ve encolado sin nadie que lo atienda, vence el TTL de 30 segundos
 * y el propio broker lo enruta a la DLQ. Al abrirlo, el header x-death dice
 * "expired", que lo distingue de un mensaje rechazado por venir mal formado.
 */

const COLA = 'q.cmd.kitchen';
const DLQ = 'q.cmd.kitchen.dlq';

async function entrar(page: Page) {
  await page.goto(CONSOLA);
  const usuario = page.locator('input[name="username"]');
  if (await usuario.isVisible({ timeout: 5000 }).catch(() => false)) {
    await usuario.fill('guest');
    await page.locator('input[name="password"]').fill('guest');
    await page.locator('input[type="submit"]').click();
  }
  await expect(page.locator('#menu').first()).toBeVisible({ timeout: 20000 });
}

async function ir(page: Page, hash: string) {
  await page.goto(`${CONSOLA}/#${hash}`);
  await page.waitForTimeout(2500);
}

test.describe('Caida de un consumidor', () => {
  test.afterAll(() => {
    // Dejar el sistema como estaba, pase lo que pase con la prueba.
    try {
      levantarConsumidor();
    } catch { /* el reporte de la prueba ya dice que fallo */ }
  });

  test('un comando sin consumidor vence y el broker lo manda a la cola muerta', async ({ page }) => {
    test.setTimeout(4 * 60 * 1000);

    // Partir de una DLQ vacia: si quedara un mensaje de una corrida anterior,
    // la asercion pasaria sin demostrar nada.
    vaciar(DLQ);
    const marcas = new Marcador();
    await entrar(page);
    marcas.marcar('partida');

    // 1. Punto de partida: la cola tiene su consumidor enganchado.
    await ir(page, `/queues/%2F/${COLA}`);
    await expect(page.locator('text=Consumers').first()).toBeVisible();
    await page.waitForTimeout(3000);

    // 2. Se cae el consumidor.
    marcas.marcar('caida');
    detenerConsumidor();
    await page.waitForTimeout(8000);
    await page.reload();
    await page.waitForTimeout(3000);

    // 3. Se publica un comando valido desde la consola, por el exchange direct.
    marcas.marcar('publicar');
    await ir(page, '/exchanges/%2F/cmd.direct');
    const publicar = page.locator('text=Publish message').first();
    await publicar.scrollIntoViewIfNeeded();
    await publicar.click();
    await page.waitForTimeout(1500);

    // La pagina tiene dos campos routing_key, uno para crear bindings y otro
    // para publicar. Se acota al formulario de publicacion.
    const formulario = page.locator('form[action="#/exchanges/publish"]');
    await formulario.locator('input[name="routing_key"]').fill('kitchen.ticket');
    await formulario.locator('textarea[name="payload"]').fill(JSON.stringify({
      type: 'KitchenTicketRequested',
      eventId: `k-sin-consumidor-${Date.now()}`,
      correlationId: 'demo-fallo',
      payload: { orderId: 77, customerId: 'cliente-77', cantidadItems: 4 },
    }));
    await formulario.locator('input[type="submit"], button[type="submit"]').first().click();
    await page.waitForTimeout(3000);

    // 4. El mensaje queda encolado y nadie lo atiende.
    marcas.marcar('espera');
    await ir(page, `/queues/%2F/${COLA}`);
    expect(mensajesEn(COLA)).toBeGreaterThan(0);
    await page.waitForTimeout(4000);

    // 5. Vence el TTL de 30 segundos y el broker lo enruta solo.
    //    No se recarga la pagina: la consola se refresca sola cada 5 segundos,
    //    y recargar una SPA con rutas por hash en un bucle aborta la
    //    navegacion. Se sondea la API aparte y se deja la pantalla quieta,
    //    que ademas es lo que hace legible el video.
    for (let i = 0; i < 24 && mensajesEn(DLQ) === 0; i++) {
      await page.waitForTimeout(2500);
    }
    expect(mensajesEn(DLQ)).toBeGreaterThan(0);
    expect(mensajesEn(COLA)).toBe(0);

    // 6. El mensaje esta en la cola muerta.
    marcas.marcar('vencido');
    await ir(page, `/queues/%2F/${DLQ}`);
    await page.waitForTimeout(3000);

    // 7. Y el broker dejo escrito el motivo.
    marcas.marcar('motivo');
    const obtener = page.locator('h2:has-text("Get messages")').first();
    await obtener.scrollIntoViewIfNeeded();
    await obtener.click();
    await page.waitForTimeout(1500);
    const formGet = page.locator('form[action="#/queues/get"]');
    await formGet.locator('input[type="submit"], button[type="submit"]').first().click();
    await page.waitForTimeout(4000);

    await expect(page.locator('text=expired').first()).toBeVisible({ timeout: 20000 });
    await page.waitForTimeout(8000);
    marcas.marcar('fin');
    marcas.guardar('fallo');
  });
});
