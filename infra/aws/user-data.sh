#!/bin/bash
# User data de las instancias: deja Docker y Compose listos al primer arranque.
# Lo consume provisionar.sh, que lo codifica en base64.
set -x

dnf update -y
dnf install -y docker git
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user

# Compose v2 como plugin del CLI.
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Amazon Linux 2023 trae un buildx anterior al que exige `docker compose build`,
# asi que se actualiza. Sin esto la construccion de las imagenes falla.
VER=$(curl -s https://api.github.com/repos/docker/buildx/releases/latest \
      | grep -o '"tag_name": "[^"]*"' | cut -d'"' -f4)
curl -fSL "https://github.com/docker/buildx/releases/download/${VER}/buildx-${VER}.linux-amd64" \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx

# Marca para que desplegar.sh sepa que la instancia termino de prepararse.
touch /home/ec2-user/.docker-listo
