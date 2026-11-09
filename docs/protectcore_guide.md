# Guía Oficial de Comandos: ProtectCore (`/po`)
### Sistema de Auditoría, Inspección y Reversión de Ederus Network

---

## 📌 Introducción

**ProtectCore** es el núcleo de registro de eventos (*logging*), auditoría e inspección para los servidores de **Ederus Network**. Registra cada interacción, colocación o rotura de bloques, aperturas de cofres, mensajes de chat y ejecución de comandos en una base de datos MariaDB de alto rendimiento.

El comando base es:
```text
/po [subcomando] [parámetros]
```

> [!NOTE]
> Todos los parámetros aceptan sintaxis completa (`user:`, `time:`, `radius:`, `action:`, `include:`, `exclude:`, `page:`) o sus alias abreviados (`u:`, `t:`, `r:`, `a:`, `i:`, `e:`, `p:`).

---

## 🔍 1. Auditoría en Juego (`/po lookup` o `/po l`)

Permite realizar búsquedas históricas filtradas de acciones directamente en el chat del juego o por consola, con una interfaz y presentación idéntica a **CoreProtect**.

### Sintaxis General
```text
/po lookup [u:<jugador>] [t:<tiempo>] [r:<radio>] [a:<acción>] [i:<bloques>] [e:<excluidos>] [p:<página>]
# o mediante su alias rápido:
/po l [parámetros]
```

### Paginación Rápida con Memoria
ProtectCore recuerda tu última búsqueda por jugador. Para cambiar de página no necesitas reescribir todos los filtros:
```text
/po l 2
/po lookup 3
```
*Salta directamente a la página indicada manteniendo intactos todos los filtros anteriores.*

### Parámetros Disponibles

| Parámetro | Abreviatura | Descripción | Valores de Ejemplo |
| :--- | :--- | :--- | :--- |
| `user:<jugador>` | `u:<nombre>` | Jugador o entidad a inspeccionar. Compatible con Java y Bedrock. | `u:Steve`, `u:.JugadorBedrock` |
| `time:<periodo>` | `t:<tiempo>` | Ventana de tiempo hacia atrás. (Por defecto: `1d`). | `t:10m`, `t:1h`, `t:12h`, `t:3d`, `t:2w` |
| `radius:<bloques>`| `r:<número>` | Radio en bloques desde tu posición actual. | `r:5`, `r:15`, `r:50`, `r:global` |
| `action:<tipo>` | `a:<acción>` | Tipo específico de evento a buscar. | `a:chat`, `a:cmd`, `a:block_break`, `a:all` |
| `include:<items>`| `i:<material>`| Filtra sólo por ciertos bloques/ítems separados por comas. | `i:diamond_ore,ancient_debris` |
| `exclude:<items>`| `e:<material>`| Omite bloques/ítems comunes del reporte. | `e:stone,dirt,cobblestone` |
| `page:<número>` | `p:<página>` o `<número>` | Página del resultado (también navegable con ◀ y ▶). | `p:2`, `/po l 2` |

---

### 🎨 Formato Visual Idéntico a CoreProtect

ProtectCore replica con fidelidad el estándar visual de CoreProtect:
- **Tiempos Relativos Dinámicos:** Muestra la antigüedad exacta del evento (`0.05/h ago`, `1.50/h ago`, `2.30/d ago`, `12.50/s ago`). Al pasar el cursor por encima del tiempo, muestra la fecha y hora completa en formato `yyyy-MM-dd HH:mm:ss`.
- **Paleta de Colores Oficial:** Nombres de usuario y materiales en Dark Aqua (`&3`), verbos y conectores en Blanco (`&f`), y símbolos de acción distintivos:
  - `+` en verde (`&a+`): colocación de bloques, depósitos en contenedores.
  - `-` en rojo (`&c-`): rotura de bloques, retiros de contenedores, asesinatos.
  - `-` en gris (`&7-`): interacciones o clics.
  - `>` en amarillo (`&e>`): transferencias o movimientos.
- **Acciones Revertidas (Tachadas):** Las acciones que ya han sido revertidas con `/po rollback` aparecen con formato tachado (`strikethrough`), permitiendo identificar al instante si el daño ya fue solucionado.
- **Coordenadas y Teletransporte con 1 Clic:** En la segunda línea de cada registro se detallan las coordenadas exactas `^ (x/y/z/mundo)` con indicador de acción `(a:action)`. Al hacer clic en las coordenadas te teletransportas automáticamente a la ubicación del suceso.
- **Transacciones de Contenedores con Cantidades:** Visualiza la cantidad exacta de ítems movidos (`x64 oak_planks`, `x1 diamond`) y al pasar el cursor sobre el ítem verás su nombre personalizado, encantamientos y lore.

---

### 💬 Registro de Chat y Comandos

ProtectCore registra el historial conversacional y administrativo en tiempo real con iconos dedicados:

- **Búsqueda de mensajes de chat:**
  ```text
  /po lookup u:Jugador a:chat t:6h
  ```
  *(Alias válidos: `a:chat`, `a:msg`, `a:message`, `a:player_message`)*  
  *Salida:* `0.10/h ago - Steve: Hola a todos!`

- **Búsqueda de comandos ejecutados:**
  ```text
  /po lookup u:Jugador a:cmd t:1d
  ```
  *(Alias válidos: `a:cmd`, `a:command`, `a:player_command`)*  
  *Salida:* `0.15/h ago - Steve: /pay Pedro 5000`

- **Ver absolutamente todo (sin exclusiones):**
  ```text
  /po lookup u:Jugador a:all t:30m
  ```

---

### 📱 Soporte Especial para Jugadores Bedrock

Los jugadores de Bedrock acceden a través de Geyser/Floodgate con el prefijo de punto (`.`):

- Puedes buscar directamente usando su nombre con punto:
  ```text
  /po lookup u:.AlvaroGaming a:chat t:2d
  /po lookup u:.CarlosMC r:20 t:1h
  ```
- ProtectCore resolverá de forma automática su UUID tanto desde el caché del servidor como desde el registro histórico de MariaDB.

---

### 📋 Tipos de Acciones Disponibles (`action:`)

| Acción | Alias / Atajo | Qué registra |
| :--- | :--- | :--- |
| `BLOCK_BREAK` | `break` | Bloques rotos |
| `BLOCK_PLACE` | `place` | Bloques colocados |
| `CONTAINER_ADD` | `add` | Ítems depositados en cofres, shulkers o barriles |
| `CONTAINER_TAKE` | `take` | Ítems retirados de cofres o contenedores |
| `INTERACT` | `interact`, `click` | Apertura de puertas, palancas, cofres, botones |
| `ITEM_PICKUP` | `pickup` | Ítems recogidos del suelo |
| `ITEM_DROP` | `drop` | Ítems arrojados al suelo |
| `BUCKET_EMPTY` | `bucket` | Vaciado de cubos (agua, lava) |
| `PLAYER_MESSAGE` | `chat`, `msg` | Mensajes enviados al chat global o local |
| `PLAYER_COMMAND` | `cmd` | Comandos ejecutados |
| `BLOCK_EXPLODE` | `explode` | Explosiones de TNT, Creepers, etc. |
| `ENTITY_KILL` | `kill` | Asesinato de entidades o jugadores |
| `ALL` | `all` | Muestra todo sin filtrar |

---

## 🔎 2. Modo Inspector en Mano (`/po inspect` o `/po i`)

Activa o desactiva la herramienta de inspección interactiva sobre el terreno.

```text
/po inspect
# o de forma abreviada:
/po i
```

### Mecánicas de Clic:
1. **Clic Izquierdo en un bloque:** Muestra el historial de quién colocó o rompió ese bloque exacto.
   - Encabezado: `----- ProtectCore ----- (x/y/z)`
2. **Clic Derecho en un Contenedor (Cofre, Shulker, Barril):** Muestra exclusivamente las transacciones de ítems depositados y retirados.
   - Encabezado dedicado: `----- Container Transactions ----- (x/y/z)`
   - Formato de ítems: `x64 oak_planks`, `x1 netherite_sword` (con tooltip interactivo de encantamientos y lore al pasar el cursor).
3. **Clic Derecho en Bloque Sólido:** Inspecciona automáticamente el bloque de aire adyacente contra la cara seleccionada (`block.getRelative(face)`). De esta forma se detectan bloques rotos o colocados sobre esa superficie sin necesidad de romper la pared o suelo.
4. Escribe nuevamente `/po i` para desactivar el modo inspector.

---

## 📍 3. Auditoría de Proximidad Rápida (`/po near`)

Busca rápidamente eventos ocurridos cerca de ti en un radio determinado sin necesidad de escribir múltiples parámetros:

```text
/po near [radio] [tiempo]
```
**Ejemplo:**
```text
/po near 10 30m
```
*Muestra los últimos eventos ocurridos en un radio de 10 bloques durante los últimos 30 minutos.*

---

## 🌐 4. Editor Web Moderno (`/po editor`)

ProtectCore incluye una interfaz gráfica moderna accesible vía navegador web bajo el dominio oficial:
🔗 **`https://editor.ederus.com`**

### Generar Enlace de Acceso
Para generar una sesión segura con token temporal (expira a los 60 minutos):
```text
/po editor
```
*En el chat de Minecraft aparecerá un mensaje interactivo:*
> **[ProtectCore] ¡Haz clic aquí para abrir el editor!**

Al hacer clic, se abrirá tu navegador en:
`https://editor.ederus.com/editor?token=<TOKEN_DE_SESIÓN>&since=0`

### Filtros Directos al Generar el Enlace
Puedes generar el enlace con filtros ya precargados:
```text
/po editor user:Steve time:1d
/po editor user:.BedrockUser time:6h
```

### Funciones del Panel Web:
- **Identidad Ederus:** Con el logo oficial y favicon corporativo.
- **Buscador en tiempo real:** Filtra por jugador, rango de fechas, material, encantamiento o *lore*.
- **Pestañas por acción:** *Block Break, Block Place, Container Add, Container Take, Player Command, Player Message, etc.*
- **Teletransporte con 1 clic:** Haz clic en las coordenadas `(X, Y, Z)` de un registro para teletransportarte directamente en el servidor.
- **Modo Claro / Oscuro.**

---

## ⏪ 5. Reversión de Cambios (`/po rollback`)

Deshace acciones dañinas (*griefing*, robos, explosiones) restaurando el estado previo del mundo.

> [!WARNING]
> Los rollbacks modifican el mundo en tiempo real. Utiliza siempre radios acotados (`r:15`) y rangos de tiempo prudentes para evitar alterar construcciones legítimas.

### Sintaxis
```text
/po rollback u:<jugador> t:<tiempo> [r:<radio>] [a:<acción>] [i:<incluir>] [e:<excluir>]
```

### Ejemplos Prácticos de Rollback

1. **Revertir roturas y colocaciones de un usuario en un radio:**
   ```text
   /po rollback u:Griefer t:2h r:20
   ```
2. **Revertir robos de cofres (*container take*):**
   ```text
   /po rollback u:Ladron t:6h r:15 a:container_take
   ```
3. **Revertir solo lava y fuego colocados:**
   ```text
   /po rollback u:Griefer t:1h r:30 i:lava,fire
   ```
4. **Rollback Global de un usuario:**
   Si no se especifica radio, ProtectCore aplicará el rollback en todo el mundo.
   ```text
   /po rollback u:Hacker t:1h
   ```
   > [!CAUTION]
   > El servidor te pedirá confirmación por seguridad. Debes repetir el comando en menos de 15 segundos para confirmar la operación global.

---

## 🛠️ 6. Comandos Administrativos y Mantenimiento

### Estado del Sistema (`/po status`)
Muestra la salud de la base de datos MariaDB, la cola de eventos en memoria y el estado del servidor web:
```text
/po status
```

### Recargar Configuración (`/po reload`)
Aplica cambios en `config.yml`, mensajes y reinicia el servicio HTTP del editor web sin reiniciar el servidor:
```text
/po reload
```

### Purgar Registros Antiguos (`/po purge`)
Limpia registros antiguos de la base de datos para optimizar espacio en disco:
```text
/po purge t:60d
```
*(Elimina registros con más de 60 días de antigüedad).*

---

## 🔐 7. Tabla de Permisos (LuckPerms)

| Permiso | Descripción | Grupo Recomendado |
| :--- | :--- | :--- |
| `protectcore.commands` | Acceso al comando base `/po` | Staff / Admin |
| `protectcore.inspect` | Uso de `/po inspect` (Wand) | Helper, Mod, Admin |
| `protectcore.near` | Uso de `/po near` | Helper, Mod, Admin |
| `protectcore.lookup` | Búsquedas avanzadas (`/po lookup`) | Mod, Admin |
| `protectcore.editor` | Generar enlaces web (`/po editor`) | Mod, Admin |
| `protectcore.rollback` | Reversión de eventos (`/po rollback`) | Admin / SrMod |
| `protectcore.rollback.bypass` | Ignora límites de tamaño/radio en rollback | Admin |
| `protectcore.admin.status` | Ver estado y rendimiento (`/po status`) | Admin / Developer |
| `protectcore.reload` | Recargar el plugin (`/po reload`) | Developer / Owner |
| `protectcore.admin.purge` | Purgar registros antiguos de la BD | Owner |
| `protectcore.*` | Todos los permisos | Administrador |

---

## 💡 Trucos Rápidos para Staff

1. **¿Sospechas de un robo en una base?**
   - Ve a la base y ejecuta `/po inspect`.
   - Haz clic derecho en el cofre saqueado para ver quién sacó ítems y a qué hora.
2. **¿Alguien insultó o mandó spam en el chat y se perdió el mensaje?**
   - `/po lookup u:Jugador a:chat t:1h`
3. **¿Un usuario usó comandos sospechosos antes de desconectarse?**
   - `/po lookup u:Jugador a:cmd t:3h`
4. **¿Griefing masivo en una zona protegida?**
   - `/po lookup r:20 t:12h` para identificar a los culpables.
   - `/po rollback u:Culpable t:12h r:25` para reparar el daño en segundos.
