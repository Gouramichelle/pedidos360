const pptxgen = require('pptxgenjs');

const DARK = '0B2027';
const TEAL = '028090';
const SEA = '00A896';
const MINT = '02C39A';
const INK = '22282A';
const MUTED = '667377';
const TINT = 'EEF6F6';
const WHITE = 'FFFFFF';

const HEAD = 'Cambria';
const BODY = 'Calibri';

const pres = new pptxgen();
pres.layout = 'LAYOUT_WIDE'; // 13.33 x 7.5
pres.author = 'Goura Rodriguez';
pres.title = 'Pedidos360 — Arquitectura Cloud Native';

const M = 0.7;
const W = 13.33 - M * 2;

function titulo(slide, texto) {
  slide.addText(texto, {
    x: M, y: 0.42, w: W, h: 0.75,
    fontFace: HEAD, fontSize: 32, bold: true, color: DARK,
    isTextBox: true, margin: 0,
  });
}

function bajada(slide, texto) {
  slide.addText(texto, {
    x: M, y: 1.18, w: W, h: 0.42,
    fontFace: BODY, fontSize: 13, color: MUTED, italic: true,
    isTextBox: true, margin: 0,
  });
}

function nota(slide, texto) {
  slide.addNotes(texto);
}

// Linea "Donde verlo" al pie, para slides sin referencia por item
function ubicacion(slide, texto) {
  slide.addText([
    { text: 'Donde verlo   ', options: { bold: true, fontSize: 10.5, color: TEAL, fontFace: BODY } },
    { text: texto, options: { fontSize: 10.5, color: MUTED, fontFace: 'Courier New' } },
  ], {
    x: M, y: 6.62, w: W, h: 0.45, isTextBox: true, margin: 0, valign: 'top',
  });
}

// Parrafo de descripcion con su referencia debajo
function conRef(desc, ref) {
  if (!ref) return desc;
  return [
    { text: desc, options: { breakLine: true } },
    { text: ref, options: { fontFace: 'Courier New', fontSize: 10, color: TEAL } },
  ];
}

// --- Portada / cierre en oscuro
function slidePortada(titulo1, titulo2, sub, detalle) {
  const s = pres.addSlide();
  s.background = { color: DARK };
  s.addText(titulo1, {
    x: M, y: 2.1, w: W, h: 0.95,
    fontFace: HEAD, fontSize: 48, bold: true, color: WHITE,
    isTextBox: true, margin: 0,
  });
  s.addText(titulo2, {
    x: M, y: 3.0, w: W, h: 0.7,
    fontFace: HEAD, fontSize: 30, color: MINT,
    isTextBox: true, margin: 0,
  });
  s.addText(sub, {
    x: M, y: 4.0, w: W - 1.2, h: 0.95,
    fontFace: BODY, fontSize: 16, color: 'B9C6C9',
    isTextBox: true, margin: 0,
  });
  s.addText(detalle, {
    x: M, y: 6.3, w: W, h: 0.4,
    fontFace: BODY, fontSize: 12, color: '7E9095',
    isTextBox: true, margin: 0,
  });
  return s;
}

// --- Divisor de fase
function slideFase(num, texto, sub, puntos) {
  const s = pres.addSlide();
  s.background = { color: DARK };
  s.addShape(pres.ShapeType.ellipse, {
    x: M, y: 2.35, w: 1.5, h: 1.5, fill: { color: TEAL },
  });
  s.addText(num, {
    x: M, y: 2.35, w: 1.5, h: 1.5,
    fontFace: HEAD, fontSize: 52, bold: true, color: WHITE,
    align: 'center', valign: 'middle', isTextBox: true, margin: 0,
  });
  s.addText(texto, {
    x: 2.6, y: 2.4, w: 9.8, h: 0.8,
    fontFace: HEAD, fontSize: 38, bold: true, color: WHITE,
    isTextBox: true, margin: 0,
  });
  s.addText(sub, {
    x: 2.6, y: 3.2, w: 9.8, h: 0.5,
    fontFace: BODY, fontSize: 15, color: MINT,
    isTextBox: true, margin: 0,
  });
  if (puntos) {
    s.addText(puntos.map((p, i) => ({
      text: p, options: { breakLine: i < puntos.length - 1 },
    })), {
      x: 2.6, y: 3.9, w: 9.8, h: 1.4,
      fontFace: BODY, fontSize: 13, color: 'B9C6C9',
      bullet: { code: '2022' }, paraSpaceAfter: 6,
      isTextBox: true, margin: 0,
    });
  }
  return s;
}

// --- Pasos numerados
function slidePasos(tit, sub, pasos) {
  const s = pres.addSlide();
  titulo(s, tit);
  if (sub) bajada(s, sub);
  const y0 = sub ? 1.78 : 1.5;
  const alto = pasos.length <= 4 ? 1.28 : 1.08;
  pasos.forEach((p, i) => {
    const y = y0 + i * alto;
    s.addShape(pres.ShapeType.ellipse, {
      x: M, y: y + 0.02, w: 0.46, h: 0.46, fill: { color: TEAL },
    });
    s.addText(String(i + 1), {
      x: M, y: y + 0.02, w: 0.46, h: 0.46,
      fontFace: HEAD, fontSize: 15, bold: true, color: WHITE,
      align: 'center', valign: 'middle', isTextBox: true, margin: 0,
    });
    s.addText(p.t, {
      x: M + 0.68, y: y, w: W - 0.68, h: 0.34,
      fontFace: BODY, fontSize: 15.5, bold: true, color: INK,
      isTextBox: true, margin: 0,
    });
    s.addText(conRef(p.d, p.r), {
      x: M + 0.68, y: y + 0.33, w: W - 0.68, h: alto - 0.36,
      fontFace: BODY, fontSize: 12.5, color: MUTED,
      isTextBox: true, margin: 0,
    });
  });
  return s;
}

// --- Tarjetas (2 columnas x N filas)
function slideTarjetas(tit, sub, tarjetas) {
  const s = pres.addSlide();
  titulo(s, tit);
  if (sub) bajada(s, sub);
  const cols = 2;
  const filas = Math.ceil(tarjetas.length / cols);
  const y0 = sub ? 1.8 : 1.55;
  const ancho = (W - 0.4) / cols;
  const alto = filas <= 2 ? 2.3 : 1.55;
  tarjetas.forEach((c, i) => {
    const col = i % cols;
    const fila = Math.floor(i / cols);
    const x = M + col * (ancho + 0.4);
    const y = y0 + fila * (alto + 0.35);
    s.addShape(pres.ShapeType.roundRect, {
      x, y, w: ancho, h: alto, rectRadius: 0.08,
      fill: { color: TINT },
      shadow: { type: 'outer', angle: 90, blur: 8, offset: 1, opacity: 0.12, color: '9AAAAE' },
    });
    s.addText(c.t, {
      x: x + 0.3, y: y + 0.22, w: ancho - 0.6, h: 0.35,
      fontFace: BODY, fontSize: 15, bold: true, color: TEAL,
      isTextBox: true, margin: 0,
    });
    s.addText(conRef(c.d, c.r), {
      x: x + 0.3, y: y + 0.62, w: ancho - 0.6, h: alto - 0.85,
      fontFace: BODY, fontSize: 12.5, color: INK,
      isTextBox: true, margin: 0,
    });
  });
  return s;
}

// --- Tabla
function slideTabla(tit, sub, encabezados, filas, anchos) {
  const s = pres.addSlide();
  titulo(s, tit);
  if (sub) bajada(s, sub);
  const head = encabezados.map((h) => ({
    text: h,
    options: { bold: true, color: WHITE, fill: { color: TEAL }, fontSize: 12.5, fontFace: BODY },
  }));
  const body = filas.map((f, idx) =>
    f.map((celda) => ({
      text: celda,
      options: {
        fontSize: 11.5, fontFace: BODY, color: INK,
        fill: { color: idx % 2 === 0 ? WHITE : TINT },
      },
    })),
  );
  s.addTable([head, ...body], {
    x: M, y: sub ? 1.85 : 1.6, w: W,
    colW: anchos,
    border: { type: 'solid', color: 'DDE6E7', pt: 1 },
    align: 'left', valign: 'middle',
    rowH: 0.34,
    margin: 0.09,
  });
  return s;
}

// --- Cifras grandes
function slideCifras(tit, sub, cifras, pie) {
  const s = pres.addSlide();
  titulo(s, tit);
  if (sub) bajada(s, sub);
  const ancho = (W - 0.45 * (cifras.length - 1)) / cifras.length;
  cifras.forEach((c, i) => {
    const x = M + i * (ancho + 0.45);
    s.addText(c.n, {
      x, y: 2.05, w: ancho, h: 1.25,
      fontFace: HEAD, fontSize: 60, bold: true, color: TEAL,
      align: 'center', isTextBox: true, margin: 0,
    });
    s.addText(c.t, {
      x, y: 3.32, w: ancho, h: 0.85,
      fontFace: BODY, fontSize: 13, color: INK,
      align: 'center', isTextBox: true, margin: 0,
    });
  });
  if (pie) {
    s.addText(pie, {
      x: M, y: 5.25, w: W, h: 0.9,
      fontFace: BODY, fontSize: 13.5, color: MUTED,
      isTextBox: true, margin: 0,
    });
  }
  return s;
}

// --- Dos columnas con listas
function slideDosColumnas(tit, sub, izq, der) {
  const s = pres.addSlide();
  titulo(s, tit);
  if (sub) bajada(s, sub);
  const ancho = (W - 0.6) / 2;
  const y0 = sub ? 1.85 : 1.6;
  [izq, der].forEach((col, i) => {
    const x = M + i * (ancho + 0.6);
    s.addText(col.t, {
      x, y: y0, w: ancho, h: 0.4,
      fontFace: BODY, fontSize: 17, bold: true, color: i === 0 ? TEAL : SEA,
      isTextBox: true, margin: 0,
    });
    s.addText(col.items.map((p, j) => ({
      text: p, options: { breakLine: j < col.items.length - 1 },
    })), {
      x, y: y0 + 0.5, w: ancho, h: 4.2,
      fontFace: BODY, fontSize: 13, color: INK,
      bullet: { code: '2022' }, paraSpaceAfter: 9,
      isTextBox: true, margin: 0,
    });
  });
  return s;
}

/* ========================= CONTENIDO ========================= */

const JAVA = (svc, resto) => `ms-pedidos360-${svc}/src/main/java/.../${resto}`;

// 1
let s = slidePortada(
  'Pedidos360',
  'Arquitectura Cloud Native',
  'Desarrollo Cloud Native I - DSY1107 - Evaluaciones Parciales 1 y 2',
  'Angular + MSAL - Spring Boot - AWS API Gateway - EC2 - RDS - RabbitMQ - Kafka',
);
nota(s, 'Cada slide indica al pie o bajo cada punto donde verlo: ruta del archivo en el repositorio o ubicacion en la consola.');

// 2
s = slideCifras('Que se construyo', 'Alcance completo del sistema entregado',
  [
    { n: '5', t: 'Microservicios\nSpring Boot 3 y Java 21' },
    { n: '4', t: 'Roles de aplicacion\nAdmin, Operador, Cliente, Auditor' },
    { n: '2', t: 'Sistemas de mensajeria\nRabbitMQ y Kafka' },
    { n: '70', t: 'Pruebas automatizadas\nen los cinco servicios' },
  ],
  'Un frontend Angular con inicio de sesion corporativo, cinco microservicios detras de un API Gateway que valida el token, mensajeria asincrona y base de datos administrada, todo desplegado en la nube.');
ubicacion(s, 'github.com/Gouramichelle/pedidos360  ·  README.md');

// 3
s = slideTarjetas('Arquitectura de la solucion', 'Como viaja una peticion desde el navegador hasta la base de datos',
  [
    { t: '1. Navegador', d: 'La aplicacion Angular obtiene un token de Azure AD mediante MSAL y lo adjunta a cada llamada a la API.',
      r: 'frontend-pedidos360/src/app/core/auth/msal.config.ts' },
    { t: '2. API Gateway', d: 'Valida firma, emisor, vigencia y audiencia del token antes de enrutar. Es el equivalente al API Manager del caso.',
      r: 'Consola AWS > API Gateway > pedidos360-api > Authorization' },
    { t: '3. Microservicios', d: 'Cada uno vuelve a validar el token de forma independiente y aplica autorizacion por rol en cada endpoint.',
      r: JAVA('*', 'config/SecurityConfig.java') },
    { t: '4. Datos y mensajeria', d: 'PostgreSQL en RDS para el estado, RabbitMQ para comandos y Kafka para eventos de negocio.',
      r: 'infra/apps/docker-compose.yml' },
  ]);
nota(s, 'La doble validacion del token es intencional: aunque alguien alcance el puerto del microservicio sin pasar por el Gateway, necesita un token vigente.');

// 4
s = slideDosColumnas('Decisiones de diseno', 'Tomadas al inicio y sostenidas durante todo el proyecto',
  {
    t: 'Que se decidio',
    items: [
      'PostgreSQL en RDS en lugar de Oracle, por costo y tiempo de aprovisionamiento.',
      'Sin BFF: la validacion de borde la cubren el API Gateway y Spring Security en cada servicio.',
      'Monorepo en lugar de seis repositorios separados.',
      'Una sola instancia de base de datos con un esquema por servicio.',
      'Cuatro roles, sumando Auditor al trio del caso.',
    ],
  },
  {
    t: 'Por que',
    items: [
      'El lab tiene presupuesto acotado y Oracle no aporta nada al objetivo evaluado.',
      'Un BFF repetiria una validacion que ya ocurre dos veces, sin agregar una garantia distinta.',
      'Facilita revisar el proyecto completo y mantener sincronizados los contratos entre servicios.',
      'Reduce costo y tiempo sin perder el aislamiento logico entre dominios.',
      'El caso menciona al Auditor como actor del modulo de auditoria.',
    ],
  });
ubicacion(s, 'README.md  ·  ms-pedidos360-*/src/main/resources/application.yml (DB_SCHEMA)');

// 5
s = slidePasos('Orden de trabajo', 'La secuencia importa: cada fase depende de datos que produce la anterior',
  [
    { t: 'Fase 1 - Identidad en Azure AD', d: 'Primero, porque produce el identificador de cliente, el de inquilino y el scope que despues consumen el frontend, el Gateway y los cinco microservicios.',
      r: 'Portal de Azure > Microsoft Entra ID' },
    { t: 'Fase 2 - Infraestructura en AWS', d: 'Red, security groups, base de datos e instancias. Produce los endpoints y direcciones que necesita la configuracion del codigo.',
      r: 'Consola AWS > VPC, EC2, RDS' },
    { t: 'Fase 3 - Desarrollo del codigo', d: 'Microservicios y frontend, escritos contra la configuracion ya conocida de las dos fases anteriores.',
      r: 'frontend-pedidos360/  y  ms-pedidos360-*/' },
    { t: 'Fase 4 - Despliegue', d: 'Contenedores en las instancias, mensajeria, servidor web y por ultimo el API Gateway, que necesita las direcciones ya activas.',
      r: 'infra/  ·  README.md > Despliegue' },
    { t: 'Fase 5 - Verificacion', d: 'Prueba del flujo completo de negocio y de los controles de seguridad, con evidencia de respuestas 200, 401 y 403.',
      r: 'README.md > 8. Verificacion' },
  ]);
nota(s, 'Explicar por que este orden y no otro: la identidad primero porque todo lo demas la consume.');

/* ---------- FASE 1 ---------- */
s = slideFase('1', 'Identidad', 'Microsoft Entra ID como proveedor de identidad',
  ['Se hace una sola vez y no depende de AWS', 'Produce los identificadores que consume todo el resto del sistema',
   'Todo ocurre en portal.azure.com, no hay codigo involucrado']);

s = slidePasos('Inquilino y registro de la aplicacion', 'Portal de Azure - Microsoft Entra ID',
  [
    { t: 'Crear el inquilino de Microsoft Entra ID', d: 'Es el directorio propio donde viven los usuarios y las aplicaciones del proyecto. Queda con un dominio del tipo nombre.onmicrosoft.com.',
      r: 'Portal de Azure > Microsoft Entra ID > Overview' },
    { t: 'Registrar la aplicacion Pedidos360', d: 'Con tipo de cuentas limitado al directorio propio. Entrega el identificador de aplicacion y el de inquilino, que se usan en todo el resto del sistema.',
      r: 'Entra ID > App registrations > New registration' },
    { t: 'Declarar la plataforma como aplicacion de pagina unica', d: 'Es lo que habilita el flujo moderno con codigo de autorizacion y PKCE en lugar del flujo implicito, ya obsoleto.',
      r: 'App registrations > Pedidos360 > Authentication' },
    { t: 'Registrar el URI de redireccion', d: 'La direccion a la que Azure AD devuelve al usuario tras autenticarse. Debe coincidir exactamente con la que usa el frontend.',
      r: 'Authentication > Single-page application > Redirect URIs' },
  ]);
nota(s, 'Mostrar en vivo la pantalla de Overview con el Application ID y el Directory ID.');

s = slidePasos('Exponer la API y definir los permisos', 'El backend se declara como recurso protegido',
  [
    { t: 'Definir el identificador de la API', d: 'Se acepta el valor propuesto, del tipo api://identificador-de-aplicacion. Es el nombre con el que el backend se identifica como recurso.',
      r: 'Pedidos360 > Expose an API > Application ID URI' },
    { t: 'Crear el scope access_as_user', d: 'Representa el permiso que la aplicacion pide en nombre del usuario. Sin el, Azure AD devuelve un token para Microsoft Graph y no para nuestra API.',
      r: 'Expose an API > Add a scope' },
    { t: 'Definir los cuatro roles de aplicacion', d: 'Admin, Operador, Cliente y Auditor. El valor de cada rol viaja en el token y es exactamente lo que evalua el backend, mayusculas incluidas.',
      r: 'Pedidos360 > App roles' },
    { t: 'Otorgar el consentimiento de administrador', d: 'Evita que a cada usuario, y en particular a los invitados de otra organizacion, le aparezca una pantalla de aprobacion.',
      r: 'Pedidos360 > API permissions > Grant admin consent' },
  ]);
nota(s, 'El valor del rol debe coincidir exactamente con lo que evalua @PreAuthorize en los controladores.');

s = slidePasos('Emision de tokens y usuarios', 'Dos pasos que condicionan todo el funcionamiento posterior',
  [
    { t: 'Forzar la version 2 de los tokens de acceso', d: 'Sin esto Azure AD emite tokens de version 1, cuyo emisor no coincide con el configurado en el Gateway ni en los microservicios, y todo responde 401 pese a que el login funciona.',
      r: 'Pedidos360 > Manifest > api.requestedAccessTokenVersion' },
    { t: 'Crear los usuarios de prueba', d: 'Un usuario administrador con los cuatro roles para la demostracion, y usuarios con un solo rol para comprobar las restricciones reales.',
      r: 'Entra ID > Users > New user' },
    { t: 'Invitar usuarios de otra organizacion', d: 'Mediante invitacion de colaboracion. El invitado acepta simplemente iniciando sesion en la aplicacion, sin necesidad del correo.',
      r: 'Entra ID > Users > Invite external user' },
    { t: 'Asignar los roles a cada usuario', d: 'Un usuario puede tener varios roles, agregandolo una vez por cada uno.',
      r: 'Enterprise applications > Pedidos360 > Users and groups' },
  ]);
nota(s, 'La version 2 del token fue el origen de un 401 persistente pese a que el login funcionaba correctamente.');

/* ---------- FASE 2 ---------- */
s = slideFase('2', 'Infraestructura', 'Red, base de datos y servidores en AWS',
  ['Se usa la nube del laboratorio academico', 'Produce las direcciones y endpoints que consume la configuracion',
   'Todo en la consola de AWS, region us-east-1']);

s = slidePasos('Red y control de acceso', 'Consola de AWS - VPC y EC2',
  [
    { t: 'Confirmar la region habilitada', d: 'Toda la infraestructura debe quedar en la misma region, indicada por las restricciones del laboratorio.',
      r: 'Consola AWS > selector de region, arriba a la derecha' },
    { t: 'Usar la red virtual por defecto', d: 'Ya viene creada con subredes publicas que asignan direccion publica automaticamente. No es necesario crear nada.',
      r: 'Consola AWS > VPC > Your VPCs  y  VPC > Subnets' },
    { t: 'Crear cinco grupos de seguridad', d: 'Uno por funcion: acceso remoto, microservicios, mensajeria de comandos, mensajeria de eventos y base de datos.',
      r: 'Consola AWS > EC2 > Security Groups' },
    { t: 'Encadenar los permisos entre grupos', d: 'En lugar de abrir puertos a direcciones fijas, cada grupo autoriza al grupo que lo necesita. La base de datos solo acepta conexiones desde los microservicios.',
      r: 'Security Groups > Inbound rules > Source = otro grupo' },
  ]);
nota(s, 'Encadenar grupos evita reconfigurar reglas cada vez que cambian las direcciones.');

s = slideTabla('Grupos de seguridad', 'Que abre cada uno y desde donde',
  ['Grupo', 'Puertos', 'Origen autorizado', 'Proposito'],
  [
    ['seg-ssh', '22', 'Mi direccion IP', 'Administracion remota de las instancias'],
    ['seg-apps', '8080 a 8085', 'Internet', 'Microservicios y servidor web del frontend'],
    ['seg-mq', '5672 y 15672', 'seg-apps', 'RabbitMQ y su consola de administracion'],
    ['seg-kafka', '9092 y 2181', 'seg-apps y si mismo', 'Kafka y Zookeeper'],
    ['seg-rds', '5432', 'seg-apps', 'PostgreSQL, sin acceso desde internet'],
  ],
  [1.7, 1.7, 2.6, 5.93]);
ubicacion(s, 'Consola AWS > EC2 > Security Groups  ·  README.md > 2. Red y Security Groups');

s = slidePasos('Base de datos y servidores', 'RDS y EC2',
  [
    { t: 'Crear la instancia PostgreSQL administrada', d: 'Con la plantilla de capa gratuita, sin acceso publico y protegida por su grupo de seguridad. Se define una base inicial llamada pedidos360.',
      r: 'Consola AWS > RDS > Create database' },
    { t: 'Separar los dominios por esquema', d: 'Los cuatro servicios con persistencia comparten la instancia y la base, cada uno en su propio esquema. Hibernate crea esquemas y tablas al arrancar.',
      r: 'ms-pedidos360-*/src/main/resources/application.yml' },
    { t: 'Lanzar la instancia de aplicaciones', d: 'Ejecuta los cinco microservicios y el servidor web. Necesita memoria suficiente: con dos gigabytes las compilaciones la dejaban sin responder.',
      r: 'Consola AWS > EC2 > Instances > ec2-apps' },
    { t: 'Lanzar la instancia de mensajeria', d: 'Ejecuta RabbitMQ, Kafka y Zookeeper, separada de las aplicaciones para que un pico de carga no afecte al resto.',
      r: 'Consola AWS > EC2 > Instances > ec2-mq-kafka' },
    { t: 'Instalar Docker al arranque', d: 'Mediante un script que instala el motor y el complemento de composicion en el primer inicio de cada instancia.',
      r: 'EC2 > Launch instance > Advanced details > User data' },
  ]);
nota(s, 'El dimensionamiento de la instancia de aplicaciones fue un problema real: hubo que ampliarla a t3.medium.');

/* ---------- FASE 3 ---------- */
s = slideFase('3', 'Desarrollo', 'Los cinco microservicios y el frontend',
  ['Escritos contra la configuracion ya conocida de identidad e infraestructura',
   'Cada servicio con su propio dominio, base y responsabilidad',
   'Repositorio: github.com/Gouramichelle/pedidos360']);

s = slideTabla('Estructura del repositorio', 'Un repositorio unico con los seis componentes',
  ['Carpeta', 'Responsabilidad', 'Persistencia'],
  [
    ['frontend-pedidos360', 'Interfaz Angular con inicio de sesion y vistas por rol', 'No aplica'],
    ['ms-pedidos360-orders', 'Pedidos, maquina de estados y publicacion de eventos', 'Esquema orders'],
    ['ms-pedidos360-catalog', 'Productos, precios y control de stock', 'Esquema catalog'],
    ['ms-pedidos360-notify', 'Notificaciones, consume comandos de RabbitMQ', 'Sin base de datos'],
    ['ms-pedidos360-report', 'Indicadores de gestion, consume eventos de Kafka', 'Esquema report'],
    ['ms-pedidos360-audit', 'Trazabilidad de eventos, consume de Kafka', 'Esquema audit'],
    ['infra', 'Composicion de contenedores y documentacion de despliegue', 'No aplica'],
  ],
  [3.5, 6.7, 1.73]);
ubicacion(s, 'Raiz del repositorio  ·  cada carpeta tiene su README.md, Dockerfile y pruebas');

s = slidePasos('Servicio de pedidos', 'El nucleo del dominio y el unico que publica eventos',
  [
    { t: 'Maquina de estados del pedido', d: 'Creado, aceptado, en preparacion, despachado, entregado o cancelado. Las transiciones validas estan declaradas en el propio tipo, no dispersas en condicionales.',
      r: JAVA('orders', 'domain/OrderStatus.java') },
    { t: 'La regla clave del caso queda garantizada por diseno', d: 'No se puede despachar sin aceptar, porque despachado solo es alcanzable desde en preparacion, que a su vez exige haber aceptado.',
      r: JAVA('orders', 'domain/Order.java') + ' > cambiarEstado()' },
    { t: 'Coordinacion con el catalogo', d: 'Al aceptar descuenta el stock llamando al catalogo y propagando el token del usuario. Si el catalogo rechaza, la transicion se revierte.',
      r: JAVA('orders', 'web/OrderService.java') + '  ·  client/CatalogClient.java' },
    { t: 'Publicacion hacia los dos sistemas de mensajeria', d: 'Cada cambio de estado publica un evento en Kafka y encola un comando de notificacion en RabbitMQ.',
      r: JAVA('orders', 'messaging/OrderEventPublisher.java') + '  ·  EmailCommandPublisher.java' },
  ]);
nota(s, 'Las pruebas de la maquina de estados estan en src/test/.../OrderServiceTest.java');

s = slideTarjetas('Los otros cuatro servicios', 'Cada uno con una responsabilidad acotada',
  [
    { t: 'Catalogo', d: 'Productos, precios y stock. Usa bloqueo optimista para que dos descuentos simultaneos no se pisen. Es la fuente de verdad del precio.',
      r: JAVA('catalog', 'domain/Product.java') },
    { t: 'Notificaciones', d: 'Consume comandos de RabbitMQ. No expone interfaz publica y descarta mensajes repetidos por identificador de evento.',
      r: JAVA('notify', 'messaging/EmailNotificationListener.java') },
    { t: 'Reporteria', d: 'Consume eventos de Kafka y mantiene una proyeccion de solo lectura para ventas por hora, tiempo de ciclo y pedidos activos.',
      r: JAVA('report', 'web/ReportService.java') },
    { t: 'Auditoria', d: 'Consume los mismos eventos y guarda quien hizo que y cuando, con filtros por pedido, usuario y rango de fechas.',
      r: JAVA('audit', 'messaging/OrderEventsListener.java') },
  ]);
nota(s, 'Reporteria y auditoria consumen el mismo evento de forma independiente, cada una con su propio avance de lectura.');

s = slidePasos('Seguridad en el backend', 'La misma configuracion replicada en los servicios expuestos',
  [
    { t: 'Validacion del token en cuatro puntos', d: 'Firma contra las claves publicas del emisor, emisor exacto, vigencia y audiencia. Se construye de forma explicita para que quede a la vista que se comprueba.',
      r: JAVA('*', 'config/SecurityConfig.java') + ' > jwtDecoder()' },
    { t: 'Aceptar los dos formatos de audiencia', d: 'El identificador del recurso cambia de formato entre versiones del token, asi que se aceptan ambos.',
      r: JAVA('*', 'config/AudienceValidator.java') },
    { t: 'Lectura de roles y permisos desde el token', d: 'Los roles de aplicacion y los permisos delegados se traducen a autoridades que Spring Security entiende. Sin esta traduccion la verificacion de roles falla siempre.',
      r: JAVA('*', 'config/SecurityConfig.java') + ' > extraerAuthorities()' },
    { t: 'Autorizacion por endpoint y codigos de error', d: 'Cada operacion declara que roles la pueden ejecutar. Responde 401 si falta el token o es invalido, y 403 si el token es valido pero el rol no alcanza.',
      r: JAVA('*', 'web/*Controller.java') + ' > @PreAuthorize' },
  ]);
nota(s, 'La distincion entre 401 y 403 es la evidencia central del indicador de validacion del token.');

s = slideDosColumnas('Autorizacion a nivel de objeto', 'Una revision propia del codigo detecto que el control por rol no era suficiente',
  {
    t: 'Que se detecto',
    items: [
      'El detalle de un pedido no verificaba pertenencia: un cliente podia leer cualquier pedido cambiando el identificador en la direccion.',
      'El identificador del cliente se tomaba del cuerpo de la peticion, de modo que se podian crear pedidos a nombre de otra persona.',
      'El precio tambien venia del cuerpo, asi que se podia pedir al precio que se quisiera y se distorsionaban los indicadores de ventas.',
    ],
  },
  {
    t: 'Como se corrigio',
    items: [
      'El detalle responde como no encontrado cuando el pedido pertenece a otra persona, para no revelar siquiera que existe.',
      'El cliente se resuelve desde el token. Administrador y operador conservan la posibilidad de tomar un pedido en nombre de un cliente.',
      'El precio y el codigo de producto se consultan al catalogo al momento de crear el pedido.',
      'Se agregaron cuatro pruebas de regresion que fijan estos comportamientos.',
    ],
  });
ubicacion(s, 'ms-pedidos360-orders: web/OrderController.java  ·  web/OrderService.java  ·  src/test/.../OrderServiceTest.java');

s = slidePasos('Frontend en Angular', 'Inicio de sesion corporativo y vistas por rol',
  [
    { t: 'Inicio de sesion con Authorization Code y PKCE', d: 'Se genera un verificador aleatorio y su desafio con SHA-256, que viaja en la peticion de autorizacion. Ademas se validan el parametro state, contra falsificacion de peticion, y el nonce, contra reuso del token.',
      r: 'Navegador > DevTools > Network > authorize?...code_challenge_method=S256' },
    { t: 'Solicitud explicita del permiso de la API', d: 'Al iniciar sesion se pide el scope de la API. Sin esto el token devuelto sirve para Microsoft Graph y llega sin los roles.',
      r: 'src/app/features/login/login.component.ts' },
    { t: 'Interceptor que adjunta el token', d: 'Agrega la credencial unicamente a las llamadas dirigidas al backend propio, nunca a terceros.',
      r: 'src/app/core/auth/msal.config.ts > msalInterceptorConfigFactory' },
    { t: 'Guardas de ruta por autenticacion y por rol', d: 'Una comprueba que haya sesion y lleva a la pantalla de inicio; otra comprueba que el rol permita ver esa pantalla.',
      r: 'src/app/core/guards/auth.guard.ts  ·  role.guard.ts  ·  app.routes.ts' },
    { t: 'Lectura de roles y selector de vista', d: 'Los roles se leen de los claims del token. El selector permite previsualizar la interfaz de cada perfil sin cerrar sesion.',
      r: 'src/app/core/auth/current-user.service.ts  ·  layout/shell/shell.component.ts' },
  ]);
nota(s, 'Las pantallas siguen la propuesta del caso: login, dashboard, pedidos, catalogo, reportes y auditoria, en src/app/features/');

s = slideDosColumnas('Mensajeria: dos sistemas, dos propositos', 'La diferencia esta en que le pasa al mensaje cuando alguien lo lee',
  {
    t: 'RabbitMQ - comandos',
    items: [
      'Expresa una orden: enviar esta notificacion.',
      'Un unico consumidor la toma, la ejecuta y la confirma; entonces desaparece de la cola.',
      'Si falla se reintenta, y si insiste en fallar termina en una cola de descarte.',
      'Topologia del caso: tres intercambiadores, tres colas de trabajo y sus colas de descarte.',
    ],
  },
  {
    t: 'Kafka - eventos',
    items: [
      'Expresa un hecho: el pedido cambio de estado.',
      'El evento permanece durante su periodo de retencion y muchos consumidores lo leen de forma independiente.',
      'Reporteria y auditoria avanzan cada una a su ritmo, sin afectarse entre si.',
      'Permite reprocesar el pasado retrocediendo la posicion de lectura, algo imposible con una cola.',
    ],
  });
ubicacion(s, 'orders: config/RabbitTopologyConfig.java  ·  config/KafkaProducerConfig.java  ·  infra/mq y infra/kafka');

s = slideTarjetas('Trazabilidad de extremo a extremo', 'Un mismo identificador recorre los cuatro servicios',
  [
    { t: 'Sobre comun para todos los mensajes', d: 'Cada mensaje lleva tipo, identificador de evento, marca de tiempo, identificador de traza y de correlacion, tal como pide el caso.',
      r: JAVA('orders', 'messaging/EventEnvelope.java') },
    { t: 'Un identificador por operacion', d: 'El mismo identificador de correlacion aparece en los registros del servicio que publica y en los de los tres que consumen.',
      r: JAVA('orders', 'messaging/OrderEventPublisher.java') },
    { t: 'Idempotencia en los comandos', d: 'El servicio de notificaciones descarta un mensaje ya procesado, para que un reenvio del broker no genere dos correos.',
      r: JAVA('notify', 'messaging/EmailNotificationListener.java') },
    { t: 'Descarte de mensajes irrecuperables', d: 'Cada consumidor reintenta y, si el mensaje sigue fallando, lo publica en su propio destino de descarte con los datos del error.',
      r: JAVA('report', 'config/KafkaConsumerConfig.java') },
  ]);
nota(s, 'Permite seguir una sola operacion de punta a punta atravesando los dos sistemas de mensajeria.');

/* ---------- FASE 4 ---------- */
s = slideFase('4', 'Despliegue', 'De codigo fuente a sistema corriendo en la nube',
  ['Contenedores en las instancias, mensajeria primero',
   'El API Gateway al final, porque necesita las direcciones activas',
   'Procedimiento completo en README.md > Despliegue']);

s = slidePasos('Contenedores y mensajeria', 'Primero la infraestructura de la que dependen los servicios',
  [
    { t: 'Imagenes construidas en dos etapas', d: 'Una etapa compila con Maven y otra conserva solo el entorno de ejecucion de Java. La imagen final no incluye el compilador ni el codigo fuente.',
      r: 'ms-pedidos360-*/Dockerfile  ·  frontend-pedidos360/Dockerfile' },
    { t: 'Levantar RabbitMQ y Kafka', d: 'Cada uno con su propia composicion de contenedores en la instancia de mensajeria, con volumenes para que los datos sobrevivan a un reinicio.',
      r: 'infra/mq/docker-compose.yml  ·  infra/kafka/docker-compose.yml' },
    { t: 'Anunciar la direccion correcta de Kafka', d: 'Debe anunciar la direccion privada de su instancia, porque los consumidores corren en otra maquina. Anunciar la direccion local hace que se conecten pero nunca reciban nada.',
      r: 'infra/kafka/docker-compose.yml > KAFKA_ADVERTISED_LISTENERS' },
    { t: 'Declarar los temas desde el codigo', d: 'La creacion automatica esta deshabilitada en el intermediario, asi que los temas de eventos, auditoria y descarte los declaran los propios servicios al arrancar.',
      r: JAVA('orders', 'config/KafkaProducerConfig.java') + '  ·  audit: config/KafkaConfig.java' },
  ]);
nota(s, 'La direccion anunciada por Kafka es un error clasico y silencioso: todo parece conectado pero no llegan mensajes.');

s = slidePasos('Microservicios y frontend', 'En la instancia de aplicaciones',
  [
    { t: 'Un unico archivo de configuracion', d: 'Reune el acceso a la base de datos, las direcciones de la mensajeria y los parametros de identidad. Nunca se versiona: el repositorio solo incluye un ejemplo con valores ficticios.',
      r: 'infra/apps/.env (no versionado)  ·  ms-pedidos360-*/.env.example' },
    { t: 'Configuracion por variables de entorno', d: 'Ningun valor sensible esta escrito en el codigo. Los servicios no arrancan si falta un parametro de seguridad, lo que es preferible a arrancar mal configurados.',
      r: 'ms-pedidos360-*/src/main/resources/application.yml' },
    { t: 'Levantar los cinco servicios', d: 'Con una sola composicion de contenedores, cada servicio en su puerto y con reinicio automatico si la instancia se reinicia.',
      r: 'infra/apps/docker-compose.yml' },
    { t: 'Servir el frontend con Nginx', d: 'Entrega los archivos compilados y reescribe las rutas internas para que recargar una direccion profunda no devuelva un error.',
      r: 'frontend-pedidos360/nginx.conf' },
    { t: 'Habilitar HTTPS', d: 'Con certificado autofirmado, porque Azure AD exige protocolo seguro en la direccion de retorno de una aplicacion de pagina unica.',
      r: 'frontend-pedidos360/Dockerfile > openssl req' },
  ]);
nota(s, 'El certificado autofirmado obliga a aceptar una advertencia del navegador la primera vez.');

s = slidePasos('API Gateway', 'El ultimo paso, porque necesita las direcciones ya activas',
  [
    { t: 'Crear las integraciones hacia los microservicios', d: 'Una por servicio, apuntando a su puerto en la instancia de aplicaciones. Pedidos necesita dos: una para la coleccion y otra para los elementos individuales.',
      r: 'API Gateway > pedidos360-api > Integrations' },
    { t: 'Definir las rutas', d: 'Con una variable que captura los segmentos siguientes y los reenvia al backend. Esa variable exige al menos un segmento, y por eso la coleccion requiere su propia ruta.',
      r: 'API Gateway > pedidos360-api > Routes' },
    { t: 'Configurar el validador de token', d: 'Con el emisor del inquilino y las audiencias aceptadas. Debe adjuntarse a cada ruta: no se aplica de forma automatica.',
      r: 'API Gateway > Authorization > azure-ad-jwt' },
    { t: 'Configurar el intercambio entre origenes', d: 'Permite que el navegador, servido desde otra direccion, pueda llamar a la API declarando los origenes, metodos y cabeceras admitidos.',
      r: 'API Gateway > pedidos360-api > CORS' },
    { t: 'Publicacion automatica', d: 'La etapa por defecto publica cada cambio sin un paso manual, y entrega la direccion estable que consume el frontend.',
      r: 'API Gateway > Stages > $default  ·  frontend: environment.prod.ts' },
  ]);
nota(s, 'Las audiencias deben incluir los dos formatos posibles, porque cambian entre versiones del token.');

s = slideTabla('Rutas del API Manager', 'Las cinco rutas creadas y a que microservicio dirige cada una',
  ['Metodo', 'Ruta en el Gateway', 'Destino en ec2-apps', 'Servicio'],
  [
    ['ANY', '/api/orders', ':8080/api/orders', 'Pedidos: listar y crear'],
    ['ANY', '/api/orders/{proxy+}', ':8080/api/orders/{proxy}', 'Pedidos: detalle y cambio de estado'],
    ['ANY', '/api/catalog/{proxy+}', ':8081/api/catalog/{proxy}', 'Catalogo de productos'],
    ['ANY', '/api/report/{proxy+}', ':8083/api/report/{proxy}', 'Indicadores de gestion'],
    ['ANY', '/api/audit/{proxy+}', ':8084/api/audit/{proxy}', 'Trazabilidad de eventos'],
  ],
  [1.15, 3.1, 3.3, 4.38]);
s.addText('La variable {proxy+} captura los segmentos siguientes y los reenvia al backend, pero exige al menos uno. Por eso Pedidos necesita dos rutas: la coleccion vive en el camino sin segmento adicional. Las cinco rutas llevan el validador de token adjunto.', {
  x: 0.7, y: 5.25, w: 11.93, h: 0.9, fontFace: 'Calibri', fontSize: 12.5, color: '667377', isTextBox: true, margin: 0,
});
ubicacion(s, 'API Gateway > pedidos360-api > Routes  y  > Integrations');
nota(s, 'Mostrar en vivo la lista de rutas y abrir una para ensenar su integracion y su authorizer.');

s = slideTabla('CORS en el API Manager', 'Valores configurados para que el navegador pueda llamar a la API',
  ['Cabecera', 'Valor configurado', 'Por que ese valor'],
  [
    ['Access-Control-Allow-Origin', 'https://<ip-apps>:8085', 'Solo el origen del frontend, no un comodin'],
    ['Access-Control-Allow-Headers', 'authorization, content-type', 'Lo minimo: la credencial y el tipo de contenido'],
    ['Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS', 'Los metodos que la aplicacion realmente usa'],
  ],
  [3.5, 4.3, 4.13]);
s.addText('El frontend y el Gateway estan en origenes distintos, asi que el navegador envia primero una peticion de verificacion con el metodo OPTIONS. Esa peticion viaja sin credencial, de modo que las rutas deben permitirla sin exigir token; el resto de los metodos si pasa por el validador. Los origenes se declaran de forma explicita para no conceder permisos de mas.', {
  x: 0.7, y: 4.35, w: 11.93, h: 1.3, fontFace: 'Calibri', fontSize: 12.5, color: '667377', isTextBox: true, margin: 0,
});
ubicacion(s, 'API Gateway > pedidos360-api > CORS  ·  backend: ALLOWED_ORIGINS en infra/apps/.env');
nota(s, 'Mostrar la pantalla de CORS con los valores y, en DevTools, la peticion OPTIONS previa a la llamada real.');

/* ---------- FASE 5 ---------- */
s = slideFase('5', 'Verificacion', 'Comprobar el negocio y la seguridad',
  ['El flujo completo de un pedido atravesando los cinco servicios',
   'Las respuestas esperadas ante token ausente, invalido y sin permisos',
   'Comandos de verificacion en README.md > 8. Verificacion']);

s = slidePasos('Prueba del flujo de negocio', 'Un pedido recorriendo todo el sistema',
  [
    { t: 'Inicio de sesion y obtencion del token', d: 'El usuario se autentica contra Azure AD y regresa con un token que incluye sus roles y el permiso de la API.',
      r: 'Aplicacion > pantalla de inicio de sesion' },
    { t: 'Creacion del pedido', d: 'Un usuario con rol de cliente crea el pedido. El servicio consulta el precio al catalogo, publica el evento y encola la notificacion.',
      r: 'Aplicacion > Pedidos > Nuevo pedido' },
    { t: 'Recorrido por los estados', d: 'Un usuario con rol de operador o administrador lleva el pedido por aceptado, en preparacion, despachado y entregado. Al aceptar se descuenta el stock.',
      r: 'Aplicacion > Pedidos > detalle > Cambiar estado' },
    { t: 'Confirmacion en los consumidores', d: 'Notificaciones procesa cada comando, auditoria registra cada evento con su actor, y reporteria actualiza sus indicadores.',
      r: 'ec2-apps: sudo docker compose logs ms-notify | ms-audit | ms-report' },
    { t: 'Revision en la interfaz', d: 'La pantalla de auditoria muestra la linea de tiempo completa y la de reportes calcula el tiempo de ciclo del pedido entregado.',
      r: 'Aplicacion > Auditoria  y  Aplicacion > Reportes' },
  ]);
nota(s, 'En los registros se sigue el mismo identificador de correlacion a traves de los cuatro servicios.');

s = slideTabla('Evidencia de los controles de seguridad', 'Resultados obtenidos contra el sistema desplegado',
  ['Situacion', 'Resultado', 'Que demuestra'],
  [
    ['Peticion sin token', '401', 'El Gateway exige credencial antes de enrutar'],
    ['Peticion con token inventado', '401', 'Se valida la firma, no solo la presencia del token'],
    ['Token valido, rol insuficiente', '403', 'La autorizacion por rol se aplica en el microservicio'],
    ['Token valido con el rol correcto', '200', 'El flujo completo funciona de extremo a extremo'],
    ['Cliente consultando un pedido ajeno', '404', 'Se verifica la pertenencia sin revelar que existe'],
  ],
  [4.2, 1.6, 6.13]);
ubicacion(s, 'README.md > 8. Verificacion  ·  DevTools > Network  ·  config/SecurityConfig.java');

s = slideTabla('Respuesta de cada ruta', 'Comportamiento verificado endpoint por endpoint',
  ['Ruta', 'Sin token', 'Con token y rol correcto', 'Con rol insuficiente'],
  [
    ['/api/orders', '401', '200, lista de pedidos en JSON', 'Cliente ve solo los suyos'],
    ['/api/orders/{id}', '401', '200, pedido con sus lineas', '404 si es de otro cliente'],
    ['/api/catalog/products', '401', '200, lista de productos', '403 al crear sin rol Admin'],
    ['/api/report/kpis', '401', '200, indicadores de gestion', '403 sin rol Admin'],
    ['/api/audit/events', '401', '200, eventos de auditoria', '403 sin Admin ni Auditor'],
  ],
  [2.9, 1.5, 4.1, 3.43]);
s.addText('Cada ruta devuelve el JSON del microservicio correspondiente, lo que confirma que el Gateway enruta al destino correcto y no a otro. El 404 del detalle es deliberado: responder 403 revelaria que ese pedido existe y pertenece a otra persona.', {
  x: 0.7, y: 5.2, w: 11.93, h: 0.95, fontFace: 'Calibri', fontSize: 12.5, color: '667377', isTextBox: true, margin: 0,
});
ubicacion(s, 'DevTools > Network  ·  ms-pedidos360-*/src/main/java/.../web/*Controller.java');
nota(s, 'Recorrer la aplicacion con DevTools abierto y mostrar cada llamada saliendo hacia el dominio del Gateway.');

s = slideDosColumnas('Problemas encontrados y su solucion', 'Los que costaron tiempo y no son evidentes en ninguna guia',
  {
    t: 'Identidad y acceso',
    items: [
      'El token llegaba en version 1, cuyo emisor no coincidia: se forzo la version 2 en el manifiesto.',
      'El inicio de sesion no pedia el permiso de la API, asi que el token era para Microsoft Graph y llegaba sin roles.',
      'Tras autenticarse, la libreria recargaba el navegador hacia la pantalla de inicio, pisando la navegacion al panel.',
      'La verificacion previa del navegador viajaba sin credencial y el validador la rechazaba.',
    ],
  },
  {
    t: 'Datos y mensajeria',
    items: [
      'El sobre de los mensajes usaba un tipo generico y el contenido llegaba sin convertir, rompiendo a los consumidores.',
      'Los destinos de descarte no existian y el reintento quedaba en un ciclo infinito.',
      'Un campo de texto largo se almacenaba como objeto grande y no podia leerse fuera de una transaccion.',
      'El detalle de un pedido no traia sus lineas y fallaba al construir la respuesta.',
    ],
  });
ubicacion(s, 'README.md > Configuracion critica que costo encontrar  ·  infra/docs/checklist-reinicio-lab.md');

s = slideDosColumnas('Limitaciones conocidas', 'Declaradas de forma deliberada, no por descuido',
  {
    t: 'Del entorno academico',
    items: [
      'El laboratorio expira cada cuatro horas y al reiniciarlo cambian las direcciones publicas, lo que obliga a reconfigurar cuatro puntos.',
      'El certificado es autofirmado, de modo que el navegador advierte antes de entrar.',
      'La base de datos permite que el esquema se ajuste al arrancar, practico aqui pero no recomendable en produccion.',
    ],
  },
  {
    t: 'De alcance',
    items: [
      'El envio real de correos queda como punto de extension: el caso pide que la notificacion sea asincrona por cola, y eso es lo que se demuestra.',
      'El grupo de seguridad de aplicaciones esta abierto, de modo que el Gateway puede evitarse; lo que sostiene la seguridad es que cada servicio revalida el token.',
      'El alta de usuarios es administrada, propia de un directorio organizacional.',
    ],
  });
ubicacion(s, 'infra/docs/checklist-reinicio-lab.md  ·  notify/messaging/EmailNotificationListener.java');

// Cierre
s = slidePortada(
  'Sistema completo',
  'desplegado y verificado',
  'Identidad, red, datos, mensajeria, microservicios, frontend y puerta de enlace, funcionando de extremo a extremo',
  'Repositorio: github.com/Gouramichelle/pedidos360',
);
nota(s, 'Cierre: la documentacion completa de despliegue esta en el README del repositorio.');

pres.writeFile({ fileName: 'Pedidos360-Presentacion.pptx' })
  .then((f) => console.log('Generado:', f));
