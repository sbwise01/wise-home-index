package com.bradandmarsha.wisehomeindex.rest;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;

import java.util.List;

/**
 * Renders the index page HTML. Kept dependency-free (plain string building) so
 * the page can be served directly from a JAX-RS resource without a templating
 * engine.
 */
final class HtmlRenderer {

    /** Default tile image served as a static asset when an application omits one. */
    static final String DEFAULT_IMAGE = "/images/default-tile.svg";

    private HtmlRenderer() {
    }

    static String render(List<ApplicationEntry> applications, boolean privateScope) {
        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"en\">\n")
            .append("<head>\n")
            .append("  <meta charset=\"utf-8\">\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            .append("  <title>wise-k8s home index</title>\n")
            .append("  <style>\n")
            .append(css())
            .append("  </style>\n")
            .append("</head>\n")
            .append("<body>\n")
            .append("  <header class=\"page-header\">\n")
            .append("    <h1>wise-k8s home</h1>\n")
            .append("    <p class=\"subtitle\">Services hosted on the homelab cluster</p>\n")
            .append("    <span class=\"scope-badge ").append(privateScope ? "scope-private" : "scope-public").append("\">")
            .append(privateScope ? "Private network &mdash; showing all services" : "Public access &mdash; showing public services")
            .append("</span>\n")
            .append("  </header>\n")
            .append("  <main class=\"grid\">\n");

        if (applications.isEmpty()) {
            html.append("    <p class=\"empty\">No applications are configured to display.</p>\n");
        } else {
            for (ApplicationEntry app : applications) {
                html.append(tile(app));
            }
        }

        html.append("  </main>\n")
            .append("  <footer class=\"page-footer\">home.bradandmarsha.com</footer>\n")
            .append("</body>\n")
            .append("</html>\n");
        return html.toString();
    }

    private static String tile(ApplicationEntry app) {
        String image = app.getImage() != null && !app.getImage().isBlank()
                ? app.getImage().trim()
                : DEFAULT_IMAGE;
        String visibilityClass = app.isPublic() ? "tile-public" : "tile-private";
        String visibilityLabel = app.isPublic() ? "public" : "private";

        return "    <a class=\"tile " + visibilityClass + "\" href=\"" + escapeAttr(app.getUrl()) + "\">\n"
                + "      <img class=\"tile-image\" src=\"" + escapeAttr(image) + "\" alt=\"" + escapeAttr(app.getName()) + " logo\" "
                + "onerror=\"this.onerror=null;this.src='" + DEFAULT_IMAGE + "';\">\n"
                + "      <span class=\"tile-name\">" + escapeText(app.getName()) + "</span>\n"
                + "      <span class=\"tile-visibility\">" + visibilityLabel + "</span>\n"
                + "    </a>\n";
    }

    private static String css() {
        return """
                :root {
                  --bg: #0f172a;
                  --bg-card: #1e293b;
                  --bg-card-hover: #334155;
                  --text: #e2e8f0;
                  --muted: #94a3b8;
                  --accent: #38bdf8;
                  --public: #22c55e;
                  --private: #f59e0b;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                  background: var(--bg);
                  color: var(--text);
                  min-height: 100vh;
                }
                .page-header {
                  text-align: center;
                  padding: 2.5rem 1rem 1.5rem;
                }
                .page-header h1 { margin: 0; font-size: 2rem; letter-spacing: -0.5px; }
                .subtitle { margin: 0.25rem 0 1rem; color: var(--muted); }
                .scope-badge {
                  display: inline-block;
                  padding: 0.35rem 0.85rem;
                  border-radius: 999px;
                  font-size: 0.8rem;
                  font-weight: 600;
                }
                .scope-public { background: rgba(34,197,94,0.15); color: var(--public); }
                .scope-private { background: rgba(245,158,11,0.15); color: var(--private); }
                .grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
                  gap: 1.25rem;
                  max-width: 1100px;
                  margin: 0 auto;
                  padding: 1rem 1.5rem 3rem;
                }
                .tile {
                  position: relative;
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  justify-content: center;
                  gap: 0.75rem;
                  background: var(--bg-card);
                  border: 1px solid rgba(148,163,184,0.12);
                  border-radius: 16px;
                  padding: 1.5rem 1rem;
                  text-decoration: none;
                  color: var(--text);
                  transition: transform 0.15s ease, background 0.15s ease, border-color 0.15s ease;
                }
                .tile:hover {
                  transform: translateY(-4px);
                  background: var(--bg-card-hover);
                  border-color: var(--accent);
                }
                .tile-image {
                  width: 64px;
                  height: 64px;
                  object-fit: contain;
                  border-radius: 12px;
                }
                .tile-name { font-weight: 600; text-align: center; }
                .tile-visibility {
                  position: absolute;
                  top: 0.6rem;
                  right: 0.6rem;
                  font-size: 0.65rem;
                  text-transform: uppercase;
                  letter-spacing: 0.04em;
                  padding: 0.15rem 0.45rem;
                  border-radius: 999px;
                }
                .tile-public .tile-visibility { background: rgba(34,197,94,0.18); color: var(--public); }
                .tile-private .tile-visibility { background: rgba(245,158,11,0.18); color: var(--private); }
                .empty { grid-column: 1 / -1; text-align: center; color: var(--muted); }
                .page-footer {
                  text-align: center;
                  color: var(--muted);
                  font-size: 0.8rem;
                  padding: 1.5rem 1rem 2.5rem;
                }
                """;
    }

    private static String escapeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeAttr(String value) {
        if (value == null) {
            return "";
        }
        return escapeText(value)
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
