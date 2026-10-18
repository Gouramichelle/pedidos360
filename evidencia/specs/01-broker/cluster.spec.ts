import { test, expect, Page } from '@playwright/test';

/**
 * Evidencia 1: el cluster de RabbitMQ, sus colas y sus exchanges.
 *
 * Cubre cuatro puntos de la pauta de EP4:
 *   - dos nodos RabbitMQ configurados e integrados en un cluster
 *   - tres colas de mensajes con sus respectivas DLQ
 *   - exchanges de tipo direct y topic correctamente configurados
 *   - las politicas de retencion y limpieza
 *
 * Requiere el tunel SSH abierto contra ec2-broker:
 *   ssh -i labsuser.pem -L 15672:localhost:15672 ec2-user@<ip-broker>
 */

const CONSOLA = 'http://localhost:15672';

/**
 * La consola de RabbitMQ usa un formulario, no autenticacion basica, asi que
 * httpCredentials no alcanza. guest/guest son las credenciales por defecto del
 * broker de desarrollo, no credenciales personales.
 */
async function entrar(page: Page) {
  await page.goto(CONSOLA);
  const usuario = page.locator('input[name="username"]');
  if (await usuario.isVisible({ timeout: 5000 }).catch(() => false)) {
    await usuario.fill('guest');
    await page.locator('input[name="password"]').fill('guest');
    await page.locator('input[type="submit"]').click();
  }
  // .first(): el selector matchea dos elementos y el modo estricto de
  // Playwright rechaza un locator ambiguo.
  await expect(page.locator('#menu').first()).toBeVisible({ timeout: 20000 });
}

/** La consola es una SPA con rutas por hash. */
async function ir(page: Page, hash: string) {
  await page.goto(`${CONSOLA}/#${hash}`);
  await page.waitForTimeout(2500);
}

test.describe('Cluster de RabbitMQ', () => {
  test.beforeEach(async ({ page }) => {
    await entrar(page);
  });

  test('los dos nodos estan integrados en un cluster', async ({ page }) => {
    await ir(page, '/');
    // El Overview lista los nodos del cluster al pie de la pagina.
    await page.locator('text=Nodes').first().scrollIntoViewIfNeeded();
    await page.waitForTimeout(3000);

    await expect(page.locator('text=rabbit@rabbit1').first()).toBeVisible();
    await expect(page.locator('text=rabbit@rabbit2').first()).toBeVisible();
  });

  test('existen las seis colas: tres de comando y sus tres DLQ', async ({ page }) => {
    await ir(page, '/queues');

    for (const cola of [
      'q.cmd.email', 'q.cmd.email.dlq',
      'q.cmd.kitchen', 'q.cmd.kitchen.dlq',
      'q.cmd.invoice', 'q.cmd.invoice.dlq',
    ]) {
      await expect(page.locator(`text=${cola}`).first()).toBeVisible();
    }
    await page.waitForTimeout(3000);
  });

  test('hay un exchange direct y uno topic, mas el de dead-lettering', async ({ page }) => {
    await ir(page, '/exchanges');

    await expect(page.locator('text=cmd.direct').first()).toBeVisible();
    await expect(page.locator('text=cmd.topic').first()).toBeVisible();
    await expect(page.locator('text=cmd.dead.dlx').first()).toBeVisible();
    await page.waitForTimeout(3000);
  });

  test('las colas llevan politica de retencion y dead-lettering', async ({ page }) => {
    await ir(page, '/queues/%2F/q.cmd.kitchen');
    await page.waitForTimeout(2000);

    // x-message-ttl es lo que hace que un comando sin consumir termine en la
    // DLQ; sin el, con el consumidor caido el mensaje se quedaria encolado.
    await expect(page.locator('text=x-message-ttl').first()).toBeVisible();
    await expect(page.locator('text=x-dead-letter-exchange').first()).toBeVisible();
    await page.waitForTimeout(3000);
  });
});
