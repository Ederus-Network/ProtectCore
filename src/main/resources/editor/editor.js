// ─── manual overrides for icon names ────────────────────────────────────
const ICON_OVERRIDES = {
  redstone_wire:    'redstone',
  carrots:          'carrot',
  potatoes:         'potato',
  wall_torch:       'torch',
  soul_wall_torch:  'soul_torch',
  tripwire:         'string',
  beetroots:        'beetroot',
  pumpkin_stem:     'pumpkin',
  melon_stem:       'melon',
  leash_knot:       'lead',
  cave_vines:       'glow_berries',
  fire:             'blaze_powder',
  torchflower_crop: 'torchflower',
  sweet_berry_bush: 'sweet_berries',
  player_wall_head: 'player_head'
};

function getIconFilename(materialId) {
  if (!materialId) return null;
  let name = materialId.replace(/^minecraft:/, '');
  if (ICON_OVERRIDES[name]) {
    return ICON_OVERRIDES[name] + '.png';
  }
  if (name.endsWith('_wall_hanging_sign')) {
    return name.replace('_wall_hanging_sign', '_hanging_sign') + '.png';
  }
  if (name.endsWith('_wall_sign')) {
    return name.replace('_wall_sign', '_sign') + '.png';
  }
  return name + '.png';
}

function getEntityIconFilename(entityId) {
  let name = entityId.replace(/^minecraft:/, '');
  return `mobs/${name}.png`;
}

const ALL_ACTIONS =[
  { id: 'all', label: 'All' },
  ...MASTER_ACTIONS
    .filter(a => !DISABLED_ACTIONS.includes(a))
    .map(a => ({ id: a.toLowerCase().replace(/_/g, '-'), label: a }))
];

/* ✱ PER-ACTION PAGING STATE */
const finished = Object.fromEntries(ALL_ACTIONS.map(a => [a.id, false]));
const lastTimestamps = Object.fromEntries(ALL_ACTIONS.map(a => [a.id, 0]));
const lastIds = Object.fromEntries(ALL_ACTIONS.map(a => [a.id, 0]));

const itemsPerPage   = 30;

/* Misc flags */
let currentAction = 'all';
let isLoading     = false;
/* ✱ request-nonce – detects stale async results */
let requestNonce  = 0;        // monotonically increasing
let currentController;
let requestFailed = false;
let accessExpired = false;
const loadedCounts = Object.fromEntries(ALL_ACTIONS.map(a => [a.id, 0]));

const MINECRAFT_COLORS = {
  '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA',
  '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA',
  '8': '#555555', '9': '#5555FF', 'a': '#55FF55', 'b': '#55FFFF',
  'c': '#FF5555', 'd': '#FF55FF', 'e': '#FFFF55', 'f': '#FFFFFF'
};

const NAMED_COLORS = {
  black: '#000000',
  dark_blue: '#0000AA',
  dark_green: '#00AA00',
  dark_aqua: '#00AAAA',
  dark_red: '#AA0000',
  dark_purple: '#AA00AA',
  gold: '#FFAA00',
  gray: '#AAAAAA',
  grey: '#AAAAAA',
  dark_gray: '#555555',
  dark_grey: '#555555',
  blue: '#5555FF',
  green: '#55FF55',
  aqua: '#55FFFF',
  red: '#FF5555',
  light_purple: '#FF55FF',
  yellow: '#FFFF55',
  white: '#FFFFFF'
};

function parseHex(hexStr) {
  let h = hexStr.replace(/^#/, '');
  if (h.length === 3) {
    h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
  }
  if (h.length !== 6) return null;
  const num = parseInt(h, 16);
  if (isNaN(num)) return null;
  return {
    r: (num >> 16) & 255,
    g: (num >> 8) & 255,
    b: num & 255,
    hex: '#' + h.toLowerCase()
  };
}

function colorToRgb(colorStr) {
  if (!colorStr) return null;
  colorStr = colorStr.trim().toLowerCase();
  if (NAMED_COLORS[colorStr]) {
    return parseHex(NAMED_COLORS[colorStr]);
  }
  return parseHex(colorStr);
}

function interpolateRgb(c1, c2, factor) {
  const r = Math.round(c1.r + factor * (c2.r - c1.r));
  const g = Math.round(c1.g + factor * (c2.g - c1.g));
  const b = Math.round(c1.b + factor * (c2.b - c1.b));
  return `rgb(${r}, ${g}, ${b})`;
}

function evaluateMultiStopGradient(stops, factor) {
  if (stops.length === 0) return '#FFFFFF';
  if (stops.length === 1 || factor <= 0) return stops[0].hex || `rgb(${stops[0].r},${stops[0].g},${stops[0].b})`;
  if (factor >= 1) return stops[stops.length - 1].hex || `rgb(${stops[stops.length-1].r},${stops[stops.length-1].g},${stops[stops.length-1].b})`;

  const segmentLength = 1 / (stops.length - 1);
  const index = Math.min(Math.floor(factor / segmentLength), stops.length - 2);
  const localFactor = (factor - (index * segmentLength)) / segmentLength;
  return interpolateRgb(stops[index], stops[index + 1], localFactor);
}

function hslToRgb(h, s, l) {
  let r, g, b;
  if (s === 0) {
    r = g = b = l;
  } else {
    const hue2rgb = (p, q, t) => {
      if (t < 0) t += 1;
      if (t > 1) t -= 1;
      if (t < 1/6) return p + (q - p) * 6 * t;
      if (t < 1/2) return q;
      if (t < 2/3) return p + (q - p) * (2/3 - t) * 6;
      return p;
    };
    const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
    const p = 2 * l - q;
    r = hue2rgb(p, q, h + 1/3);
    g = hue2rgb(p, q, h);
    b = hue2rgb(p, q, h - 1/3);
  }
  return `rgb(${Math.round(r * 255)}, ${Math.round(g * 255)}, ${Math.round(b * 255)})`;
}

function preprocessMiniMessage(text) {
  if (!text || typeof text !== 'string') return '';

  text = text.replace(/<gradient:([^>]+)>(.*?)<\/gradient>/gis, (match, colorList, content) => {
    const colorNames = colorList.split(':');
    const stops = [];
    for (const cn of colorNames) {
      const rgb = colorToRgb(cn);
      if (rgb) stops.push(rgb);
    }
    if (stops.length === 0) return content;

    const chars = Array.from(content);
    if (chars.length === 0) return '';
    if (chars.length === 1) return `<span style="color:${stops[0].hex}">${chars[0]}</span>`;

    let res = '';
    const total = chars.length;
    chars.forEach((char, idx) => {
      const factor = idx / (total - 1);
      const color = evaluateMultiStopGradient(stops, factor);
      res += `<span style="color:${color}">${char}</span>`;
    });
    return res;
  });

  text = text.replace(/<rainbow>(.*?)<\/rainbow>/gis, (match, content) => {
    const chars = Array.from(content);
    if (chars.length === 0) return '';
    let res = '';
    const total = chars.length;
    chars.forEach((char, idx) => {
      const h = idx / Math.max(total, 1);
      const color = hslToRgb(h, 0.9, 0.6);
      res += `<span style="color:${color}">${char}</span>`;
    });
    return res;
  });

  return text;
}

function isMsgOrCmd(actionId) {
  return actionId === 'player-message' || actionId === 'player-command';
}

function setGroupVisible(id, visible) {
  const el = document.getElementById(id);
  if (!el) return;
  el.style.display = visible ? '' : 'none';
}

function updateFilterVisibility(actionId) {
  const msg = isMsgOrCmd(actionId);

  // Hide item-related filters on message/command tabs
  setGroupVisible('materialGroup', !msg);
  setGroupVisible('nameGroup', !msg);
  setGroupVisible('enchantGroup', !msg);
  setGroupVisible('loreGroup', !msg);

  // Show text search only on message/command tabs
  setGroupVisible('textGroup', msg);

    if (msg) {
      ['itemFilter', 'nameFilter', 'loreFilter'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
      });
      document.querySelectorAll('.enchant-checkbox:checked').forEach(cb => cb.checked = false);
    } else {
      const tf = document.getElementById('textFilter');
      if (tf) tf.value = '';
    }
}

function escapeHtml(value) {
  if (value == null) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function translateColorCodesSafe(input) {
  if (!input) return '';

  let text = preprocessMiniMessage(String(input));
  let output = '';
  const closeStack = [];

  const closeAll = () => {
    while (closeStack.length) {
      output += closeStack.pop();
    }
  };

  const openSpan = (style) => {
    output += `<span style="${style}">`;
    closeStack.push('</span>');
  };

  const openTag = (tag) => {
    output += `<${tag}>`;
    closeStack.push(`</${tag}>`);
  };

  const len = text.length;
  let i = 0;

  while (i < len) {
    const ch = text[i];

    // Check for Section (§) or Ampersand (&) formatting
    if (ch === '§' || ch === '&') {
      const remaining = text.slice(i);

      // 1. Bungee/Spigot Hex format: §x§r§r§g§g§b§b or &x&r&r&g&g&b&b (any case)
      const bungeeHexMatch = remaining.match(/^([§&]x(?:[§&][0-9a-fA-F]){6})/i);
      if (bungeeHexMatch) {
        const raw = bungeeHexMatch[1];
        const hex = '#' + raw.replace(/[§&x]/gi, '');
        closeAll();
        openSpan(`color:${hex}`);
        i += raw.length;
        continue;
      }

      // 2. Standard Hex format: &#RRGGBB or §#RRGGBB or &[#RRGGBB]
      const hexMatch = remaining.match(/^[§&](?:#|\[#)([0-9a-fA-F]{6})\]?/i);
      if (hexMatch) {
        const hex = '#' + hexMatch[1];
        closeAll();
        openSpan(`color:${hex}`);
        i += hexMatch[0].length;
        continue;
      }

      // 3. Single legacy code: §0-§f, §l, etc.
      if (i + 1 < len) {
        const code = text[i + 1].toLowerCase();
        const color = MINECRAFT_COLORS[code];

        if (color !== undefined) {
          closeAll();
          openSpan(`color:${color}`);
          i += 2;
          continue;
        }

        switch (code) {
          case 'l':
            openTag('strong');
            i += 2;
            continue;
          case 'o':
            openTag('em');
            i += 2;
            continue;
          case 'n':
            openTag('u');
            i += 2;
            continue;
          case 'm':
            openTag('del');
            i += 2;
            continue;
          case 'k':
            output += '<span class="mc-obfuscated">';
            closeStack.push('</span>');
            i += 2;
            continue;
          case 'r':
            closeAll();
            i += 2;
            continue;
        }
      }
    }

    // Check for curly hex format: {#RRGGBB}
    if (ch === '{') {
      const remaining = text.slice(i);
      const curlyHexMatch = remaining.match(/^\{#([0-9a-fA-F]{6})\}/i);
      if (curlyHexMatch) {
        closeAll();
        openSpan(`color:#${curlyHexMatch[1]}`);
        i += curlyHexMatch[0].length;
        continue;
      }
    }

    // Check for MiniMessage / XML tags: <tag>
    if (ch === '<') {
      const remaining = text.slice(i);

      // Check if this is an already rendered span (from preprocessMiniMessage)
      const renderedSpanMatch = remaining.match(/^<span style="([^"]+)">/i);
      if (renderedSpanMatch) {
        output += renderedSpanMatch[0];
        closeStack.push('</span>');
        i += renderedSpanMatch[0].length;
        continue;
      }
      if (remaining.startsWith('</span>')) {
        if (closeStack.length && closeStack[closeStack.length - 1] === '</span>') {
          output += closeStack.pop();
        } else {
          output += '</span>';
        }
        i += 7;
        continue;
      }

      // Closing tags: </color>, </gradient>, </rainbow>, </b>, </i>, </u>, </s>, </red>, etc.
      const closeTagMatch = remaining.match(/^<\/(color|colour|gradient|rainbow|bold|b|italic|em|i|underlined|u|strikethrough|st|s|obf|obfuscated|reset|r|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white)>/i);
      if (closeTagMatch) {
        if (closeStack.length) {
          output += closeStack.pop();
        }
        i += closeTagMatch[0].length;
        continue;
      }

      // <#RRGGBB> or <color:#RRGGBB>
      const mmHexMatch = remaining.match(/^<(?:color:|colour:)?#([0-9a-fA-F]{6})>/i);
      if (mmHexMatch) {
        const hex = '#' + mmHexMatch[1];
        closeAll();
        openSpan(`color:${hex}`);
        i += mmHexMatch[0].length;
        continue;
      }

      // Formatting tags: <b>, <i>, <u>, <s>, etc.
      const mmFormatMatch = remaining.match(/^<(b|bold|i|italic|em|u|underlined|s|strikethrough|st|obf|obfuscated|reset|r)>/i);
      if (mmFormatMatch) {
        const fTag = mmFormatMatch[1].toLowerCase();
        if (fTag === 'b' || fTag === 'bold') {
          openTag('strong');
        } else if (fTag === 'i' || fTag === 'italic' || fTag === 'em') {
          openTag('em');
        } else if (fTag === 'u' || fTag === 'underlined') {
          openTag('u');
        } else if (fTag === 's' || fTag === 'strikethrough' || fTag === 'st') {
          openTag('del');
        } else if (fTag === 'obf' || fTag === 'obfuscated') {
          output += '<span class="mc-obfuscated">';
          closeStack.push('</span>');
        } else if (fTag === 'reset' || fTag === 'r') {
          closeAll();
        }
        i += mmFormatMatch[0].length;
        continue;
      }

      // Named color tags: <red>, <color:red>, etc.
      const mmNamedColorMatch = remaining.match(/^<(?:color:|colour:)?([a-z_]+)>/i);
      if (mmNamedColorMatch && NAMED_COLORS[mmNamedColorMatch[1].toLowerCase()]) {
        const color = NAMED_COLORS[mmNamedColorMatch[1].toLowerCase()];
        closeAll();
        openSpan(`color:${color}`);
        i += mmNamedColorMatch[0].length;
        continue;
      }
    }

    // Normal character -> escape HTML
    output += escapeHtml(ch);
    i++;
  }

  closeAll();
  return output;
}

function createKvRow(label, value, allowHtml = false, valueClass = 'log-value') {
  const row = document.createElement('div');

  const strong = document.createElement('strong');
  strong.className = 'log-label';
  strong.textContent = label + ': ';

  const span = document.createElement('span');
  span.className = valueClass;

  if (allowHtml) {
    span.innerHTML = value || '';
  } else {
    span.textContent = value ?? '';
  }

  row.appendChild(strong);
  row.appendChild(span);
  return row;
}

function formatLogTime(log) {
  const whenMs = (log.event_ts ?? log.eventTimestamp ?? log.timestamp);
  if (whenMs == null || !Number.isFinite(new Date(whenMs).getTime())) return 'Desconocido';
  const date = new Date(whenMs);
  const now = Date.now();
  const diffSec = Math.floor((now - date.getTime()) / 1000);
  let rel = '';
  if (diffSec >= 0) {
    if (diffSec < 60) rel = 'hace instantes';
    else if (diffSec < 3600) rel = `hace ${Math.floor(diffSec / 60)} min`;
    else if (diffSec < 86400) rel = `hace ${Math.floor(diffSec / 3600)} h`;
    else if (diffSec < 604800) rel = `hace ${Math.floor(diffSec / 86400)} d`;
  }
  const dateStr = date.toLocaleString(DATE_LOCALE || 'es-ES', {
    timeZone: TIMEZONE || undefined,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
  return rel ? `${rel} · ${dateStr}` : dateStr;
}

function parseMaterialForIcon(log) {
  if (['PLAYER_MESSAGE', 'PLAYER_COMMAND'].includes(log.action)) return '';
  let materialForDataAttr = log.material ? String(log.material).toLowerCase() : '';

  if (!materialForDataAttr && log.metadata && log.metadata !== '{}') {
    try {
      const parsed = JSON.parse(log.metadata);
      if (parsed) {
        const mat = parsed.item?.material ?? parsed.material ?? parsed.blockMaterial ?? parsed.block?.material;
        if (mat) materialForDataAttr = String(mat).toLowerCase();
      }
    } catch (_) {}
  }

  if (!materialForDataAttr && log.details) {
    const d = String(log.details);
    const m =
      d.match(/^(?:added|dropped|took|gave|removed|picked up|placed|broken)\s+(?:x?\d+\s+)?([a-z0-9_:]+)/i) ||
      d.match(/with\s+(?:x?\d+\s+)?([a-z0-9_:]+)/i) ||
      d.match(/(?:block\s+)?([a-z0-9_:]+)\s+(?:placed|broken)/i);

    if (m && m[1]) materialForDataAttr = m[1].toLowerCase();
  }

  if (!materialForDataAttr && log.action && log.action.startsWith('BUCKET_')) {
    materialForDataAttr = String(log.details || '').toLowerCase() + '_bucket';
  }

  if (log.action === 'ENTITY_DEATH' && log.details) {
    const m = String(log.details).match(/(minecraft:)?([a-z_]+)\b/i);
    if (m) {
      materialForDataAttr = m[2].toLowerCase();
      log._isMob = true;
    }
  }

  return materialForDataAttr;
}

function buildIconElement(materialForDataAttr, isMob) {
  if (!materialForDataAttr) return null;

  const img = document.createElement('img');
  img.className = 'mc-icon-large';
  img.loading = 'lazy';
  img.alt = '';

  const iconName = isMob
    ? getEntityIconFilename(materialForDataAttr)
    : getIconFilename(materialForDataAttr);

  img.src = `${ICON_BASE}/${iconName}`;
  img.onerror = function () { this.remove(); };

  return img;
}

function getCssClass(action) {
  switch (action) {
    case 'BLOCK_PLACE':
    case 'ENTITY_PLACE': return 'log-place';
    case 'ITEM_PICKUP':  return 'log-pickup';
    case 'CONTAINER_ADD':return 'log-add';

    case 'BLOCK_BREAK':  return 'log-break';
    case 'ITEM_DROP':    return 'log-drop';
    case 'CONTAINER_TAKE':return 'log-take';

    case 'BUCKET_EMPTY': return 'log-emptied';
    case 'ENTITY_DEATH': return 'log-killed';

    case 'PLAYER_COMMAND': return 'log-cmd';       // NEW
    case 'PLAYER_MESSAGE': return 'log-message';   // NEW
  }
  return '';
}

function addFiltersToUrl(url, actionId) {
  const msg = isMsgOrCmd(actionId);

  // Always-allowed filters
  const hideRolled = document.getElementById('hideRolled')?.checked;
  const hideWE     = document.getElementById('hideWE')?.checked;
  if (hideRolled) url.searchParams.set('hideRolled', '1');
  if (hideWE)     url.searchParams.set('hideWE', '1');

  if (IS_ALL_USERS_VIEW) {
    const playerNameFilterInput = document.getElementById('playerNameFilter');
    if (playerNameFilterInput) {
      const playerName = playerNameFilterInput.value.trim();
      if (playerName) {
        url.searchParams.set('user', playerName);
      }
    }
  } else if (INITIAL_USER_FILTER) {
    url.searchParams.set('user', INITIAL_USER_FILTER);
  }

  // Action-specific filters
  if (msg) {
    // NEW: dedicated keyword search -> backend param q
    const q = document.getElementById('textFilter')?.value?.trim() || '';
    if (q) url.searchParams.set('q', q);

    // IMPORTANT: do NOT send item filters for message/command tabs
    return;
  }

  // Normal item filters for everything else
  const material = document.getElementById('itemFilter')?.value?.trim() || '';
  const name     = document.getElementById('nameFilter')?.value?.trim() || '';
  const lore     = document.getElementById('loreFilter')?.value?.trim() || '';
  const enchants = Array.from(document.querySelectorAll('.enchant-checkbox:checked')).map(cb => cb.value);

  if (material) url.searchParams.set('material', material);
  if (name)     url.searchParams.set('name', name);
  if (lore)     url.searchParams.set('lore', lore);
  if (enchants.length) url.searchParams.set('enchants', enchants.join(','));
}

function isRolledBack(log) {
  // Prefer explicit flags on the log object
  let v = log?.rolled_back ?? log?.rolledBack ?? log?.rolled ?? log?.isRolled ?? log?.is_rolled ?? log?.undone ?? log?.rollback;
  if (Array.isArray(log?.flags)) {
    if (log.flags.includes('ROLLED_BACK') || log.flags.includes('UNDO')) return true;
  }
  // Coerce common representations
  if (typeof v === 'string') return ['1','true','yes','y','t'].includes(v.toLowerCase());
  if (typeof v === 'number') return v !== 0;
  if (typeof v === 'boolean') return v;

  // Last resort: some backends tuck it into metadata JSON
  try {
    const meta = log?.metadata ? JSON.parse(log.metadata) : null;
    const mv = meta?.rolled_back ?? meta?.rolledBack ?? meta?.rollback ?? meta?.undone;
    if (typeof mv === 'string') return['1','true','yes','y','t'].includes(mv.toLowerCase());
    if (typeof mv === 'number') return mv !== 0;
    if (typeof mv === 'boolean') return mv;
  } catch (_) { /* ignore bad JSON */ }

  return false;
}

function recordedAmount(log) {
  let amount = log.amount;
  if (amount == null && log.metadata) {
    try {
      const meta = JSON.parse(log.metadata);
      amount = meta?.item?.amount ?? meta?.item?.count ?? meta?.amount ?? meta?.count;
    } catch (_) {}
  }
  if (amount == null) amount = String(log.details || '').match(/^(?:added|dropped|took|gave|removed|picked up)\s+x?(\d+)\s+/i)?.[1];
  const number = Number(amount);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
}

function getActionBadgeClass(action) {
  switch (action) {
    case 'BLOCK_BREAK':
    case 'ENTITY_DEATH':
      return 'badge-break';
    case 'BLOCK_PLACE':
    case 'ENTITY_PLACE':
      return 'badge-place';
    case 'ITEM_PICKUP':
      return 'badge-pickup';
    case 'ITEM_DROP':
      return 'badge-drop';
    case 'CONTAINER_ADD':
    case 'CONTAINER_TAKE':
    case 'BUCKET_EMPTY':
    case 'BUCKET_FILL':
      return 'badge-container';
    case 'PLAYER_COMMAND':
      return 'badge-cmd';
    case 'PLAYER_MESSAGE':
      return 'badge-msg';
    default:
      return 'badge-generic';
  }
}

function appendLogItem(log, targetActionId = currentAction) {
  if (targetActionId === 'all' && ['PLAYER_MESSAGE', 'PLAYER_COMMAND'].includes(log.action)) return false;
  if (DISABLED_ACTIONS.includes(log.action)) return false;
  const uniqueId = `log-${log.id}-${targetActionId}`;
  if (document.getElementById(uniqueId)) return false;
  const rolled = isRolledBack(log);
  const material = parseMaterialForIcon(log);
  const hasExtra = log.has_meta === 1 || log.has_meta === true || log.hasMeta === 1 || log.hasMeta === true;
  const offerDetails = hasExtra && !['PLAYER_MESSAGE', 'PLAYER_COMMAND'].includes(log.action);
  const card = document.createElement('div');
  card.id = uniqueId;
  card.className = `card themed-card log-card ${getCssClass(log.action)}`;
  card.dataset.action = log.action;
  card.dataset.timestamp = String(log.timestamp ?? '');
  const copyable = !['PLAYER_MESSAGE', 'PLAYER_COMMAND'].includes(log.action)
    && Number.isSafeInteger(log.id) && log.id > 0 && typeof log.world === 'string' && log.world.trim()
    && [log.x, log.y, log.z].every(Number.isSafeInteger);
  if (copyable) {
    card.classList.add('copyable-card');
    card.dataset.logId = String(log.id);
    card.title = 'Clic para copiar comando de teletransporte';
  }
  if (material) card.dataset.material = material;
  if (rolled) { card.classList.add('rolled-back'); card.dataset.rolled = '1'; }

  const body = document.createElement('div');
  body.className = 'card-body log-body';

  // Player Avatar
  const avatarWrap = document.createElement('div');
  avatarWrap.className = 'log-avatar-wrap';
  const avatar = document.createElement('img');
  avatar.className = 'player-avatar';
  avatar.loading = 'lazy';
  avatar.alt = log.playerName || 'Jugador';
  const isBedrock = log.playerName && (log.playerName.startsWith('.') || log.playerName.startsWith('*'));
  const lookupId = log.playerUUID ? log.playerUUID : (log.playerName ? log.playerName.replace(/^[.*]/, '') : 'MHF_Steve');
  avatar.src = `https://mc-heads.net/avatar/${encodeURIComponent(lookupId)}/36`;
  avatar.onerror = function () {
    this.onerror = null;
    this.src = 'https://mc-heads.net/avatar/MHF_Steve/36';
  };
  avatarWrap.appendChild(avatar);
  body.appendChild(avatarWrap);

  // Main container
  const main = document.createElement('div');
  main.className = 'log-main';

  // Heading row
  const heading = document.createElement('div');
  heading.className = 'log-heading';

  const player = document.createElement('span');
  player.className = 'log-player';
  player.textContent = log.playerName || 'Desconocido';
  heading.appendChild(player);

  if (isBedrock) {
    const bedrockBadge = document.createElement('span');
    bedrockBadge.className = 'badge-bedrock';
    bedrockBadge.textContent = 'BEDROCK';
    bedrockBadge.title = 'Jugador de Bedrock';
    heading.appendChild(bedrockBadge);
  }

  const action = document.createElement('span');
  action.className = `action-badge ${getActionBadgeClass(log.action)}`;
  action.textContent = String(log.action).replace(/_/g, ' ').toLowerCase();
  action.title = log.action;
  heading.appendChild(action);

  if (material || /^(BLOCK_|ITEM_|CONTAINER_|BUCKET_|HOPPER_)/.test(log.action)) {
    const item = document.createElement('span');
    item.className = 'material-chip';
    const icon = buildIconElement(material, !!log._isMob);
    if (icon) {
      icon.className = 'mc-icon';
      item.appendChild(icon);
    }
    const nameSpan = document.createElement('span');
    const name = material ? material.replace(/^minecraft:/, '').replace(/_/g, ' ') : 'Item';
    nameSpan.textContent = name;
    item.appendChild(nameSpan);

    const amount = recordedAmount(log);
    if (amount != null) {
      const countSpan = document.createElement('span');
      countSpan.className = 'material-count';
      countSpan.textContent = ` × ${amount}`;
      item.appendChild(countSpan);
    }
    item.title = material || 'Sin material';
    heading.appendChild(item);
  }

  if (rolled) {
    const badge = document.createElement('span');
    badge.className = 'rolled-badge';
    badge.textContent = 'Revertido';
    heading.appendChild(badge);
  }
  main.appendChild(heading);

  // Details
  const description = document.createElement('div');
  description.className = 'log-details';
  if (log.details) {
    description.innerHTML = translateColorCodesSafe(log.details);
  } else {
    description.textContent = 'Sin descripción registrada.';
  }
  main.appendChild(description);

  // Facts
  const facts = document.createElement('div');
  facts.className = 'log-facts';

  const time = document.createElement('span');
  time.className = 'log-time';
  time.textContent = formatLogTime(log);
  facts.appendChild(time);

  if (log.world) {
    const worldBadge = document.createElement('span');
    const wLower = String(log.world).toLowerCase();
    let wClass = 'badge-world-overworld';
    let wLabel = log.world;
    if (wLower.includes('nether')) {
      wClass = 'badge-world-nether';
      wLabel = 'Nether';
    } else if (wLower.includes('the_end') || wLower.includes('end')) {
      wClass = 'badge-world-end';
      wLabel = 'The End';
    } else if (wLower.includes('world') || wLower.includes('overworld')) {
      wLabel = 'Overworld';
    }
    worldBadge.className = `badge-world ${wClass}`;
    worldBadge.textContent = wLabel;
    facts.appendChild(worldBadge);
  }

  const location = document.createElement(copyable ? 'button' : 'div');
  location.className = copyable ? 'log-location copy-teleport' : 'log-location';
  if (copyable) {
    location.type = 'button';
    location.title = 'Clic para copiar comando de teletransporte';
    location.setAttribute('aria-label', `Comando de teletransporte a ${log.world} en ${log.x}, ${log.y}, ${log.z}`);
    location.textContent = `${log.x}, ${log.y}, ${log.z}`;
  } else {
    const coordinate = value => value == null ? '?' : value;
    location.textContent = `(${coordinate(log.x)}, ${coordinate(log.y)}, ${coordinate(log.z)})`;
  }
  facts.appendChild(location);
  main.appendChild(facts);

  if (offerDetails) {
    const collapseId = `collapseMeta-${log.id}-${targetActionId}`;
    const button = document.createElement('button');
    button.className = 'btn btn-sm meta-btn';
    button.type = 'button';
    button.title = 'Mostrar u ocultar detalles del ítem';
    button.setAttribute('data-bs-toggle', 'collapse');
    button.setAttribute('data-bs-target', `#${collapseId}`);
    button.setAttribute('data-target', collapseId);
    button.setAttribute('aria-expanded', 'false');
    button.setAttribute('aria-controls', collapseId);
    button.textContent = 'Ver Detalles';
    const controls = document.createElement('div');
    controls.className = 'log-controls';
    controls.appendChild(button);
    main.appendChild(controls);
    const collapse = document.createElement('div');
    collapse.className = 'collapse metadata-collapse';
    collapse.id = collapseId;
    collapse.title = 'Detalles del ítem';
    const contents = document.createElement('div');
    contents.className = 'card card-body themed-metadata-details';
    collapse.appendChild(contents);
    collapse.addEventListener('shown.bs.collapse', () => { button.textContent = 'Ocultar Detalles'; });
    collapse.addEventListener('hidden.bs.collapse', () => { button.textContent = 'Ver Detalles'; });
    main.appendChild(collapse);
  }

  body.appendChild(main);
  card.appendChild(body);
  createTabAndPane(log.action, log.action.toLowerCase().replace(/_/g, '-'), false);
  const list = document.getElementById(`${targetActionId}-pane`)?.querySelector('.list-group');
  if (!list) return false;
  list.appendChild(card);
  return true;
}

function createTabAndPane(actionName, tabId, isActive) {
  const logTab = document.getElementById('logTab');
  const logTabContent = document.getElementById('logTabContent');
  if (!document.getElementById(`${tabId}-tab`)) {
      const li = document.createElement('li');
      li.className = 'nav-item';
      li.setAttribute('role', 'presentation');
      const button = document.createElement('button');
      button.className = `nav-link ${isActive ? 'active' : ''}`;
      button.id = `${tabId}-tab`;
      button.setAttribute('data-bs-toggle', 'tab');
      button.setAttribute('data-bs-target', `#${tabId}-pane`);
      button.type = 'button';
      button.setAttribute('role', 'tab');
      button.setAttribute('aria-controls', `${tabId}-pane`);
      button.setAttribute('aria-selected', isActive ? 'true' : 'false');
      button.textContent = String(actionName).replace(/_/g, ' ').toLowerCase()
        .replace(/\b\w/g, ch => ch.toUpperCase());
      li.appendChild(button);
      logTab.appendChild(li);

      const paneDiv = document.createElement('div');
      paneDiv.className = `tab-pane fade ${isActive ? 'show active' : ''}`;
      paneDiv.id = `${tabId}-pane`;
      paneDiv.setAttribute('role', 'tabpanel');
      paneDiv.setAttribute('aria-labelledby', `${tabId}-tab`);
      const listGroup = document.createElement('div');
      listGroup.className = 'list-group log-list';
      paneDiv.appendChild(listGroup);
      logTabContent.appendChild(paneDiv);
  }
}

function scrollTabStrip(direction) {
  const strip = document.getElementById('logTab');
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  strip.scrollBy({left: direction * Math.max(160, strip.clientWidth * .75), behavior: reduced ? 'auto' : 'smooth'});
}

function updateTabOverflow() {
  const strip = document.getElementById('logTab');
  document.getElementById('tabsPrevious').disabled = strip.scrollLeft <= 1;
  document.getElementById('tabsNext').disabled = strip.scrollLeft + strip.clientWidth >= strip.scrollWidth - 1;
}

function scrollCategoriesWithWheel(event) {
  if (event.ctrlKey || event.metaKey || event.target.closest?.('select, input, textarea')) return;
  const strip = document.getElementById('logTab');
  if (strip.scrollWidth <= strip.clientWidth + 1) return;
  const delta = Math.abs(event.deltaX || 0) > Math.abs(event.deltaY || 0) ? event.deltaX : event.deltaY;
  if (!delta || !Number.isFinite(delta)) return;
  const scale = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? strip.clientWidth : 1;
  event.preventDefault();
  strip.scrollLeft = Math.max(0, Math.min(strip.scrollWidth - strip.clientWidth, strip.scrollLeft + delta * scale));
  updateTabOverflow();
}

function syncCategoryNavigation() {
  const strip = document.getElementById('logTab');
  document.getElementById('categoryJump').value = currentAction;
  const active = document.getElementById(`${currentAction}-tab`);
  if (active) {
    const viewport = strip.getBoundingClientRect();
    const tab = active.getBoundingClientRect();
    const delta = tab.left < viewport.left ? tab.left - viewport.left : tab.right > viewport.right ? tab.right - viewport.right : 0;
    if (delta) strip.scrollBy({left: delta});
  }
  updateTabOverflow();
}

function activateCategory(actionId) {
  if (!ALL_ACTIONS.some(action => action.id === actionId)) return;
  const button = document.getElementById(`${actionId}-tab`);
  if (button) bootstrap.Tab.getOrCreateInstance(button).show();
}

function categoryShown(actionId) {
  if (currentAction === actionId) { syncCategoryNavigation(); return; }
  currentAction = actionId;
  updateFilterVisibility(currentAction);
  syncCategoryNavigation();
  return resetAndReload();
}

function initializeCategoryNavigation() {
  const jump = document.getElementById('categoryJump');
  jump.replaceChildren();
  ALL_ACTIONS.forEach(action => {
    const option = document.createElement('option');
    option.value = action.id;
    option.textContent = action.label.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, value => value.toUpperCase());
    jump.appendChild(option);
  });
  jump.addEventListener('change', () => activateCategory(jump.value));
  document.getElementById('tabsPrevious').addEventListener('click', () => scrollTabStrip(-1));
  document.getElementById('tabsNext').addEventListener('click', () => scrollTabStrip(1));
  document.getElementById('logTab').addEventListener('scroll', updateTabOverflow);
  document.getElementById('categoryNav').addEventListener('wheel', scrollCategoriesWithWheel, {passive: false});
  window.addEventListener('resize', syncCategoryNavigation);
  if (typeof ResizeObserver !== 'undefined') new ResizeObserver(syncCategoryNavigation).observe(document.getElementById('logTab'));
  syncCategoryNavigation();
}

function initializeFilterPanel() {
  const fields = document.getElementById('filterFields');
  const toggle = document.getElementById('filterToggle');
  fields.addEventListener('shown.bs.collapse', event => {
    if (event.target !== fields) return;
    toggle.textContent = 'Hide filters'; toggle.setAttribute('aria-expanded', 'true');
  });
  fields.addEventListener('hidden.bs.collapse', event => {
    if (event.target !== fields) return;
    toggle.textContent = 'Show filters'; toggle.setAttribute('aria-expanded', 'false');
  });
  fields.addEventListener('hide.bs.collapse', event => {
    if (event.target === fields) bootstrap.Dropdown.getInstance(document.getElementById('enchantDropdown'))?.hide();
  });
}

function setMessage(message, retry = false) {
  document.getElementById('userMessageText').textContent = message;
  document.getElementById('userMessage').style.display = 'block';
  document.getElementById('retryLogs').hidden = !retry;
}

function clearMessage() {
  document.getElementById('userMessage').style.display = 'none';
  document.getElementById('retryLogs').hidden = true;
}

function setLoading(loading) {
  isLoading = loading;
  document.getElementById('loadingIndicator').style.display = loading ? 'block' : 'none';
  document.getElementById('logTabContent').setAttribute('aria-busy', String(loading));
}

function updateResultCount() {
  const count = loadedCounts[currentAction];
  const el = document.getElementById('resultCount');
  if (!el) return;
  el.textContent = count
    ? `${count} ${count === 1 ? 'registro cargado' : 'registros cargados'}` : 'No se encontraron registros';
}

function requestError(status) {
  const error = new Error(status === 503
    ? 'Database temporarily unavailable. Retry when the connection recovers.'
    : status === 400 ? 'Check your filters and date range, then try again.'
    : 'Could not load entries. Please retry.');
  error.status = status;
  return error;
}

function updateFilterSummary() {
  const url = new URL(API_URL, window.location.origin);
  addFiltersToUrl(url, currentAction);
  const selected = document.querySelectorAll('.enchant-checkbox:checked').length;
  document.getElementById('enchantDropdown').textContent = selected
    ? `${selected} ${selected === 1 ? 'enchantment' : 'enchantments'} selected` : 'Select Enchants';
  const labels = {user: 'Player', q: 'Text', material: 'Material', name: 'Item name',
    lore: 'Lore', enchants: 'Enchantments', hideRolled: 'Hide rolled-back', hideWE: 'Hide WorldEdit'};
  const summary = document.getElementById('filterSummary');
  summary.replaceChildren();
  const chip = text => {
    const span = document.createElement('span');
    span.className = 'filter-chip';
    span.textContent = text;
    summary.appendChild(span);
  };
  if (!url.searchParams.has('user')) chip('All users');
  for (const [key, value] of url.searchParams) {
    chip(key.startsWith('hide') ? labels[key] : `${labels[key]}: ${value}`);
  }
  const from = document.getElementById('dateFrom').value;
  const through = document.getElementById('dateTo').value;
  if (from) chip(`From: ${from}`);
  if (through) chip(`Through: ${through}`);
  if (!from && !through) chip(SINCE > 0
    ? `Since: ${new Date(SINCE * 1000).toLocaleString(DATE_LOCALE, {timeZone: TIMEZONE})}` : 'All time');
}

function resetState() {
  for (const a of ALL_ACTIONS) {
    finished[a.id] = false;
    lastTimestamps[a.id] = 0;
    lastIds[a.id] = 0;
    loadedCounts[a.id] = 0;
    document.querySelector(`#${a.id}-pane .list-group`)?.replaceChildren();
  }
  updateResultCount();
}

async function loadPage(actionId) {
  if (isLoading || finished[actionId] || requestFailed || accessExpired) return;
  const myNonce = ++requestNonce;
  currentController = new AbortController();
  const {signal} = currentController;
  const url = authenticatedUrl(API_URL);
  url.searchParams.set('pageSize', itemsPerPage);
  if (lastTimestamps[actionId] > 0 && lastIds[actionId] > 0) {
    url.searchParams.set('lastTimestamp', lastTimestamps[actionId]);
    url.searchParams.set('lastId', lastIds[actionId]);
  }
  const from = document.getElementById('dateFrom').value;
  const through = document.getElementById('dateTo').value;
  if (from || through) {
    if (from) url.searchParams.set('sinceDate', from);
    if (through) url.searchParams.set('untilDate', through);
  } else {
    url.searchParams.set('since', SINCE);
  }
  if (actionId !== 'all') url.searchParams.set('action', actionId.toUpperCase().replace(/-/g, '_'));
  addFiltersToUrl(url, actionId);
  clearMessage();
  setLoading(true);
  document.getElementById('endOfLogsIndicator').style.display = 'none';
  try {
    const resp = await fetch(url, {signal});
    if (myNonce !== requestNonce) return;
    if (resp.status === 403) {
      accessExpired = true;
      setMessage('Editor link expired. Run /po editor again.');
      return;
    }
    if (!resp.ok) throw requestError(resp.status);
    const data = await resp.json();
    if (myNonce !== requestNonce) return;
    if (data.playerNotFound) {
      finished[actionId] = true;
      setMessage('Player not found. Try a UUID or a recently seen name.');
      return;
    }
    if (!Array.isArray(data.logs)) throw new Error('Invalid log response');
    for (const log of data.logs) {
      if (appendLogItem(log, actionId)) loadedCounts[actionId]++;
    }
    if (data.logs.length) {
      lastTimestamps[actionId] = data.lastTimestamp || 0;
      lastIds[actionId] = data.lastId || 0;
    }
    updateResultCount();
    if (data.logs.length < itemsPerPage) {
      finished[actionId] = true;
      const end = document.getElementById('endOfLogsIndicator');
      end.textContent = loadedCounts[actionId] ? 'End of results.' : 'No entries match these filters.';
      end.style.display = 'block';
    }
  } catch (error) {
    if (error.name === 'AbortError' || myNonce !== requestNonce) return;
    requestFailed = true;
    setMessage(error.status ? error.message : 'Could not load entries. Check your connection and retry.', true);
  } finally {
    if (myNonce === requestNonce) setLoading(false);
  }
}

window.addEventListener('scroll', () => {
  const nearBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 800;
  if (nearBottom) loadPage(currentAction);
});

function cancelCurrentRequest() {
  requestNonce++;
  currentController?.abort();
  setLoading(false);
}

function resetAndReload() {
  clearTimeout(debounceTimeout);
  cancelCurrentRequest();
  requestFailed = false;
  clearMessage();
  resetState();
  updateFilterSummary();
  document.getElementById('endOfLogsIndicator').style.display = 'none';
  if (accessExpired) {
    setMessage('Editor link expired. Run /po editor again.');
    return;
  }
  return loadPage(currentAction);
}

let debounceTimeout;
function debounceResetAndReload() {
  clearTimeout(debounceTimeout);
  cancelCurrentRequest();
  requestFailed = true; // Do not paginate with old cursors while new filters are pending.
  updateFilterSummary();
  debounceTimeout = setTimeout(resetAndReload, 500);
}

function retryLogs() {
  requestFailed = false;
  return loadPage(currentAction);
}

function resetFilters() {
  ['playerNameFilter', 'itemFilter', 'nameFilter', 'loreFilter', 'textFilter', 'dateFrom', 'dateTo', 'enchantSearch']
    .forEach(id => { const input = document.getElementById(id); if (input) input.value = ''; });
  document.querySelectorAll('.enchant-checkbox').forEach(input => input.checked = false);
  document.querySelectorAll('#enchantDropdownPanel .form-check').forEach(item => item.style.display = '');
  document.getElementById('hideRolled').checked = true;
  document.getElementById('hideWE').checked = true;
  return resetAndReload();
}

['itemFilter', 'nameFilter', 'loreFilter', 'textFilter', 'dateFrom', 'dateTo', 'playerNameFilter']
  .forEach(id => document.getElementById(id)?.addEventListener('input', debounceResetAndReload));
document.querySelectorAll('.enchant-checkbox')
  .forEach(input => input.addEventListener('change', debounceResetAndReload));
['hideRolled', 'hideWE'].forEach(id => document.getElementById(id)?.addEventListener('change', debounceResetAndReload));
document.getElementById('resetFilters').addEventListener('click', resetFilters);
document.getElementById('retryLogs').addEventListener('click', retryLogs);
document.getElementById('enchantSearch')?.addEventListener('keyup', function() {
  const query = this.value.toLowerCase();
  document.querySelectorAll('#enchantDropdownPanel .form-check').forEach(item => {
    const label = item.querySelector('label').textContent.toLowerCase();
    item.style.display = label.includes(query) ? '' : 'none';
  });
});

function renderMetadataHtml(metaRaw) {
  const meta = JSON.parse(metaRaw || '{}');
  const item = meta && typeof meta.item === 'object' && meta.item !== null ? meta.item : meta;
  if (!item || typeof item !== 'object' || Array.isArray(item)) return '<em>No displayable details found.</em>';
  const section = (title, content) => `<section class="metadata-section"><h3>${title}</h3>${content}</section>`;
  const fields = [];
  const field = (label, value, formatted = false) => {
    if (value === undefined || value === null || value === '') return;
    fields.push(`<dt>${label}</dt><dd>${formatted ? translateColorCodesSafe(String(value)) : escapeHtml(value)}</dd>`);
  };
  field('Name', item.displayName ?? item.customName, true);
  field('Material', item.material);
  field('Quantity', item.amount);
  field('Custom model data', item.cmd ?? item.customModelData);
  let html = fields.length ? section('Item information', `<dl class="metadata-kv">${fields.join('')}</dl>`) : '';
  let enchantments = [];
  if (Array.isArray(item.enchantments)) {
    enchantments = item.enchantments.map(value => {
      if (value && typeof value === 'object') {
        return `${value.id ?? value.name ?? value.key ?? 'Enchantment'}: ${value.level ?? value.lvl ?? '?'}`;
      }
      return String(value);
    });
  } else if (item.enchantments && typeof item.enchantments === 'object') {
    enchantments = Object.entries(item.enchantments).map(([key, value]) => `${key}: ${value}`);
  }
  if (enchantments.length) html += section('Enchantments', `<ul>${enchantments.map(value => `<li>${translateColorCodesSafe(String(value))}</li>`).join('')}</ul>`);
  if (Array.isArray(item.lore) && item.lore.length) {
    const renderedLore = item.lore.map(value => {
      const formatted = translateColorCodesSafe(String(value));
      const textOnly = formatted.replace(/<[^>]+>/g, '').trim();
      return `<div class="lore-line">${textOnly ? formatted : '&nbsp;'}</div>`;
    }).join('');
    html += section('Lore', `<div class="metadata-lore">${renderedLore}</div>`);
  }
  const excluded = new Set(['material', 'amount', 'count', 'type', 'serialized', 'dataVersion', 'v',
    'timestamp', 'eventTimestamp', 'origin', 'source', 'rolled_back', 'rolledBack', 'rollback', 'undone',
    'entityUuid', 'snapshot_id', 'snapshotId', 'id', 'world', 'x', 'y', 'z', 'playerUUID', 'playerName', 'action', 'details',
    'event_ts', 'rolled_back_ts', 'rolled_back_by', 'origin_id', 'rb_id', 'fromX', 'fromY', 'fromZ', 'anchorX', 'anchorY', 'anchorZ',
    'displayName', 'customName', 'cmd', 'customModelData', 'lore', 'enchantments']);
  const hasContext = item === meta && ['blockData', 'blockMaterial', 'block', 'entity', 'sign', 'containerStructure'].some(key => key in meta);
  const explicitItemKeys = new Set(['nbt', 'components', 'customData', 'persistentData']);
  const meaningful = value => value != null && (typeof value === 'string' ? value.trim() !== ''
    : typeof value === 'object' ? Object.values(value).some(meaningful) : true);
  const extra = Object.fromEntries(Object.entries(item).filter(([key, value]) =>
    !excluded.has(key) && (!hasContext || explicitItemKeys.has(key)) && meaningful(value)));
  if (Object.keys(extra).length) html += section('Additional item data', `<pre class="metadata-json">${escapeHtml(JSON.stringify(extra, null, 2))}</pre>`);
  return html || '<em>No displayable details found.</em>';
}

async function loadMetadata(wrapper, id) {
  if (wrapper.dataset.filled === '1' || wrapper.dataset.loading === '1' || accessExpired) return;
  wrapper.dataset.loading = '1';
  wrapper.setAttribute('aria-busy', 'true');
  wrapper.textContent = 'Loading item details…';
  try {
    const url = authenticatedUrl('/api/logMeta');
    url.searchParams.set('id', id);
    const response = await fetch(url);
    if (response.status === 403) {
      accessExpired = true;
      setMessage('Editor link expired. Run /po editor again.');
      wrapper.textContent = 'Editor link expired. Run /po editor again.';
      return;
    }
    if (!response.ok) throw requestError(response.status);
    const html = renderMetadataHtml(await response.text());
    if (!wrapper.isConnected) return;
    wrapper.innerHTML = html;
    wrapper.dataset.filled = '1';
  } catch (error) {
    if (!wrapper.isConnected) return;
    wrapper.textContent = error.status === 503
      ? 'Database temporarily unavailable.' : 'Could not load item details.';
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.className = 'btn btn-sm btn-outline-secondary ms-2';
    retry.textContent = 'Retry details';
    retry.addEventListener('click', () => loadMetadata(wrapper, id));
    wrapper.appendChild(retry);
  } finally {
    delete wrapper.dataset.loading;
    wrapper.setAttribute('aria-busy', 'false');
  }
}
let teleportCopyNonce = 0;
let teleportCopyController;
let pendingTeleportCard;
let fallbackTeleport;
let clipboardWrites = Promise.resolve();

let copyNoticeTimeout = null;
function showCopyNotice(message, fallback = false) {
  const notice = document.getElementById('copyNotice');
  if (notice) notice.hidden = false;
  const status = document.getElementById('copyStatus');
  if (status) status.textContent = message;
  const fb = document.getElementById('copyFallback');
  if (fb) fb.hidden = !fallback;
  if (copyNoticeTimeout) clearTimeout(copyNoticeTimeout);
  if (!fallback) {
    copyNoticeTimeout = setTimeout(() => {
      dismissCopyNotice();
    }, 4500);
  }
}

function dismissCopyNotice() {
  if (copyNoticeTimeout) {
    clearTimeout(copyNoticeTimeout);
    copyNoticeTimeout = null;
  }
  teleportCopyNonce++;
  teleportCopyController?.abort();
  pendingTeleportCard = null;
  fallbackTeleport = null;
  const notice = document.getElementById('copyNotice');
  if (notice) notice.hidden = true;
  const cmd = document.getElementById('teleportCommand');
  if (cmd) cmd.value = '';
}

function legacyCopyText(text) {
  const active = document.activeElement;
  const selection = window.getSelection?.();
  const ranges = [];
  for (let index = 0; index < (selection?.rangeCount || 0); index++) ranges.push(selection.getRangeAt(index).cloneRange());
  const field = document.createElement('textarea');
  field.value = text;
  field.readOnly = true;
  field.style.position = 'fixed';
  field.style.left = '-10000px';
  document.body.appendChild(field);
  try {
    field.focus({preventScroll: true});
    field.select();
    return document.execCommand?.('copy') === true;
  } catch (_) {
    return false;
  } finally {
    field.remove();
    if (active?.isConnected) active.focus?.({preventScroll: true});
    if (selection && ranges.length) {
      selection.removeAllRanges();
      ranges.forEach(range => selection.addRange(range));
    }
  }
}

function writeTeleportClipboard(text, current) {
  // Serialize writes: an older pending browser write cannot finish after the newest one.
  const operation = clipboardWrites.catch(() => {}).then(async () => {
    if (!current()) return false;
    try {
      if (globalThis.navigator?.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
        return true;
      }
    } catch (_) { /* Try the compatibility path without reading the clipboard. */ }
    return current() && legacyCopyText(text);
  });
  clipboardWrites = operation.catch(() => false);
  return operation;
}

function teleportCardForEvent(event) {
  if (event.defaultPrevented || (event.button != null && event.button !== 0)) return null;
  const target = event.target;
  const card = target.closest?.('.copyable-card');
  if (!card || target.closest('.metadata-collapse, .themed-metadata-details')) return null;
  const explicitCopy = target.closest('.copy-teleport');
  if (!explicitCopy && target.closest('button, a, input, select, textarea, label, [contenteditable]')) return null;
  const selection = window.getSelection?.();
  if (!explicitCopy && selection && !selection.isCollapsed && selection.toString()
      && card.contains(selection.anchorNode)) return null;
  return card;
}

async function copyTeleportCommand(card) {
  if (pendingTeleportCard === card) return;
  if (accessExpired) { showCopyNotice('Editor link expired. Run /po editor again.'); return; }
  const nonce = ++teleportCopyNonce;
  teleportCopyController?.abort();
  const controller = new AbortController();
  teleportCopyController = controller;
  pendingTeleportCard = card;
  fallbackTeleport = null;
  document.getElementById('teleportCommand').value = '';
  showCopyNotice('Preparing teleport command…');
  const startedAt = Date.now();
  const current = () => nonce === teleportCopyNonce && card.isConnected;
  try {
    const url = authenticatedUrl('/api/teleportCommand');
    url.searchParams.set('id', card.dataset.logId);
    const response = await fetch(url, {method: 'POST', signal: controller.signal});
    if (!current()) return;
    if (response.status === 403) {
      accessExpired = true;
      setMessage('Editor link expired. Run /po editor again.');
      throw new Error('Editor link expired. Run /po editor again.');
    }
    if (!response.ok) throw new Error(response.status === 404
      ? 'This entry or its world is no longer available.' : response.status === 503
      ? 'The server or database is temporarily unavailable. Click the entry to retry.'
      : 'Could not prepare a teleport command. Click the entry to retry.');
    const data = await response.json();
    if (!current()) return;
    if (typeof data.command !== 'string' || !data.command.startsWith('/pc_tp ') || data.command.length > 512
        || !Number.isFinite(data.expiresInSeconds) || data.expiresInSeconds <= 0 || data.expiresInSeconds > 60) {
      throw new Error('Invalid teleport command response. Click the entry to retry.');
    }
    fallbackTeleport = {command: data.command, expiresAt: startedAt + data.expiresInSeconds * 1000, current};
    if (Date.now() >= fallbackTeleport.expiresAt) throw new Error('Command expired. Click the entry again.');
    const copied = await writeTeleportClipboard(data.command, () => current() && Date.now() < startedAt + data.expiresInSeconds * 1000);
    if (!current()) return;
    if (Date.now() >= fallbackTeleport.expiresAt) throw new Error('Command expired. Click the entry again.');
    if (copied) {
      showCopyNotice('Teleport command copied. Paste it in-game within 60 seconds.');
    } else {
      document.getElementById('teleportCommand').value = data.command;
      showCopyNotice('Automatic copying was blocked. Copy this command and paste it in-game within 60 seconds.', true);
      const field = document.getElementById('teleportCommand');
      field.focus({preventScroll: true});
      field.select();
    }
  } catch (error) {
    if (error.name === 'AbortError' || !current()) return;
    fallbackTeleport = null;
    showCopyNotice(error.message || 'Could not prepare a teleport command. Click the entry to retry.');
  } finally {
    if (nonce === teleportCopyNonce) {
      pendingTeleportCard = null;
      if (!card.isConnected) dismissCopyNotice();
    }
  }
}

async function copyFallbackTeleport() {
  const pending = fallbackTeleport;
  if (!pending || !pending.current() || Date.now() >= pending.expiresAt) {
    fallbackTeleport = null;
    document.getElementById('teleportCommand').value = '';
    showCopyNotice('Command expired or entry changed. Click the entry again.');
    return;
  }
  const copied = await writeTeleportClipboard(pending.command, () => pending.current() && Date.now() < pending.expiresAt);
  if (!pending.current()) return;
  if (Date.now() >= pending.expiresAt) {
    fallbackTeleport = null;
    document.getElementById('teleportCommand').value = '';
    showCopyNotice('Command expired. Click the entry again.');
    return;
  }
  showCopyNotice(copied ? 'Teleport command copied. Paste it in-game within 60 seconds.'
    : 'Copying is blocked by this browser. Select the command and copy it manually.', !copied);
}

window.addEventListener('DOMContentLoaded', () => {
  document.getElementById('textFilter')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') resetAndReload();
  });

  let first = true;
  ALL_ACTIONS.forEach(a => {
    createTabAndPane(a.label, a.id, first);
    first = false;
  });

  currentAction = 'all';
  initializeCategoryNavigation();
  initializeFilterPanel();
  document.getElementById('copyDismiss').addEventListener('click', dismissCopyNotice);
  document.getElementById('copyAgain').addEventListener('click', copyFallbackTeleport);
  updateFilterVisibility(currentAction);
  updateFilterSummary();
  loadPage(currentAction);

  const logTabElement = document.getElementById('logTab');
  if (logTabElement) {
    logTabElement.addEventListener('shown.bs.tab', e => {
      const newActionId = e.target.id.replace('-tab', '');
      categoryShown(newActionId);
    });
  }

  document.body.addEventListener('click', async (e) => {
    const btn = e.target.closest('.meta-btn');
    if (!btn) {
      const card = teleportCardForEvent(e);
      if (card) await copyTeleportCommand(card);
      return;
    }

    const collapseId = btn.getAttribute('data-target');
    const wrapper = document.getElementById(collapseId)?.querySelector('.themed-metadata-details');
    if (!wrapper) return;

    const card = btn.closest('.log-card');
    const id = Number(card.id.replace(/^log-/, '').split('-')[0]);

    await loadMetadata(wrapper, id);
  });
});

(function injectEditorCustomStyles() {
  const css = `
    .metadata-section {
      margin-bottom: 1rem;
    }
    .metadata-section:last-child {
      margin-bottom: 0;
    }
    .metadata-section h3 {
      font-size: 0.82rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--ederus-primary);
      margin-bottom: 0.5rem;
      font-weight: 700;
    }
    .metadata-kv {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 0.35rem 1rem;
      margin: 0;
    }
    .metadata-kv dt {
      color: var(--ederus-text-muted);
      font-weight: 500;
    }
    .metadata-kv dd {
      margin: 0;
      color: var(--ederus-text);
      font-weight: 500;
      font-family: var(--ederus-mono);
    }
    .metadata-lore {
      background: rgba(0, 0, 0, 0.45);
      border: 1px solid var(--ederus-border);
      border-radius: 8px;
      padding: 0.75rem 1rem;
      font-family: var(--ederus-mono);
      font-size: 0.88rem;
      line-height: 1.5;
      word-break: break-word;
      color: #e2e8f0;
    }
    .lore-line {
      min-height: 1.25em;
    }
    .mc-obfuscated {
      filter: blur(0.8px);
      user-select: none;
    }
  `;
  const styleEl = document.createElement('style');
  styleEl.textContent = css;
  document.head.appendChild(styleEl);
})();
