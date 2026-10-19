import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

/** Lee las IPs que dejo infra/aws/provisionar.sh. */
function cargar(): Record<string, string> {
  const ruta = join(__dirname, '..', '..', 'infra', 'aws', 'entorno-actual.env');
  const valores: Record<string, string> = {};
  for (const linea of readFileSync(ruta, 'utf8').split('\n')) {
    const m = linea.match(/^([A-Z_]+)=(.*)$/);
    if (m) valores[m[1]] = m[2].trim();
  }
  return valores;
}

export const entorno = cargar();
export const CONSOLA = 'http://localhost:15672';
export const PEM = join(homedir(), 'Downloads', 'labsuser.pem');

/** Ejecuta un comando en una instancia por SSH. */
export function enInstancia(ip: string, comando: string): string {
  const opciones = `-i "${PEM}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=15`;
  return execSync(`ssh ${opciones} ec2-user@${ip} '${comando}'`, { encoding: 'utf8' });
}

export const enApps = (c: string) => enInstancia(entorno.APPS_IP_PUBLICA, c);

/** Cuantos mensajes tiene una cola, segun la API de management. */
export function mensajesEn(cola: string): number {
  const salida = execSync(
    `curl -s -u guest:guest "${CONSOLA}/api/queues/%2F/${cola}"`, { encoding: 'utf8' });
  try {
    return JSON.parse(salida).messages ?? 0;
  } catch {
    return 0;
  }
}

/** Cuantos consumidores tiene enganchados una cola. */
export function consumidoresEn(cola: string): number {
  const salida = execSync(
    `curl -s -u guest:guest "${CONSOLA}/api/queues/%2F/${cola}"`, { encoding: 'utf8' });
  try {
    return JSON.parse(salida).consumers ?? 0;
  } catch {
    return 0;
  }
}

/** Descarta los mensajes de una cola, para partir de un estado conocido. */
export function vaciar(cola: string): void {
  execSync(`curl -s -u guest:guest -X DELETE "${CONSOLA}/api/queues/%2F/${cola}/contents"`,
    { encoding: 'utf8' });
}

/**
 * Anota en que segundo de la grabacion ocurre cada paso.
 *
 * Sirve para cortar despues el clip en tramos y ponerle a cada uno su propia
 * narracion. Sin esto hay que adivinar donde empieza cada escena, y la voz
 * termina hablando de algo que ya no esta en pantalla.
 */
export class Marcador {
  private readonly t0 = Date.now();
  private readonly pasos: Array<{ paso: string; segundo: number }> = [];

  marcar(paso: string): void {
    this.pasos.push({ paso, segundo: (Date.now() - this.t0) / 1000 });
  }

  guardar(archivo: string): void {
    const fs = require('node:fs');
    fs.mkdirSync('montaje/marcas', { recursive: true });
    fs.writeFileSync(`montaje/marcas/${archivo}.json`, JSON.stringify(this.pasos, null, 2));
  }
}


/**
 * Control del consumidor, para los escenarios de fallo.
 *
 * El entorno se elige con la variable DESTINO: `local` opera sobre el proceso
 * de Maven que corre en esta maquina, y `aws` sobre el contenedor en ec2-apps.
 * La topologia es identica en los dos —el mismo cluster de dos nodos, las
 * mismas seis colas, los mismos TTL—, asi que la demostracion vale igual.
 */
export const DESTINO = process.env.DESTINO ?? 'local';
export const URL_APP = process.env.URL_APP ?? 'http://localhost:4200';

const raizProyecto = join(__dirname, '..', '..');

/** Baja el consumidor del todo: los consumidores de la cola pasan a cero. */
export function detenerConsumidor(): void {
  if (DESTINO === 'aws') {
    enApps('cd ~/infra/apps && sudo docker compose stop ms-notify');
    return;
  }
  // En local no hay contenedor: se mata el proceso de Maven.
  try {
    execSync("pkill -f 'ms-pedidos360-notify'", { stdio: 'ignore' });
  } catch {
    // pkill devuelve 1 si no encontro nada; no es un error aqui.
  }
}

/** Lo vuelve a levantar. En local tarda unos quince segundos en reconectar. */
export function levantarConsumidor(): void {
  if (DESTINO === 'aws') {
    enApps('cd ~/infra/apps && sudo docker compose up -d ms-notify');
    return;
  }
  const { spawn } = require('node:child_process');
  const hijo = spawn('mvn', ['spring-boot:run', '-Dspring-boot.run.profiles=dev'], {
    cwd: join(raizProyecto, 'ms-pedidos360-notify'),
    detached: true, stdio: 'ignore',
  });
  hijo.unref();
}

/**
 * Suspende el consumidor sin cerrar su conexion AMQP: los mensajes se acumulan
 * a la vista pero el consumidor sigue figurando conectado. Al reanudar consume
 * de inmediato, sin esperar un arranque.
 */
export function pausarConsumidor(): void {
  if (DESTINO === 'aws') {
    enApps('cd ~/infra/apps && sudo docker compose pause ms-notify');
    return;
  }
  try { execSync("pkill -STOP -f 'ms-pedidos360-notify'", { stdio: 'ignore' }); } catch { /* ya estaba */ }
}

export function reanudarConsumidor(): void {
  if (DESTINO === 'aws') {
    try { enApps('cd ~/infra/apps && sudo docker compose unpause ms-notify'); } catch { /* ya estaba */ }
    return;
  }
  try { execSync("pkill -CONT -f 'ms-pedidos360-notify'", { stdio: 'ignore' }); } catch { /* ya estaba */ }
}
