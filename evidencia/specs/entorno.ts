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
