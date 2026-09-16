# Pedidos360 — EP1 (DSY1107)

Monorepo con los 6 componentes del encargo: el frontend Angular, los 5
microservicios Spring Boot y la infraestructura de despliegue.

```
frontend-pedidos360/     Angular 18 + MSAL
ms-pedidos360-orders/    Spring Boot 3 — pedidos, maquina de estados, productor Kafka/RabbitMQ
ms-pedidos360-catalog/   Spring Boot 3 — catalogo de productos y stock
ms-pedidos360-notify/    Spring Boot 3 — consumer RabbitMQ, sin DB, sin API publica
ms-pedidos360-report/    Spring Boot 3 — consumer Kafka, KPIs de solo lectura
ms-pedidos360-audit/     Spring Boot 3 — consumer Kafka, auditoria de solo lectura
infra/                   docker-compose para apps, RabbitMQ y Kafka+Zookeeper
```

## Despliegue

- **[infra/docs/despliegue.md](infra/docs/despliegue.md)** — guía completa desde
  cero: Azure AD, red y Security Groups, RDS, EC2, mensajería, microservicios,
  frontend y API Gateway, con la verificación final.
- **[infra/docs/checklist-reinicio-lab.md](infra/docs/checklist-reinicio-lab.md)** —
  qué reconfigurar cuando expira la sesión del laboratorio y cambian las IPs.

## Por que no hay BFF

El caso original propone `ms-pedidos360-bff` como capa intermedia entre el
frontend y los microservicios. En esta implementacion se elimina y su
responsabilidad de validacion de borde se reparte en dos puntos que ya
cumplen exactamente el mismo proposito:

1. **AWS API Gateway (HTTP API) con JWT Authorizer**: valida firma, issuer y
   audience del token de Azure AD antes de enrutar la peticion a cualquier
   microservicio. Cumple el rol de "API Manager" del diagrama original.
2. **Spring Security en cada microservicio** (`SecurityConfig` +
   `AudienceValidator`, identicos en los 5 servicios): vuelve a validar firma,
   issuer, expiracion y audience de forma independiente, y aplica
   autorizacion por rol (`@PreAuthorize`) en cada endpoint segun el claim
   `roles` del token.

Agregar un BFF entre ambos puntos duplicaria una validacion que ya se hace
dos veces (Gateway + microservicio) sin sumar una tercera garantia distinta:
un BFF no le agrega informacion al chequeo, solo lo repite una vez mas antes
de reenviar la misma peticion. La superficie de ataque que un BFF reduciria
(exponer los microservicios directamente) ya la resuelve el Security Group
`sg-apps`, que en produccion deberia restringirse a las IPs del API Gateway.

**Riesgo asumido:** el indicador de la pauta EP1 nombra explicitamente
"BFF" en su categoria de logro. Se interpreta ese indicador como "capa de
validacion de borde equivalente al API Manager", cubierta por el par
Gateway + Spring Security descrito arriba. Se recomienda confirmar esta
interpretacion con el/la docente antes del plazo de entrega.

## Roles y su cobertura en el codigo

El caso define los roles `Admin`, `Operador`, `Cliente` en la seccion de
seguridad, y ademas menciona `Auditor` como actor del modulo de auditoria.
Para no dejar `/audit` inalcanzable si `Auditor` no se llega a crear en Azure
AD a tiempo, el endpoint de auditoria acepta tambien `Admin`. Se recomienda
crear el App Role `Auditor` en la App Registration de todas formas.

## Ejecucion local

Ver la seccion "Ejecucion local, sin AWS" de la
[guia de despliegue](infra/docs/despliegue.md).

## Documentacion tecnica

Ver `infra/docs/` para diagramas y decisiones de arquitectura adicionales, y
`01-aws-setup-academy-lab.md` / `roadmap-pedidos360-ep1.md` en la raiz de este
workspace para la guia de despliegue en AWS Academy Learner Lab.
