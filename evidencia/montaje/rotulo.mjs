import { chromium } from '@playwright/test';
import { writeFileSync } from 'node:fs';

/**
 * Rotulo transparente que se superpone al video en paralelo. Usa las bandas
 * que quedan arriba y abajo al poner dos capturas de 1280x720 lado a lado
 * dentro de un cuadro de 1280x720.
 */
const html = `<!doctype html><html><head><meta charset="utf-8"><style>
 *{margin:0;padding:0;box-sizing:border-box}
 body{width:1280px;height:720px;background:transparent;position:relative;
      font-family:-apple-system,"Segoe UI",Roboto,sans-serif}
 .fila{position:absolute;top:0;left:0;width:1280px;height:180px;display:flex;
       align-items:center;color:#e8eaed}
 .mitad{width:640px;text-align:center;padding:0 24px}
 .et{font-size:15px;letter-spacing:.18em;text-transform:uppercase;
     color:#ff8c42;font-weight:700;margin-bottom:10px}
 .tx{font-size:26px;font-weight:600;line-height:1.25}
 .pie{position:absolute;bottom:0;left:0;width:1280px;height:180px;display:flex;
      align-items:center;justify-content:center;padding:0 90px}
 /* El texto va dentro de un unico hijo: si los <b> fueran hijos directos del
    contenedor flex, cada uno seria un item y la frase se partiria. */
 .pie p{color:#8b929e;font-size:21px;text-align:center;line-height:1.5}
 .pie b{color:#e8eaed;font-weight:600}
</style></head><body>
 <div class="fila">
   <div class="mitad"><div class="et">Lo que hace el usuario</div>
     <div class="tx">La aplicacion</div></div>
   <div class="mitad"><div class="et">Lo que ocurre por dentro</div>
     <div class="tx">Consola de RabbitMQ</div></div>
 </div>
 <div class="pie"><p>El consumidor esta <b>pausado a proposito</b>: con los tres andando el mensaje se procesa en milisegundos y el contador nunca se mueve. Pausado, se ve llegar; al reanudarlo, se ve drenar.</p></div>
</body></html>`;

const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1280, height: 720 } });
writeFileSync('montaje/png/rotulo.html', html);
await p.goto(`file://${process.cwd()}/montaje/png/rotulo.html`);
await p.waitForTimeout(250);
await p.screenshot({ path: 'montaje/png/rotulo.png', omitBackground: true });
await b.close();
console.log('  montaje/png/rotulo.png');
