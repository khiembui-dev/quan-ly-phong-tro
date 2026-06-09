(function () {
    const ui = window.SmartRentUI = window.SmartRentUI || {};

    ui.filter = {
        init(root = document) {
            root.querySelectorAll('[data-filter-toggle]').forEach((button) => {
                if (button.dataset.filterBound) return;
                button.dataset.filterBound = 'true';
                button.addEventListener('click', () => {
                    const panel = document.querySelector(button.getAttribute('data-filter-toggle'));
                    if (!panel) return;
                    const open = panel.toggleAttribute('data-open');
                    panel.hidden = !open;
                    button.setAttribute('aria-expanded', String(open));
                });
            });
        }
    };

    document.addEventListener('DOMContentLoaded', () => ui.filter.init());
    document.addEventListener('htmx:afterSwap', (event) => ui.filter.init(event.target));
})();
