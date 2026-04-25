package ai.factoredui.compose.testing

/**
 * wasmJs actual: maintain a hidden DOM container of `data-fui-*` nodes for
 * external introspection (Playwright, screen readers via aria-label, agents).
 *
 * The container `<div id="fui-shadow" style="display:none">` is created
 * lazily on first emit and appended to <body>. Every emit upserts a single
 * `<div data-fui-id="..." data-fui-role="..." data-fui-...="...">` keyed by id.
 *
 * `display:none` keeps it invisible to users but Playwright's selectors
 * still match (querySelector ignores display state).
 */
actual object DomShadow {

    actual fun emit(id: String, role: String, attrs: Map<String, String?>) {
        // Flatten the attrs map into a single packed string.
        // Separator is ASCII unit-separator (0x1F) — never appears in
        // SDUI ids/roles/values, so the JS side splits on it safely.
        val sep = ''
        val flat = buildString {
            append(id).append(sep).append(role)
            for ((k, v) in attrs) {
                if (v == null) continue
                append(sep).append(k.lowercase()).append(sep).append(v)
            }
        }
        jsEmitShadow(flat)
    }

    actual fun remove(id: String) {
        jsRemoveShadow(id)
    }
}

@Suppress("UNUSED_PARAMETER")
private fun jsEmitShadow(packed: String): Unit = js(
    """
    (() => {
      const sep = String.fromCharCode(31);  // unit separator
      const parts = packed.split(sep);
      const id = parts[0];
      const role = parts[1];
      let host = document.getElementById('fui-shadow');
      if (!host) {
        host = document.createElement('div');
        host.id = 'fui-shadow';
        host.style.display = 'none';
        document.body.appendChild(host);
      }
      let el = host.querySelector('[data-fui-id="' + CSS.escape(id) + '"]');
      if (!el) {
        el = document.createElement('div');
        el.setAttribute('data-fui-id', id);
        host.appendChild(el);
      }
      el.setAttribute('data-fui-role', role);
      // Wipe stale data-fui-* attrs except id/role, then re-set from parts.
      for (const a of Array.from(el.attributes)) {
        if (a.name.startsWith('data-fui-') && a.name !== 'data-fui-id' && a.name !== 'data-fui-role') {
          el.removeAttribute(a.name);
        }
      }
      for (let i = 2; i + 1 < parts.length; i += 2) {
        el.setAttribute('data-fui-' + parts[i], parts[i + 1]);
      }
    })()
    """,
)

@Suppress("UNUSED_PARAMETER")
private fun jsRemoveShadow(id: String): Unit = js(
    """
    (() => {
      const host = document.getElementById('fui-shadow');
      if (!host) return;
      const el = host.querySelector('[data-fui-id="' + CSS.escape(id) + '"]');
      if (el) el.remove();
    })()
    """,
)
