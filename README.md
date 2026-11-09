# ProtectCore — Ederus Network Edition 🛡️

Núcleo de auditoría, registro de acciones (*logging*), inspección y reversión (*rollback*) de alta fidelidad para los servidores de **Ederus Network**.

---

## 🌟 Novedades y Mejoras Propias (Ederus Edition v5.7)

Esta versión personalizada de **ProtectCore** incorpora múltiples características diseñadas específicamente para el ecosistema de Ederus:

1. **💬 Búsqueda y Registro de Chat en Vivo:**
   - Inspección directa de mensajes desde consola o juego con `/po lookup a:chat [u:jugador] [t:tiempo]` (alias: `a:msg`, `a:message`).
   - Salida formateada con prefijo distintivo `💬 chat`.

2. **⚡ Auditoría de Comandos Ejecutados:**
   - Rastreo de comandos emitidos por jugadores con `/po lookup a:cmd [u:jugador] [t:tiempo]` (alias: `a:command`).
   - Salida formateada con prefijo distintivo `⚡ cmd`.

3. **🔍 Búsqueda Global Sin Filtros:**
   - Soporte para el parámetro `a:all`, permitiendo auditar todas las acciones históricas de forma secuencial (bloques, cofres, chat y comandos).

4. **📱 Soporte Transparente para Jugadores Bedrock:**
   - Resolución automática de UUID y perfiles para jugadores provenientes de Geyser/Floodgate cuyo nombre inicia con `.` (ejemplo: `.AlvaroGaming`, `.JugadorBedrock`).
   - Búsqueda dual: caché de jugadores de Bukkit y fallback directo en la base de datos MariaDB.

5. **🎨 Editor Web Moderno e Identidad Corporativa:**
   - Integración oficial con el dominio seguro [https://editor.ederus.com](https://editor.ederus.com).
   - Generación automática de tokens temporales de acceso mediante `/po editor`.
   - Incorporación del logo oficial de Ederus Network y favicon corporativo en la interfaz web.
   - Pestañas de filtrado en tiempo real, modo claro/oscuro y teletransporte in-game por clic en coordenadas.

---

## 📋 Comandos Principales

El comando base es `/po`:

| Comando | Alias | Descripción |
| :--- | :--- | :--- |
| `/po lookup [parámetros]` | `/po l` | Auditoría histórica por jugador, tiempo, radio, acción o material. |
| `/po inspect` | `/po i` | Activa/desactiva la varita de inspección física en mano. |
| `/po near [radio] [tiempo]` | | Búsqueda rápida de eventos alrededor del jugador. |
| `/po editor [filtros]` | | Genera un enlace seguro a la interfaz web `https://editor.ederus.com`. |
| `/po rollback [parámetros]` | `/po rb` | Deshace acciones destructivas restaurando el mundo y contenedores. |
| `/po status` | | Muestra la salud de MariaDB, colas en memoria y servidor web. |
| `/po reload` | | Recarga la configuración (`config.yml`) y reinicia el servicio web. |
| `/po purge t:<tiempo>` | | Limpieza de registros antiguos de la base de datos. |

> Consulta la [Guía Completa de Comandos](docs/protectcore_guide.md) para ver ejemplos detallados y la tabla completa de parámetros.

---

## 🔐 Permisos (LuckPerms)

- `protectcore.*` — Otorga acceso a todas las funciones del plugin.
- `protectcore.commands` — Permiso básico para ejecutar `/po`.
- `protectcore.inspect` — Uso de la herramienta de inspección interactiva (`/po inspect`).
- `protectcore.near` — Búsqueda de proximidad (`/po near`).
- `protectcore.lookup` — Búsquedas avanzadas (`/po lookup`).
- `protectcore.editor` — Acceso al generador de enlaces del editor web (`/po editor`).
- `protectcore.rollback` — Reversión de cambios (`/po rollback`).
- `protectcore.rollback.bypass` — Ignora restricciones de radio o tamaño en rollback.
- `protectcore.admin.status` — Ver diagnóstico del sistema (`/po status`).
- `protectcore.reload` — Recargar el plugin (`/po reload`).
- `protectcore.admin.purge` — Ejecutar purgas en la base de datos.

---

## 🚀 Requisitos

- **Servidor:** Paper / Purpur 1.21 o superior.
- **Java:** JDK 21+.
- **Base de Datos:** MariaDB 10.6+ / 11.x.
- **Reverse Proxy (Opcional para Editor Web):** Nginx con terminación SSL (Cloudflare Full Strict) apuntando al puerto HTTP interno del plugin.

---

## 🏢 Créditos y Licencia

Desarrollado originalmente por **LucidAPS**. Personalizado y mantenido por el equipo de desarrollo de **Ederus Network** para su infraestructura de producción.
