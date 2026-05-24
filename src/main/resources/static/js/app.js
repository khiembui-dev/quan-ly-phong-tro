// Global app bootstrap — runs on every page.
// Note: ai-assistant.js is loaded separately as a plain <script> in the layout
// (we keep things simple without an ES module bundler).

(function () {
    const initIcons = () => {
        if (window.lucide && typeof window.lucide.createIcons === 'function') {
            window.lucide.createIcons({ attrs: { 'stroke-width': 1.6 } });
        }
    };
    initIcons();
    document.addEventListener('htmx:afterSwap', initIcons);
    document.addEventListener('alpine:initialized', initIcons);

    // Re-render Lucide icons when new <i data-lucide> markers enter the DOM
    // (e.g. Alpine x-for adding rows after addFee()/addCustomAmenity()).
    // CRITICAL: only match <i> tags — Lucide replaces them with <svg data-lucide>,
    // and matching those would create an infinite mutation loop that swallows clicks.
    const hasLucidePlaceholder = (node) => {
        if (node.nodeType !== 1) return false;
        if (node.tagName === 'I' && node.hasAttribute('data-lucide')) return true;
        return typeof node.querySelector === 'function' && !!node.querySelector('i[data-lucide]');
    };
    let iconRafPending = false;
    const iconObserver = new MutationObserver((mutations) => {
        if (iconRafPending) return;
        outer: for (const m of mutations) {
            for (const n of m.addedNodes) {
                if (hasLucidePlaceholder(n)) {
                    iconRafPending = true;
                    break outer;
                }
            }
        }
        if (!iconRafPending) return;
        requestAnimationFrame(() => {
            iconRafPending = false;
            initIcons();
        });
    });
    const startIconObserver = () => iconObserver.observe(document.body, { childList: true, subtree: true });
    if (document.body) startIconObserver();
    else document.addEventListener('DOMContentLoaded', startIconObserver, { once: true });

    // Toast helper
    window.toast = function (message, kind = 'ok', timeout = 3500) {
        const wrap = document.getElementById('toast-stack') || (() => {
            const el = document.createElement('div');
            el.id = 'toast-stack';
            el.className = 'fixed bottom-6 left-1/2 -translate-x-1/2 z-50 flex flex-col gap-2';
            document.body.appendChild(el);
            return el;
        })();
        const t = document.createElement('div');
        t.className = 'toast ' + (kind === 'err' ? 'toast-err' : 'toast-ok');
        t.textContent = message;
        wrap.appendChild(t);
        setTimeout(() => t.remove(), timeout);
    };

    // CSRF helper for fetch
    const csrf = document.querySelector('meta[name="csrf-token"]');
    if (csrf && csrf.content) {
        const orig = window.fetch;
        window.fetch = function (input, init) {
            init = init || {};
            const method = (init.method || 'GET').toUpperCase();
            if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
                init.headers = init.headers || {};
                init.headers['X-CSRF-TOKEN'] = csrf.content;
            }
            return orig.call(this, input, init);
        };
    }

    // Format VND in browser (for client-side dynamic values)
    window.fmtVnd = function (n) {
        if (n == null) return '0₫';
        return new Intl.NumberFormat('vi-VN').format(Math.round(n)) + '₫';
    };
})();
