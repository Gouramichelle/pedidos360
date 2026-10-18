import { chromium } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';

/**
 * Genera las tarjetas de transicion como PNG de 1280x720.
 *
 * Se renderizan con el navegador y no con ffmpeg porque este ffmpeg viene sin
 * freetype, asi que no tiene el filtro drawtext. De paso la tipografia queda
 * mucho mejor.
 */

const TARJETAS = [
  { archivo: 'portada', kicker: 'DSY1107 · Desarrollo Cloud Native',
    titulo: 'Pedidos360',
    bajada: 'Seis microservicios, un frontend y mensajeria desacoplada sobre AWS',
    pie: 'Evaluaciones parciales 3 y 4' },

  { archivo: 't1', kicker: 'Parte 1 de 7', titulo: 'La aplicacion desplegada',
    bajada: 'Login con OAuth 2.0 / OpenID Connect, catalogo real y creacion de un pedido',
    pie: 'Todo pasa por el API Gateway, que valida el token antes de enrutar' },

  { archivo: 't2', kicker: 'Parte 2 de 7', titulo: 'Los consumidores reaccionan',
    bajada: 'Auditoria y reportes se alimentan solos de los eventos de Kafka',
    pie: 'Nadie los llama: reaccionan al evento que publico ms-orders' },

  { archivo: 't3', kicker: 'Parte 3 de 7', titulo: 'El cluster de RabbitMQ',
    bajada: 'Dos nodos integrados en un cluster, levantados con docker-compose',
    pie: 'Los microservicios se conectan a los dos y hacen failover' },

  { archivo: 't4', kicker: 'Parte 4 de 7', titulo: 'Seis colas',
    bajada: 'Tres de comando — email, cocina y facturacion — y la cola muerta de cada una',
    pie: 'Cada cola de comando tiene su respaldo' },

  { archivo: 't5', kicker: 'Parte 5 de 7', titulo: 'Los dos tipos de exchange',
    bajada: 'direct enruta por clave exacta; topic enruta por patron con comodines',
    pie: 'Estan los dos porque resuelven casos distintos, no por completitud' },

  { archivo: 't6', kicker: 'Parte 6 de 7', titulo: 'Retencion y limpieza',
    bajada: 'TTL de los comandos, dead-lettering y tope de las colas muertas',
    pie: 'Configurado en application.yml, no escrito en el codigo' },

  { archivo: 't7', kicker: 'Parte 7 de 7', titulo: 'Y si se cae un consumidor',
    bajada: 'El broker vence el mensaje y lo enruta solo a la cola muerta',
    pie: 'El header x-death deja escrito el motivo: expired' },

  { archivo: 'cierre', kicker: 'Resumen', titulo: 'Lo que acabamos de ver',
    bajada: 'Identidad federada, API Gateway validando JWT, cluster de dos nodos, '
          + 'seis colas con sus DLQ, los dos tipos de exchange y un fallo real soportado',
    pie: 'github.com/Gouramichelle/pedidos360' },
];

const html = (t) => `<!doctype html><html><head><meta charset="utf-8"><style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { width:1280px; height:720px; display:flex; align-items:center;
         justify-content:center; background:#0f1115;
         font-family:-apple-system,"Segoe UI",Roboto,sans-serif; color:#e8eaed; }
  .caja { max-width:960px; padding:0 72px; }
  .kicker { font-size:19px; letter-spacing:.16em; text-transform:uppercase;
            color:#ff8c42; font-weight:600; margin-bottom:28px; }
  h1 { font-size:68px; line-height:1.08; font-weight:700; letter-spacing:-.02em;
       margin-bottom:30px; }
  .bajada { font-size:28px; line-height:1.5; color:#b6bcc6; font-weight:400; }
  .regla { width:84px; height:4px; background:#ff8c42; margin:42px 0 26px; border-radius:2px; }
  .pie { font-size:19px; color:#767d89; line-height:1.5; }
</style></head><body><div class="caja">
  <div class="kicker">${t.kicker}</div>
  <h1>${t.titulo}</h1>
  <div class="bajada">${t.bajada}</div>
  <div class="regla"></div>
  <div class="pie">${t.pie}</div>
</div></body></html>`;

mkdirSync('montaje/png', { recursive: true });
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1280, height: 720 } });
for (const t of TARJETAS) {
  const tmp = `montaje/png/${t.archivo}.html`;
  writeFileSync(tmp, html(t));
  await p.goto(`file://${process.cwd()}/${tmp}`);
  await p.waitForTimeout(250);
  await p.screenshot({ path: `montaje/png/${t.archivo}.png` });
  console.log('  ' + t.archivo + '.png');
}
await b.close();
