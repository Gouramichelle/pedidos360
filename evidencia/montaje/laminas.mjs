import { chromium } from '@playwright/test';
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';

/**
 * Laminas de codigo para los tramos explicativos.
 *
 * Se renderizan como HTML y se capturan con el navegador: este ffmpeg viene
 * sin freetype, asi que no puede dibujar texto, y de todos modos asi se puede
 * resaltar la sintaxis y marcar las lineas que importan.
 */

const RAIZ = '../';
const leer = (p) => readFileSync(RAIZ + p, 'utf8').split('\n');
/** Extrae un rango de lineas 1-indexado, inclusive. */
const rango = (p, a, b) => leer(p).slice(a - 1, b).join('\n');

const LAMINAS = [
  {
    archivo: 'cfg-1-yml',
    titulo: 'La topologia se declara en el application.yml',
    pie: 'ms-pedidos360-orders/src/main/resources/application.yml',
    lenguaje: 'yaml',
    codigo: `mensajeria:
  exchanges:
    direct: cmd.direct
    topic: cmd.topic
    dlx: cmd.dead.dlx

  retencion:
    ttl-comando-ms: 30000        # un comando sin consumir vence a los 30 s
    ttl-dlq-ms: 1209600000       # la cola muerta guarda 14 dias
    max-mensajes-dlq: 1000       # y como mucho mil mensajes

  flujos:
    email:
      cola: q.cmd.email
      routing-key: email.send
      patron-topic: email.*
    kitchen:
      cola: q.cmd.kitchen
      routing-key: kitchen.ticket
      patron-topic: kitchen.#
    invoice:
      cola: q.cmd.invoice
      routing-key: invoice.gen
      patron-topic: invoice.*`,
    resaltar: [8, 9, 10],
  },
  {
    archivo: 'cfg-2-cola',
    titulo: 'Asi se construye cada cola de comando',
    pie: 'config/RabbitTopologyConfig.java',
    lenguaje: 'java',
    codigo: `private Queue colaDeFlujo(String flujo) {
    return QueueBuilder.durable(mensajeria.flujo(flujo).cola())

            // A donde van los mensajes que no se pudieron entregar
            .withArgument("x-dead-letter-exchange",
                          mensajeria.exchanges().dlx())
            .withArgument("x-dead-letter-routing-key",
                          routingKey(flujo))

            // Cuanto vive un comando sin que nadie lo consuma
            .withArgument("x-message-ttl",
                          mensajeria.retencion().ttlComandoMs())
            .build();
}`,
    resaltar: [10, 11],
  },
  {
    archivo: 'cfg-3-dlq',
    titulo: 'Y asi su cola muerta',
    pie: 'config/RabbitTopologyConfig.java',
    lenguaje: 'java',
    codigo: `private Queue dlqDeFlujo(String flujo) {
    return QueueBuilder.durable(mensajeria.flujo(flujo).dlq())

            // Retencion: la evidencia se guarda 14 dias
            .withArgument("x-message-ttl",
                          mensajeria.retencion().ttlDlqMs())

            // Limpieza: tope de mensajes, para que una tormenta
            // de errores no llene el disco del nodo
            .withArgument("x-max-length",
                          mensajeria.retencion().maxMensajesDlq())
            .build();
}

// Sin dead-lettering propio a proposito: lo que vence aqui
// se descarta. Esa es la politica de limpieza.`,
    resaltar: [16, 17],
  },
  {
    archivo: 'cfg-4-bindings',
    titulo: 'Tres bindings por flujo',
    pie: 'config/RabbitTopologyConfig.java — ejemplo del flujo de cocina',
    lenguaje: 'java',
    codigo: `@Bean  // clave exacta: un ticket tiene un unico destinatario
Binding bindKitchenDirect() {
    return BindingBuilder.bind(colaKitchen())
            .to(cmdDirectExchange()).with(routingKey(KITCHEN));
}

@Bean  // patron con comodines: admite variantes futuras
Binding bindKitchenTopic() {
    return BindingBuilder.bind(colaKitchen())
            .to(cmdTopicExchange()).with(patronTopic(KITCHEN));
}

@Bean  // la cola muerta cuelga del dead-letter exchange
Binding bindKitchenDlq() {
    return BindingBuilder.bind(colaKitchenDlq())
            .to(cmdDeadLetterExchange()).with(routingKey(KITCHEN));
}`,
    resaltar: [13, 14, 15, 16, 17],
  },
  {
    archivo: 'dlq-1-caminos',
    titulo: 'Dos caminos hacia la cola muerta',
    pie: 'El broker deja el motivo escrito en el header x-death de cada mensaje',
    lenguaje: 'texto',
    codigo: `  rejected
  ─────────────────────────────────────────────────────────
  El consumidor esta arriba y RECHAZA el mensaje porque
  viene mal formado: sin cliente, sin pedido, sin estado.

  Lo decide el codigo, con un NACK sin requeue.
  Reintentarlo daria exactamente el mismo resultado.


  expired
  ─────────────────────────────────────────────────────────
  Nadie lo consumio dentro del TTL de la cola. Por ejemplo
  porque el consumidor esta caido.

  Lo decide el broker, solo, sin que nadie intervenga.
  El mensaje estaba bien; falto quien lo atendiera.`,
    resaltar: [],
  },
];

const PINTAR = {
  yaml: (l) => l
    .replace(/(#.*)$/, '<i>$1</i>')
    .replace(/^(\s*)([a-z-]+):/, '$1<k>$2</k>:'),
  java: (l) => l
    .replace(/(\/\/.*)$/, '<i>$1</i>')
    .replace(/(&quot;[^&]*&quot;)/g, '<txt>$1</txt>')
    .replace(/\b(private|return|Binding|Queue|new|String)\b/g, '<k>$1</k>')
    .replace(/(@Bean)/g, '<a>$1</a>'),
  texto: (l) => l
    .replace(/^(\s{2})(rejected|expired)$/, '$1<a>$2</a>'),
};

const html = (L) => {
  const lineas = L.codigo.split('\n').map((l, i) => {
    const n = i + 1;
    const esc = l.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    const pintado = (PINTAR[L.lenguaje] || ((x) => x))(esc);
    const marca = L.resaltar.includes(n) ? ' class="hl"' : '';
    return `<div${marca}><n>${String(n).padStart(2, ' ')}</n>${pintado || '&nbsp;'}</div>`;
  }).join('');
  return `<!doctype html><html><head><meta charset="utf-8"><style>
 *{margin:0;padding:0;box-sizing:border-box}
 body{width:1280px;height:720px;background:#0f1115;color:#e8eaed;padding:44px 56px;
      font-family:-apple-system,"Segoe UI",Roboto,sans-serif}
 h1{font-size:34px;font-weight:700;letter-spacing:-.01em;margin-bottom:8px}
 .pie{font-size:17px;color:#767d89;font-family:"SF Mono",Menlo,monospace;margin-bottom:26px}
 pre{font-family:"SF Mono",Menlo,Consolas,monospace;font-size:19px;line-height:1.52}
 pre div{padding:1px 10px;border-radius:4px}
 pre div.hl{background:rgba(255,140,66,.16);box-shadow:inset 3px 0 0 #ff8c42}
 n{color:#4c535f;display:inline-block;width:38px;user-select:none}
 k{color:#ff8c42}  txt{color:#9ece6a}  i{color:#5f6672;font-style:normal}  a{color:#7aa2f7}
</style></head><body>
 <h1>${L.titulo}</h1><div class="pie">${L.pie}</div><pre>${lineas}</pre>
</body></html>`;
};

mkdirSync('montaje/laminas/png', { recursive: true });
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1280, height: 720 } });
for (const L of LAMINAS) {
  const f = `montaje/laminas/${L.archivo}.html`;
  writeFileSync(f, html(L));
  await p.goto(`file://${process.cwd()}/${f}`);
  await p.waitForTimeout(220);
  await p.screenshot({ path: `montaje/laminas/png/${L.archivo}.png` });
  console.log('  ' + L.archivo + '.png');
}
await b.close();
