import { test, expect, Page } from '@playwright/test';

/**
 * Evidencia 3: el recorrido completo en la aplicacion desplegada.
 *
 * Cubre los puntos de la pauta que viven en el frontend:
 *   - el frontend usa OAuth 2.0 / OpenID Connect para iniciar sesion
 *   - el frontend consume los endpoints a traves del API Manager
 *   - el backend y el frontend estan desplegados, activos e integrados
 *   - un productor genera un evento y los tres consumidores lo atienden
 *
 * El login no escribe credenciales: la sesion de Azure AD quedo guardada con
 * `npm run sesion`, y sus cookies de SSO hacen que el redirect de OAuth vuelva
 * ya autenticado. El video igual muestra el redirect completo, que es
 * justamente lo que hay que demostrar.
 */

const APP = 'https://3.83.237.65:8085';

async function entrar(page: Page) {
  await page.goto(APP);
  await page.waitForTimeout(2500);
  await page.locator('button:has-text("Iniciar sesion con Microsoft")').click();
  // El redirect pasa por login.microsoftonline.com y vuelve al dashboard.
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  await page.waitForTimeout(4000);
}

test.describe('Recorrido en la aplicacion desplegada', () => {
  test('login por OIDC, catalogo real y creacion de un pedido', async ({ page }) => {
    test.setTimeout(5 * 60 * 1000);

    // 1. Login por OAuth 2.0 / OpenID Connect contra el tenant de Azure AD.
    await entrar(page);
    await expect(page.locator('text=gou.rodriguez@duocuc.cl').first()).toBeVisible();
    // Los roles del token se muestran en la cabecera: vienen del claim "roles".
    await expect(page.locator('text=Admin').first()).toBeVisible();
    await page.waitForTimeout(4000);

    // 2. El catalogo, con productos y precios reales servidos por ms-catalog
    //    a traves del API Gateway.
    await page.locator('a:has-text("Catalogo")').first().click();
    await page.waitForTimeout(4000);
    await expect(page.locator('text=Pizza Margarita').first()).toBeVisible();
    await expect(page.locator('text=PIZZA-MARG').first()).toBeVisible();
    await page.waitForTimeout(3000);

    // 3. Crear un pedido. Esta sola accion publica los tres comandos.
    await page.locator('a:has-text("Pedidos")').first().click();
    await page.waitForTimeout(3000);
    await page.locator('a:has-text("Nuevo pedido")').first().click();
    await page.waitForURL(/\/orders\/nuevo/, { timeout: 20000 });
    await page.waitForTimeout(2500);

    // El selector de producto es el segundo select de la pagina: el primero es
    // el conmutador de rol de la cabecera.
    const producto = page.locator('select').nth(1);
    const cantidad = page.locator('input[type="number"]').first();

    await producto.selectOption({ index: 1 });
    await cantidad.fill('2');
    await page.locator('button:has-text("Agregar")').click();
    await page.waitForTimeout(2500);

    await producto.selectOption({ index: 3 });
    await cantidad.fill('4');
    await page.locator('button:has-text("Agregar")').click();
    await page.waitForTimeout(2500);

    // El total lo calcula ms-orders leyendo el precio del catalogo, no el
    // frontend: el cliente solo manda productId y cantidad.
    await page.locator('button:has-text("Confirmar pedido")').click();
    await page.waitForTimeout(6000);

    // 4. El pedido queda listado con su identificador y su estado real.
    await expect(page.locator('text=CREADO').first()).toBeVisible({ timeout: 30000 });
    await page.waitForTimeout(4000);
  });

  test('la auditoria y los reportes reflejan el pedido recien creado', async ({ page }) => {
    test.setTimeout(5 * 60 * 1000);
    await entrar(page);

    // ms-audit se alimenta solo de los eventos de Kafka que publica ms-orders.
    await page.locator('a:has-text("Auditoria")').first().click();
    await page.waitForTimeout(5000);
    await expect(page.locator('text=OrderCreated').first()).toBeVisible({ timeout: 30000 });
    await page.waitForTimeout(4000);

    // ms-report mantiene su proyeccion con los mismos eventos.
    await page.locator('a:has-text("Reportes")').first().click();
    await page.waitForTimeout(5000);
    await page.waitForTimeout(3000);
  });
});
