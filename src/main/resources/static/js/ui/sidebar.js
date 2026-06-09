(function () {
    const ui = window.SmartRentUI = window.SmartRentUI || {};

    ui.sidebar = {
        init(root = document) {
            root.querySelectorAll('[data-sidebar-toggle]').forEach((button) => {
                if (button.dataset.sidebarBound) return;
                button.dataset.sidebarBound = 'true';
                button.addEventListener('click', () => {
                    const target = document.querySelector(button.getAttribute('data-sidebar-toggle'));
                    if (!target) return;
                    const open = target.classList.toggle('is-open');
                    button.setAttribute('aria-expanded', String(open));
                    document.documentElement.classList.toggle('sidebar-open', open);
                });
            });

            root.querySelectorAll('[data-sidebar-close]').forEach((button) => {
                if (button.dataset.sidebarCloseBound) return;
                button.dataset.sidebarCloseBound = 'true';
                button.addEventListener('click', () => {
                    document.querySelectorAll('.is-open[data-sidebar]').forEach((el) => el.classList.remove('is-open'));
                    document.documentElement.classList.remove('sidebar-open');
                });
            });
        }
    };

    document.addEventListener('DOMContentLoaded', () => ui.sidebar.init());
    document.addEventListener('htmx:afterSwap', (event) => ui.sidebar.init(event.target));
})();
