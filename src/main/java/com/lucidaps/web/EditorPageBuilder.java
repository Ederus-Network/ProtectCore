/*
 * Decompiled with CFR 0.152.
 */
package com.lucidaps.web;

import com.google.gson.Gson;
import com.lucidaps.logging.ActionType;
import com.lucidaps.web.EditorTheme;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

public class EditorPageBuilder {
    private static final Gson GSON = new Gson();
    private static final String CLIENT_SCRIPT = EditorPageBuilder.loadClientScript();
    private final String targetUser;
    private final TimeZone timeZone;
    private final String dateLocale;
    private final String accentLight;
    private final String accentDark;
    private final String headerColor;
    private final String filterLabelColor;
    private final String tabBgColor;
    private final String tabTextColor;
    private final String tabActiveBgColor;
    private final String tabActiveTextColor;
    private final String entryLabelColor;
    private final String pluginVersion;
    private final long diskUsed;
    private final long diskTotal;
    private final long dbSizeBytes;
    private final long dbRowCount;
    private static final Map<Character, String> MINECRAFT_COLORS;

    private static String loadClientScript() {
        try (InputStream inputStream = EditorPageBuilder.class.getResourceAsStream("/editor/editor.js")) {
            if (inputStream == null) {
                throw new IOException("Missing bundled editor script");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException iOException) {
            throw new UncheckedIOException(iOException);
        }
    }

    private static String jsString(String string) {
        return GSON.toJson((Object)(string == null ? "" : string));
    }

    static String buildClientConfigScript(String string, String string2, long l, TimeZone timeZone, String string3, boolean bl, Set<String> set) {
        return "const API_URL='/api/logs';\nconst ACCESS_TOKEN=%s;\nconst MASTER_ACTIONS=%s;\nconst INITIAL_USER_FILTER=%s;\nconst SINCE=%d;\nconst TIMEZONE=%s;\nconst DATE_LOCALE=%s;\nconst IS_ALL_USERS_VIEW=%s;\nconst DISABLED_ACTIONS=%s;\nconst ICON_BASE='/icons';\n\nfunction authenticatedUrl(path) {\n  const url = new URL(path, window.location.origin);\n  url.searchParams.set('token', ACCESS_TOKEN);\n  return url;\n}\n".formatted(EditorPageBuilder.jsString(string), GSON.toJson(ActionType.editorNames()), EditorPageBuilder.jsString(string2 == null ? "" : string2), l, EditorPageBuilder.jsString(timeZone.getID()), EditorPageBuilder.jsString(string3), String.valueOf(bl), GSON.toJson(set));
    }

    public EditorPageBuilder(String string, long l, long l2, long l3, long l4, String string2, List<String> list, TimeZone timeZone, String string3, String string4, String string5, String string6, String string7, String string8, String string9, String string10, String string11, String string12) {
        this.pluginVersion = string;
        this.diskUsed = l;
        this.diskTotal = l2;
        this.dbSizeBytes = l3;
        this.dbRowCount = l4;
        this.targetUser = string2;
        this.timeZone = timeZone;
        this.dateLocale = string3;
        this.accentLight = string4;
        this.accentDark = string5;
        this.headerColor = string6;
        this.filterLabelColor = string7;
        this.tabBgColor = string8;
        this.tabTextColor = string9;
        this.tabActiveBgColor = string10;
        this.tabActiveTextColor = string11;
        this.entryLabelColor = string12;
    }

    public EditorPageBuilder(String string, long l, long l2, long l3, long l4, String string2, List<String> list, TimeZone timeZone, String string3, EditorTheme editorTheme) {
        this(string, l, l2, l3, l4, string2, list, timeZone, string3, editorTheme.accentLight(), editorTheme.accentDark(), editorTheme.headerColor(), editorTheme.filterLabelColor(), editorTheme.tabBgColor(), editorTheme.tabTextColor(), editorTheme.tabActiveBgColor(), editorTheme.tabActiveTextColor(), editorTheme.entryLabelColor());
    }

    private String human(long l) {
        int n;
        String[] stringArray = new String[]{"B", "KB", "MB", "GB", "TB"};
        double d = l;
        for (n = 0; d >= 1024.0 && n < stringArray.length - 1; d /= 1024.0, ++n) {
        }
        return new DecimalFormat("#,##0.#").format(d) + " " + stringArray[n];
    }

    private static String escapeHtml(String string) {
        if (string == null) {
            return "";
        }
        return string.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public String buildShellHtml(String string, long l, String string2, String string3, int n, Set<String> set) {
        return this.buildShellHtml(string, l, string2, string3, n, set, EditorPageBuilder.materialOptions(), EditorPageBuilder.enchantmentOptions());
    }

    String buildShellHtml(String string, long l, String string2, String string3, int n, Set<String> set, String string4, String string5) {
        boolean bl = string2 == null || string2.isEmpty();
        String string6 = bl ? "Todos los usuarios" : EditorPageBuilder.escapeHtml(string2);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<!DOCTYPE html><html data-bs-theme=\"dark\"><head><meta charset='UTF-8'>\n");
        stringBuilder.append("<title>ProtectCore · Web Inspector | Ederus Network</title>\n");
        stringBuilder.append("<meta name='viewport' content='width=device-width, initial-scale=1'>\n");
        stringBuilder.append("<link rel='preconnect' href='https://fonts.googleapis.com'>\n");
        stringBuilder.append("<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>\n");
        stringBuilder.append("<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap' rel='stylesheet'>\n");
        stringBuilder.append("<link rel='icon' type='image/png' href='/favicon.png'>\n");
        stringBuilder.append("<link rel='shortcut icon' href='/favicon.ico'>\n");
        stringBuilder.append("<link href='/assets/bootstrap.min.css' rel='stylesheet'>\n");

        String styles = """
        <style>
        :root {
          --ederus-primary: #3b82f6;
          --ederus-primary-glow: rgba(59, 130, 246, 0.35);
          --ederus-bg: #090d16;
          --ederus-card: #111827;
          --ederus-card-inner: #162032;
          --ederus-border: rgba(255, 255, 255, 0.08);
          --ederus-border-hover: rgba(59, 130, 246, 0.4);
          --ederus-text: #f1f5f9;
          --ederus-text-muted: #94a3b8;
          --ederus-font: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
          --ederus-mono: 'JetBrains Mono', monospace;

          --accent-color: #3b82f6;
          --header-color: #60a5fa;
          --filter-label-color: #94a3b8;
          --tab-bg: #162032;
          --tab-text: #94a3b8;
          --tab-active-bg: #2563eb;
          --tab-active-text: #ffffff;
          --entry-label-color: #38bdf8;
          --bs-body-bg-rgb: 9, 13, 22;
          --bs-body-color-rgb: 241, 245, 249;
          --custom-card-bg: #111827;
          --custom-card-border: rgba(255, 255, 255, 0.08);
          --custom-input-bg: #162032;
          --custom-input-color: #f1f5f9;
          --custom-input-border: rgba(255, 255, 255, 0.12);
          --custom-input-placeholder: #64748b;
          --custom-metadata-details-bg: #0d131f;
          --custom-metadata-details-color: #e2e8f0;
          --custom-metadata-details-border: rgba(255, 255, 255, 0.09);
          --custom-secondary-text-color: #94a3b8;
        }

        [data-bs-theme='light'] {
          --ederus-bg: #f8fafc;
          --ederus-card: #ffffff;
          --ederus-card-inner: #f1f5f9;
          --ederus-border: #e2e8f0;
          --ederus-border-hover: #3b82f6;
          --ederus-text: #0f172a;
          --ederus-text-muted: #64748b;

          --bs-body-bg-rgb: 248, 250, 252;
          --bs-body-color-rgb: 15, 23, 42;
          --custom-card-bg: #ffffff;
          --custom-card-border: #e2e8f0;
          --custom-input-bg: #f8fafc;
          --custom-input-color: #0f172a;
          --custom-input-border: #cbd5e1;
          --custom-input-placeholder: #94a3b8;
          --custom-metadata-details-bg: #f1f5f9;
          --custom-metadata-details-color: #1e293b;
          --custom-metadata-details-border: #e2e8f0;
          --custom-secondary-text-color: #64748b;
          --tab-bg: #e2e8f0;
          --tab-text: #475569;
        }

        body {
          font-family: var(--ederus-font);
          background-color: rgb(var(--bs-body-bg-rgb));
          color: rgb(var(--bs-body-color-rgb));
          min-height: 100vh;
          letter-spacing: -0.01em;
          -webkit-font-smoothing: antialiased;
        }

        /* Top Navbar */
        .app-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 1.25rem 0 1rem 0;
          border-bottom: 1px solid var(--ederus-border);
          margin-bottom: 1.5rem;
          flex-wrap: wrap;
          gap: 1rem;
        }

        .brand-container {
          display: flex;
          align-items: center;
          gap: 0.85rem;
        }

        .brand-logo-img {
          width: 44px;
          height: 44px;
          object-fit: contain;
          filter: drop-shadow(0 2px 8px rgba(59, 130, 246, 0.45));
          border-radius: 10px;
          transition: transform 0.2s ease;
        }

        .brand-logo-img:hover {
          transform: scale(1.05);
        }

        .brand-title {
          font-size: 1.35rem;
          font-weight: 700;
          margin: 0;
          line-height: 1.2;
          background: linear-gradient(120deg, #60a5fa 0%, #a78bfa 100%);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
        }

        .brand-subtitle {
          font-size: 0.82rem;
          color: var(--ederus-text-muted);
          margin: 0;
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .status-pill {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 3px 10px;
          border-radius: 20px;
          font-size: 0.75rem;
          font-weight: 600;
          background: rgba(16, 185, 129, 0.12);
          color: #34d399;
          border: 1px solid rgba(16, 185, 129, 0.25);
        }

        .status-dot {
          width: 7px;
          height: 7px;
          border-radius: 50%;
          background-color: #34d399;
          box-shadow: 0 0 8px #34d399;
          animation: pulseDot 2s infinite;
        }

        @keyframes pulseDot {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.4; transform: scale(0.85); }
        }

        .target-user-badge {
          display: inline-flex;
          align-items: center;
          gap: 5px;
          padding: 3px 9px;
          border-radius: 6px;
          font-size: 0.78rem;
          font-weight: 600;
          background: rgba(59, 130, 246, 0.15);
          color: #93c5fd;
          border: 1px solid rgba(59, 130, 246, 0.3);
        }

        /* Container */
        .container {
          max-width: 1540px;
          padding: 0 1.25rem 2rem 1.25rem;
        }

        /* Modern Cards */
        .themed-card, .log-card {
          background-color: var(--custom-card-bg);
          border: 1px solid var(--custom-card-border);
          border-radius: 12px;
          box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18);
          transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        }

        .themed-card:hover {
          border-color: var(--ederus-border-hover);
        }

        /* Stats Panel */
        #statsPanel .themed-card {
          background: linear-gradient(135deg, var(--custom-card-bg) 0%, var(--ederus-card-inner) 100%);
          border: 1px solid var(--ederus-border);
        }

        .stat-badge {
          display: inline-flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.4rem 0.85rem;
          border-radius: 8px;
          background: rgba(255, 255, 255, 0.04);
          border: 1px solid var(--ederus-border);
          font-size: 0.85rem;
        }

        .stat-badge strong {
          color: var(--ederus-primary);
        }

        /* Filter Panel */
        #filterPanel {
          background: var(--custom-card-bg);
          border: 1px solid var(--custom-card-border);
          border-radius: 14px;
          padding: 1.1rem 1.25rem;
          margin-bottom: 1.25rem;
          box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
        }

        .filter-toolbar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding-bottom: 0.75rem;
          border-bottom: 1px solid var(--ederus-border);
          margin-bottom: 0.85rem;
        }

        .filter-title {
          font-size: 0.95rem;
          font-weight: 600;
          margin: 0;
          color: var(--ederus-text);
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        #filterPanel .form-label {
          font-size: 0.82rem;
          font-weight: 600;
          color: var(--ederus-text-muted);
          margin-bottom: 0.35rem;
          text-transform: uppercase;
          letter-spacing: 0.04em;
        }

        #filterPanel .form-control,
        #filterPanel .form-select {
          background-color: var(--custom-input-bg) !important;
          color: var(--custom-input-color) !important;
          border: 1px solid var(--custom-input-border);
          border-radius: 8px;
          font-size: 0.88rem;
          padding: 0.45rem 0.75rem;
          min-height: 38px;
          transition: border-color 0.15s ease, box-shadow 0.15s ease;
        }

        #filterPanel .form-control:focus,
        #filterPanel .form-select:focus {
          border-color: var(--ederus-primary) !important;
          box-shadow: 0 0 0 3px var(--ederus-primary-glow) !important;
          outline: none;
        }

        #filterPanel .form-control::placeholder {
          color: var(--custom-input-placeholder);
          font-size: 0.84rem;
        }

        /* Switches */
        .form-check-input {
          cursor: pointer;
          background-color: var(--custom-input-bg);
          border-color: var(--custom-input-border);
        }

        .form-check-input:checked {
          background-color: var(--ederus-primary);
          border-color: var(--ederus-primary);
        }

        /* Filter Chips */
        .filter-summary {
          display: flex;
          flex-wrap: wrap;
          gap: 0.4rem;
          margin-top: 0.85rem;
          padding-top: 0.75rem;
          border-top: 1px solid var(--ederus-border);
        }

        .filter-chip {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 3px 10px;
          border-radius: 6px;
          background: rgba(59, 130, 246, 0.1);
          color: #93c5fd;
          border: 1px solid rgba(59, 130, 246, 0.25);
          font-size: 0.78rem;
          font-weight: 500;
        }

        /* Category Nav (Tabs) */
        .category-nav {
          display: grid;
          grid-template-columns: 38px minmax(0, 1fr) 38px minmax(11rem, 14rem);
          align-items: center;
          gap: 0.5rem;
          position: sticky;
          top: 0;
          z-index: 100;
          background: rgb(var(--bs-body-bg-rgb));
          padding: 0.65rem 0;
          border-bottom: 1px solid var(--ederus-border);
          margin-bottom: 0.85rem;
          backdrop-filter: blur(10px);
        }

        #logTab {
          display: flex;
          flex-wrap: nowrap;
          overflow-x: auto;
          gap: 0.35rem;
          margin: 0;
          padding: 0 0 4px 0;
          scrollbar-width: thin;
          border-bottom: none;
        }

        #logTab .nav-item {
          flex-shrink: 0;
        }

        #logTab .nav-link {
          background-color: var(--custom-card-bg);
          color: var(--ederus-text-muted);
          border: 1px solid var(--custom-card-border);
          border-radius: 8px;
          padding: 0.4rem 0.85rem;
          font-size: 0.85rem;
          font-weight: 500;
          white-space: nowrap;
          transition: all 0.15s ease;
          display: inline-flex;
          align-items: center;
          gap: 6px;
        }

        #logTab .nav-link:hover {
          background-color: var(--ederus-card-inner);
          color: var(--ederus-text);
          border-color: var(--ederus-border-hover);
        }

        #logTab .nav-link.active {
          background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
          color: #ffffff !important;
          border-color: #3b82f6;
          box-shadow: 0 2px 10px rgba(37, 99, 235, 0.4);
          font-weight: 600;
        }

        .category-nav .tab-scroll {
          border-radius: 8px !important;
          min-height: 36px;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 0;
          border: 1px solid var(--custom-card-border);
          background: var(--custom-card-bg);
          color: var(--ederus-text-muted);
          font-size: 1.2rem;
        }

        .category-nav .tab-scroll:hover:not(:disabled) {
          background: var(--ederus-card-inner);
          color: var(--ederus-text);
          border-color: var(--ederus-border-hover);
        }

        #categoryJump {
          border-radius: 8px;
          background-color: var(--custom-input-bg);
          color: var(--custom-input-color);
          border-color: var(--custom-input-border);
          min-height: 36px;
          font-size: 0.85rem;
        }

        /* Log Cards */
        .log-list {
          display: flex;
          flex-direction: column;
          gap: 8px;
          margin-top: 0.5rem;
        }

        .log-card {
          margin-bottom: 0;
          border-radius: 10px;
          border: 1px solid var(--custom-card-border);
          border-left: 4px solid #64748b;
          background: var(--custom-card-bg);
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.14);
          transition: all 0.15s ease;
        }

        .log-card:hover {
          transform: translateY(-1px);
          box-shadow: 0 4px 16px rgba(0, 0, 0, 0.28);
          border-color: var(--ederus-border-hover);
        }

        /* Action-specific left accent borders */
        .log-card.log-place { border-left-color: #10b981; }
        .log-card.log-break { border-left-color: #ef4444; }
        .log-card.log-pickup, .log-card.log-drop { border-left-color: #0ea5e9; }
        .log-card.log-add, .log-card.log-take { border-left-color: #f59e0b; }
        .log-card.log-cmd { border-left-color: #8b5cf6; }
        .log-card.log-message { border-left-color: #3b82f6; }
        .log-card.log-interact { border-left-color: #eab308; }
        .log-card.rolled-back {
          border-left-style: dashed;
          background: linear-gradient(135deg, var(--custom-card-bg) 95%, rgba(245, 158, 11, 0.08) 100%);
        }

        .log-card .card-body {
          padding: 0.75rem 1rem;
          display: flex;
          align-items: flex-start;
          gap: 0.85rem;
        }

        /* Avatar */
        .log-avatar-wrap {
          flex-shrink: 0;
          position: relative;
        }

        .player-avatar {
          width: 36px;
          height: 36px;
          border-radius: 8px;
          image-rendering: pixelated;
          border: 1px solid var(--ederus-border);
          background-color: var(--ederus-card-inner);
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);
          display: block;
        }

        .log-main {
          flex: 1;
          min-width: 0;
        }

        /* Heading */
        .log-heading {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 0.4rem 0.65rem;
          line-height: 1.4;
          margin-bottom: 0.25rem;
        }

        .log-player {
          font-weight: 700;
          font-size: 0.95rem;
          color: var(--ederus-text);
        }

        /* Badges */
        .badge-bedrock {
          display: inline-flex;
          align-items: center;
          padding: 2px 7px;
          border-radius: 4px;
          font-size: 0.68rem;
          font-weight: 700;
          letter-spacing: 0.04em;
          background: linear-gradient(135deg, #10b981 0%, #059669 100%);
          color: #ffffff;
          box-shadow: 0 1px 4px rgba(16, 185, 129, 0.35);
          vertical-align: middle;
        }

        .action-badge {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 2px 8px;
          border-radius: 5px;
          font-size: 0.78rem;
          font-weight: 600;
          text-transform: capitalize;
        }

        .action-badge.badge-break {
          background: rgba(239, 68, 68, 0.15);
          color: #f87171;
          border: 1px solid rgba(239, 68, 68, 0.3);
        }

        .action-badge.badge-place {
          background: rgba(16, 185, 129, 0.15);
          color: #34d399;
          border: 1px solid rgba(16, 185, 129, 0.3);
        }

        .action-badge.badge-pickup, .action-badge.badge-drop {
          background: rgba(14, 165, 233, 0.15);
          color: #38bdf8;
          border: 1px solid rgba(14, 165, 233, 0.3);
        }

        .action-badge.badge-container {
          background: rgba(245, 158, 11, 0.15);
          color: #fbbf24;
          border: 1px solid rgba(245, 158, 11, 0.3);
        }

        .action-badge.badge-cmd {
          background: rgba(139, 92, 246, 0.15);
          color: #c084fc;
          border: 1px solid rgba(139, 92, 246, 0.3);
        }

        .action-badge.badge-msg {
          background: rgba(59, 130, 246, 0.15);
          color: #60a5fa;
          border: 1px solid rgba(59, 130, 246, 0.3);
        }

        .action-badge.badge-generic {
          background: rgba(148, 163, 184, 0.15);
          color: #cbd5e1;
          border: 1px solid rgba(148, 163, 184, 0.25);
        }

        /* Material Chip */
        .material-chip {
          display: inline-flex;
          align-items: center;
          gap: 5px;
          padding: 2px 7px;
          border-radius: 5px;
          background: rgba(255, 255, 255, 0.05);
          border: 1px solid var(--ederus-border);
          font-size: 0.82rem;
          font-weight: 500;
          color: var(--ederus-text);
        }

        .material-chip .mc-icon {
          width: 18px;
          height: 18px;
          image-rendering: pixelated;
        }

        .material-count {
          color: var(--ederus-primary);
          font-weight: 700;
          font-size: 0.78rem;
        }

        .rolled-badge {
          display: inline-flex;
          align-items: center;
          padding: 2px 7px;
          border-radius: 4px;
          font-size: 0.72rem;
          font-weight: 600;
          background: rgba(245, 158, 11, 0.15);
          color: #fbbf24;
          border: 1px solid rgba(245, 158, 11, 0.3);
        }

        /* Details */
        .log-details {
          font-size: 0.88rem;
          color: var(--ederus-text);
          line-height: 1.45;
          margin-bottom: 0.35rem;
          word-break: break-word;
        }

        /* Facts row */
        .log-facts {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 0.4rem 0.85rem;
          font-size: 0.8rem;
          color: var(--ederus-text-muted);
        }

        .log-time {
          display: inline-flex;
          align-items: center;
          gap: 4px;
        }

        /* World badges */
        .badge-world {
          display: inline-flex;
          align-items: center;
          padding: 1px 6px;
          border-radius: 4px;
          font-size: 0.72rem;
          font-weight: 600;
          text-transform: uppercase;
        }

        .badge-world-overworld {
          background: rgba(34, 197, 94, 0.12);
          color: #4ade80;
          border: 1px solid rgba(34, 197, 94, 0.25);
        }

        .badge-world-nether {
          background: rgba(239, 68, 68, 0.12);
          color: #f87171;
          border: 1px solid rgba(239, 68, 68, 0.25);
        }

        .badge-world-end {
          background: rgba(168, 85, 247, 0.12);
          color: #c084fc;
          border: 1px solid rgba(168, 85, 247, 0.25);
        }

        /* Teleport pill */
        .copy-teleport {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          background: rgba(59, 130, 246, 0.1);
          color: #60a5fa;
          border: 1px solid rgba(59, 130, 246, 0.25);
          border-radius: 5px;
          padding: 2px 7px;
          font-family: var(--ederus-mono);
          font-size: 0.76rem;
          font-weight: 500;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .copy-teleport:hover {
          background: rgba(59, 130, 246, 0.22);
          color: #93c5fd;
          border-color: #3b82f6;
          transform: translateY(-1px);
        }

        /* Controls */
        .log-controls {
          margin-top: 0.45rem;
        }

        .meta-btn {
          font-size: 0.76rem;
          font-weight: 600;
          padding: 0.2rem 0.65rem;
          border-radius: 6px;
          background: var(--ederus-card-inner);
          border: 1px solid var(--ederus-border);
          color: var(--ederus-text-muted);
          transition: all 0.15s ease;
        }

        .meta-btn:hover {
          background: rgba(59, 130, 246, 0.15);
          color: #93c5fd;
          border-color: rgba(59, 130, 246, 0.35);
        }

        .themed-metadata-details {
          background-color: var(--custom-metadata-details-bg);
          border: 1px solid var(--custom-metadata-details-border);
          border-radius: 8px;
          padding: 0.75rem 1rem;
          margin-top: 0.5rem;
          font-size: 0.85rem;
          font-family: var(--ederus-mono);
        }

        /* Result Count & Indicators */
        #resultCount {
          font-size: 0.82rem;
          color: var(--ederus-text-muted);
          font-weight: 500;
          margin-bottom: 0.5rem;
        }

        #loadingIndicator, #endOfLogsIndicator {
          border-radius: 8px;
          background: rgba(59, 130, 246, 0.08);
          border: 1px dashed rgba(59, 130, 246, 0.25);
          padding: 0.75rem;
          margin: 0.85rem 0;
          color: #60a5fa;
          font-weight: 600;
          font-size: 0.9rem;
          text-align: center;
        }

        /* Toast floating notification */
        .copy-notice {
          position: fixed;
          bottom: 24px;
          right: 24px;
          z-index: 10000;
          min-width: 300px;
          max-width: 460px;
          background: #1e293b;
          color: #f8fafc;
          border: 1px solid rgba(59, 130, 246, 0.4);
          border-radius: 10px;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45), 0 0 15px rgba(59, 130, 246, 0.2);
          padding: 0.85rem 1.1rem;
          animation: slideUpToast 0.25s cubic-bezier(0.16, 1, 0.3, 1);
        }

        @keyframes slideUpToast {
          from { opacity: 0; transform: translateY(16px); }
          to { opacity: 1; transform: translateY(0); }
        }

        .copy-notice-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 0.75rem;
        }

        #copyStatus {
          margin: 0;
          font-size: 0.86rem;
          font-weight: 500;
        }

        #copyDismiss {
          background: none;
          border: none;
          color: #94a3b8;
          font-size: 1.25rem;
          line-height: 1;
          cursor: pointer;
          padding: 0;
        }

        #copyDismiss:hover {
          color: #ffffff;
        }

        /* Scrollbars */
        ::-webkit-scrollbar {
          width: 8px;
          height: 8px;
          background: rgba(0, 0, 0, 0.2);
        }

        ::-webkit-scrollbar-thumb {
          background: rgba(255, 255, 255, 0.15);
          border-radius: 6px;
        }

        ::-webkit-scrollbar-thumb:hover {
          background: rgba(255, 255, 255, 0.25);
        }
        </style>
        """;

        stringBuilder.append(styles);

        // Theme toggle script
        stringBuilder.append("""
        <script>
        (function() {
          const getStoredTheme = () => localStorage.getItem('theme');
          const setStoredTheme = theme => localStorage.setItem('theme', theme);
          const getPreferredTheme = () => {
            const stored = getStoredTheme();
            if (stored) return stored;
            return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
          };
          const setTheme = theme => { document.documentElement.setAttribute('data-bs-theme', theme); };
          setTheme(getPreferredTheme());
          window.addEventListener('DOMContentLoaded', () => {
            const themeToggleBtn = document.getElementById('themeToggleBtn');
            if(themeToggleBtn) {
              themeToggleBtn.addEventListener('click', () => {
                const current = document.documentElement.getAttribute('data-bs-theme');
                const next = current === 'dark' ? 'light' : 'dark';
                setStoredTheme(next);
                setTheme(next);
              });
            }
          });
        })();
        </script>
        </head><body><div class='container'>
        """);

        // Sun and Moon SVGs
        String sunSvg = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'><circle cx='12' cy='12' r='5'></circle><line x1='12' y1='1' x2='12' y2='3'></line><line x1='12' y1='21' x2='12' y2='23'></line><line x1='4.22' y1='4.22' x2='5.64' y2='5.64'></line><line x1='18.36' y1='18.36' x2='19.78' y2='19.78'></line><line x1='1' y1='12' x2='3' y2='12'></line><line x1='21' y1='12' x2='23' y2='12'></line><line x1='4.22' y1='19.78' x2='5.64' y2='18.36'></line><line x1='18.36' y1='5.64' x2='19.78' y2='4.22'></line></svg>";
        String moonSvg = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'><path d='M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z'></path></svg>";

        // Header HTML
        String headerHtml = String.format("""
          <header class='app-header'>
            <div class='brand-container'>
              <img src='/assets/ederus-logo.png' alt='Ederus Network' class='brand-logo-img' onerror="this.onerror=null;this.src='/favicon.png';">
              <div>
                <h1 class='brand-title'>Ederus · ProtectCore</h1>
                <div class='brand-subtitle'>
                  <span class='status-pill'><span class='status-dot'></span> Conectado</span>
                  <span>·</span>
                  <span class='target-user-badge'>👤 %s</span>
                </div>
              </div>
            </div>
            <div class='d-flex align-items-center gap-2'>
              <button class='btn btn-sm btn-outline-secondary' type='button' data-bs-toggle='collapse' data-bs-target='#statsPanel' aria-expanded='false'>
                ⚙ Estadísticas
              </button>
              <button class='btn btn-sm btn-outline-secondary' id='themeToggleBtn' type='button' aria-label='Toggle theme' title='Cambiar tema'>
                %s
              </button>
            </div>
          </header>

          <div class='collapse mb-3' id='statsPanel'>
            <div class='card themed-card p-3'>
              <div class='d-flex flex-wrap gap-2'>
                <div class='stat-badge'><strong>Plugin:</strong> %s</div>
                <div class='stat-badge'><strong>Disco:</strong> %s / %s</div>
                <div class='stat-badge'><strong>Tamaño DB:</strong> %s</div>
                <div class='stat-badge'><strong>Registros:</strong> %,d</div>
              </div>
            </div>
          </div>
        """, string6, moonSvg, this.pluginVersion, this.human(this.diskUsed), this.human(this.diskTotal), this.human(this.dbSizeBytes), this.dbRowCount);
        stringBuilder.append(headerHtml);

        // Filter Panel
        stringBuilder.append("""
        <div id='filterPanel' class='themed-card mb-3'>
          <div class='filter-toolbar'>
            <div class='filter-title'>
              <span>🔍 Filtros de Búsqueda</span>
            </div>
            <div class='d-flex gap-2'>
              <button id='filterToggle' class='btn btn-sm btn-outline-secondary' type='button' data-bs-toggle='collapse' data-bs-target='#filterFields' aria-expanded='true'>
                Ocultar
              </button>
              <button id='resetFilters' class='btn btn-sm btn-outline-secondary' type='button'>
                Limpiar Filtros
              </button>
            </div>
          </div>
          <div id='filterFields' class='collapse show'>
            <div class='row gy-3 gx-3'>
        """);

        if (bl) {
            stringBuilder.append("""
              <div class='col-md-6 col-lg-3'>
                <label for='playerNameFilter' class='form-label'>Jugador o UUID:</label>
                <input type='text' id='playerNameFilter' maxlength='36' class='form-control form-control-sm' placeholder='Steve o .BedrockPlayer...'>
              </div>
            """);
        }

        stringBuilder.append("""
              <div class='col-md-6 col-lg-3'>
                <label for='dateFrom' class='form-label'>Desde (Fecha):</label>
                <input type='date' id='dateFrom' class='form-control form-control-sm'>
              </div>
              <div class='col-md-6 col-lg-3'>
                <label for='dateTo' class='form-label'>Hasta (Fecha):</label>
                <input type='date' id='dateTo' class='form-control form-control-sm'>
              </div>
        """);

        stringBuilder.append(String.format("""
              <div id='materialGroup' class='col-md-6 col-lg-3'>
                <label class='form-label' for='itemFilter'>Material:</label>
                <input id='itemFilter' class='form-control form-control-sm' list='minecraftItems' placeholder='minecraft:chest...'>
                <datalist id='minecraftItems'>
                  %s
                </datalist>
              </div>
        """, string4));

        stringBuilder.append("""
              <div id='nameGroup' class='col-md-6 col-lg-3'>
                <label for='nameFilter' class='form-label'>Nombre del Ítem:</label>
                <input type='text' id='nameFilter' class='form-control form-control-sm' placeholder='Parte del nombre...'>
              </div>
        """);

        stringBuilder.append(String.format("""
              <div id='enchantGroup' class='col-md-6 col-lg-3'>
                <label class='form-label'>Encantamientos:</label>
                <div class='dropdown'>
                  <button class='btn btn-outline-secondary btn-sm dropdown-toggle w-100 text-start' type='button' id='enchantDropdown' data-bs-toggle='dropdown' data-bs-auto-close='outside'>
                    Seleccionar encantamientos
                  </button>
                  <div id='enchantDropdownPanel' class='dropdown-menu p-3' style='max-height: 320px; overflow-y: auto;'>
                    <input type='text' id='enchantSearch' class='form-control form-control-sm mb-2' placeholder='Buscar...'>
                    %s
                  </div>
                </div>
              </div>
        """, string5));

        stringBuilder.append("""
              <div id='loreGroup' class='col-md-6 col-lg-3'>
                <label for='loreFilter' class='form-label'>Lore del Ítem:</label>
                <input type='text' id='loreFilter' class='form-control form-control-sm' placeholder='Texto del lore...'>
              </div>
              <div class='col-md-6 col-lg-3'>
                <label class='form-label d-block'>Opciones de Visibilidad:</label>
                <div class='form-check form-switch'>
                  <input class='form-check-input' type='checkbox' id='hideRolled' checked>
                  <label class='form-check-label' for='hideRolled'>Ocultar deshechos (Rollbacks)</label>
                </div>
                <div class='form-check form-switch mt-1'>
                  <input class='form-check-input' type='checkbox' id='hideWE' checked>
                  <label class='form-check-label' for='hideWE'>Ocultar WorldEdit</label>
                </div>
              </div>
              <div id='textGroup' class='col-md-6 col-lg-3' style='display:none'>
                <label for='textFilter' class='form-label'>Buscar texto (Chat / Comandos):</label>
                <input type='text' id='textFilter' class='form-control form-control-sm' placeholder='Palabra clave...'>
              </div>
            </div>
          </div>
          <div id='filterSummary' class='filter-summary' aria-live='polite'></div>
        </div>
        """);

        // Category Navigation
        stringBuilder.append("""
        <nav class='category-nav' id='categoryNav' aria-label='Categorías'>
          <button id='tabsPrevious' class='btn btn-outline-secondary tab-scroll' type='button' aria-label='Scroll anterior' disabled>&lsaquo;</button>
          <ul class='nav nav-tabs' id='logTab' role='tablist'></ul>
          <button id='tabsNext' class='btn btn-outline-secondary tab-scroll' type='button' aria-label='Scroll siguiente' disabled>&rsaquo;</button>
          <div class='category-jump'>
            <label class='visually-hidden' for='categoryJump'>Saltar a categoría</label>
            <select id='categoryJump' class='form-select form-select-sm'></select>
          </div>
        </nav>

        <div id='resultCount' class='mb-2'>Cargando registros...</div>
        <div class='tab-content' id='logTabContent'></div>
        <div id='loadingIndicator' role='status' style='display:none'>Cargando más registros…</div>
        <div id='endOfLogsIndicator' role='status' style='display:none'>Fin de los registros.</div>

        <div id='userMessage' class='alert alert-warning mt-3' role='alert' style='display:none'>
          <span id='userMessageText'></span>
          <button id='retryLogs' type='button' class='btn btn-sm btn-outline-secondary ms-2' hidden>Reintentar</button>
        </div>

        <aside id='copyNotice' class='copy-notice' hidden aria-label='Comando de teletransporte'>
          <div class='copy-notice-header'>
            <p id='copyStatus' role='status' aria-live='polite'></p>
            <button id='copyDismiss' type='button' aria-label='Cerrar'>&times;</button>
          </div>
          <div id='copyFallback' hidden>
            <input id='teleportCommand' class='form-control form-control-sm mt-2' type='text' readonly spellcheck='false'>
            <button id='copyAgain' type='button' class='btn btn-sm btn-outline-primary mt-2 w-100'>Copiar comando</button>
          </div>
        </aside>
        </div> <!-- end container -->
        """);

        stringBuilder.append("<script>\n");
        stringBuilder.append(EditorPageBuilder.buildClientConfigScript(string, string2, l, this.timeZone, this.dateLocale, bl, set));
        stringBuilder.append(CLIENT_SCRIPT);
        stringBuilder.append("</script>\n");
        stringBuilder.append("<script src='/assets/bootstrap.bundle.min.js'></script>");
        stringBuilder.append("</body></html>");
        return stringBuilder.toString();
    }

    private static String materialOptions() {
        StringBuilder stringBuilder = new StringBuilder();
        ArrayList<Material> arrayList = new ArrayList<Material>();
        for (Material material2 : Material.values()) {
            if (!material2.isItem() || material2.name().startsWith("LEGACY")) continue;
            arrayList.add(material2);
        }
        arrayList.sort(Comparator.comparing(material -> material.name().replace('_', ' ').toLowerCase()));
        for (Material material3 : arrayList) {
            stringBuilder.append(String.format("<option value='minecraft:%s'>%s</option>%n", material3.name().toLowerCase(), material3.name().replace('_', ' ')));
        }
        return stringBuilder.toString();
    }

    private static String enchantmentOptions() {
        StringBuilder stringBuilder = new StringBuilder();
        ArrayList<Enchantment> arrayList = new ArrayList<Enchantment>(Arrays.asList(Enchantment.values()));
        arrayList.sort(Comparator.comparing(enchantment -> enchantment.getKey().getKey()));
        for (Enchantment enchantment2 : arrayList) {
            String string = enchantment2.getKey().getKey();
            String string2 = enchantment2.getKey().toString();
            stringBuilder.append(String.format("            <div class='form-check'>\n                <input class='form-check-input enchant-checkbox' type='checkbox' value='%s' id='enchantCheck_%s'>\n                <label class='form-check-label' for='enchantCheck_%s'>%s</label>\n            </div>\n", string, string, string, string2));
        }
        return stringBuilder.toString();
    }

    static {
        HashMap<Character, String> hashMap = new HashMap<Character, String>();
        hashMap.put(Character.valueOf('0'), "#000000");
        hashMap.put(Character.valueOf('1'), "#0000AA");
        hashMap.put(Character.valueOf('2'), "#00AA00");
        hashMap.put(Character.valueOf('3'), "#00AAAA");
        hashMap.put(Character.valueOf('4'), "#AA0000");
        hashMap.put(Character.valueOf('5'), "#AA00AA");
        hashMap.put(Character.valueOf('6'), "#FFAA00");
        hashMap.put(Character.valueOf('7'), "#AAAAAA");
        hashMap.put(Character.valueOf('8'), "#555555");
        hashMap.put(Character.valueOf('9'), "#5555FF");
        hashMap.put(Character.valueOf('a'), "#55FF55");
        hashMap.put(Character.valueOf('b'), "#55FFFF");
        hashMap.put(Character.valueOf('c'), "#FF5555");
        hashMap.put(Character.valueOf('d'), "#FF55FF");
        hashMap.put(Character.valueOf('e'), "#FFFF55");
        hashMap.put(Character.valueOf('f'), "#FFFFFF");
        hashMap.put(Character.valueOf('k'), null);
        hashMap.put(Character.valueOf('l'), null);
        hashMap.put(Character.valueOf('m'), null);
        hashMap.put(Character.valueOf('n'), null);
        MINECRAFT_COLORS = Collections.unmodifiableMap(hashMap);
    }
}
