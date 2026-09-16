# AWS Setup — Pedidos360 (Ajustado para AWS Academy Learner Lab)

> Reemplaza la sección de IAM/cuenta de la guía anterior. El resto (VPC, Security Groups, RDS, EC2, API Gateway) se mantiene casi igual, con los ajustes marcados abajo.

## 0. Cómo entrar cada vez que trabajen

1. En el curso de Canvas/AWS Academy → Módulos → **Laboratorio de aprendizaje de AWS Academy** → **Iniciar el Laboratorio de aprendizaje de AWS Academy**.
2. Click **Start Lab** (arriba a la derecha). Esperen a que el punto junto a "AWS" se ponga verde (puede tardar 1-3 min).
3. Click en **AWS** (el texto/ícono verde, al lado de "Start Lab") para abrir la **AWS Management Console** ya autenticada — no necesitan usuario/contraseña propios.
4. Si necesitan el AWS CLI desde su máquina local (no siempre necesario, la consola alcanza para todo lo de este roadmap): click **AWS Details → Show** y copien `aws_access_key_id`, `aws_secret_access_key` y `aws_session_token` a su `~/.aws/credentials`. **Estas credenciales expiran cuando termina la sesión del lab** — hay que repetir este paso cada vez que retomen el trabajo.
5. **Antes de cerrar:** no hace falta apagar nada manualmente; cuando el timer llega a 0:00 el lab detiene las instancias EC2 automáticamente (los datos en EBS/RDS se mantienen). Al volver, repitan el paso 2 y **reinicien manualmente las instancias EC2 que quedaron "stopped"** (RDS normalmente vuelve a estar disponible sola, EC2 no siempre).

**No intenten crear usuarios IAM ni políticas/roles nuevos** — el lab no lo permite y no lo necesitan. Ya tienen permisos amplios con el usuario que entrega el lab.

---

## 1. Región

Confirmen la región permitida en el link **"Region restriction"** del panel derecho del Learner Lab (normalmente **us-east-1**, a veces también us-west-2). Usen esa región en la esquina superior derecha de la consola AWS **en todos los pasos siguientes**, y no la cambien durante el proyecto.

---

## 2. VPC y Subnets

Igual que antes: usen la **VPC default** de la región permitida (el lab ya la trae creada). Verifiquen en **VPC → Your VPCs** y **VPC → Subnets** que existan subnets públicas con auto-assign IP habilitado. No necesitan crear nada acá.

---

## 3. Security Groups

AWS reserva el prefijo `sg-` para los IDs internos de los security groups y
rechaza nombres que empiecen así — se usa el prefijo `seg-` en su lugar.
Ninguno de los dos documentos del proyecto exige un nombre específico, así
que este cambio no afecta nada de lo evaluado.

| Security Group | Reglas de entrada |
|---|---|
| `seg-ssh` | `SSH (22)` desde `My IP` |
| `seg-apps` | `Custom TCP 8080-8085` desde `0.0.0.0/0` (por ahora) |
| `seg-mq` | `5672` y `15672` desde `seg-apps` |
| `seg-kafka` | `9092` desde `seg-apps`, `2181` desde sí mismo |
| `seg-rds` | `PostgreSQL (5432)` desde `seg-apps` |

---

## 4. RDS PostgreSQL

Igual que antes (`db.t3.micro`, engine PostgreSQL, `pedidos360-db`), con un detalle: en el lab a veces las clases de instancia RDS disponibles están acotadas — si `db.t3.micro` no aparece en el selector, prueben `db.t2.micro` o revisen "Service usage and other restrictions" en el panel del lab para ver qué clases están habilitadas.

---

## 5. EC2 — instancias (AJUSTADO: AMI y key pair)

**AMI:** en vez de Ubuntu, usen **Amazon Linux 2023** (casi siempre disponible en Academy; Ubuntu puede no estar en el catálogo permitido). Cambia el gestor de paquetes de `apt` a `dnf`.

**Key pair:** revisen si ya existe una llamada **`vockey`** en EC2 → Key Pairs (Academy suele pre-crearla en `us-east-1`). Si existe, úsenla — así no tienen que manejar un `.pem` nuevo. Si no existe, creen una propia igual que antes.

**Instance profile (opcional):** si algún microservicio necesita llamar a otro servicio AWS (ej. SES para emails reales, S3), asignen el rol **`LabInstanceProfile`** ya existente al lanzar la instancia (EC2 → Advanced details → IAM instance profile). Para lo que necesitamos ahora (Docker Compose con los microservicios) no es obligatorio.

Lanzamiento (**EC2 → Launch instance**), repetido para `ec2-apps` y `ec2-mq-kafka`:

1. AMI: **Amazon Linux 2023**.
2. Instance type: `t3.small` (si el lab restringe tipos, prueben `t2.micro`/`t3.micro` como fallback).
3. Key pair: `vockey` (si existe) o la que creen.
4. Network: VPC default, subnet pública, auto-assign IP habilitado, Security Groups correspondientes.
5. IAM instance profile: `LabInstanceProfile` (opcional, ver arriba).
6. **User data** (ajustado para Amazon Linux / dnf en vez de apt):

```bash
#!/bin/bash
dnf update -y
dnf install -y docker
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user

# docker compose plugin
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

7. Launch. SSH de verificación (usuario `ec2-user`, no `ubuntu`):

```bash
chmod 400 vockey.pem   # o el nombre de su key
ssh -i vockey.pem ec2-user@<IP-PUBLICA-EC2>
docker --version
docker compose version
```

---

## 6. API Gateway — esqueleto

Sin cambios respecto a la guía anterior: crear el HTTP API `pedidos360-api`, rutas placeholder, stage `$default` con auto-deploy. El JWT Authorizer se completa en la sesión de Azure AD.

---

## 7. Checklist ajustado

- [ ] Confirmada la región permitida (Region restriction del lab) y usada consistentemente.
- [ ] VPC default y subnets verificadas (sin crear nada nuevo).
- [ ] 5 Security Groups creados (`seg-ssh`, `seg-apps`, `seg-mq`, `seg-kafka`, `seg-rds`).
- [ ] RDS `pedidos360-db` disponible, endpoint copiado (verificar clase de instancia disponible en el lab).
- [ ] `ec2-apps` y `ec2-mq-kafka` corriendo con **Amazon Linux 2023**, Docker + Compose verificados por SSH.
- [ ] Key pair `vockey` (o propia) descargada y fuera del repo.
- [ ] API Gateway `pedidos360-api` creado con Invoke URL.
- [ ] Entendido el ciclo de sesión: **Start Lab → trabajar → al volver, reiniciar instancias EC2 detenidas y refrescar credenciales CLI si las usan.**

---

## Nota importante para la entrega final

Como el Learner Lab es un sandbox temporal, **las IPs públicas de las EC2 pueden cambiar si las instancias se detienen y reinician** (a menos que asignen una Elastic IP, que en algunos labs Academy también está limitada). Si notan que la IP cambia entre sesiones, tienen dos opciones:
1. Actualizar manualmente las integraciones del API Gateway y las variables de entorno del frontend cada vez que cambie.
2. Intentar reservar una **Elastic IP** (EC2 → Elastic IPs → Allocate) y asociarla a cada instancia — revisen si el lab lo permite (a veces cuenta contra un límite de recursos).

Recomendación: antes de la demo/presentación final, hagan un **"Start Lab" con margen de tiempo** y verifiquen que todas las IPs sigan siendo las que configuraron en el Gateway y en el frontend.
