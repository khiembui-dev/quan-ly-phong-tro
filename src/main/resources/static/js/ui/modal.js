(function () {
    const ui = window.SmartRentUI = window.SmartRentUI || {};

    function openModal(id) {
        const modal = document.getElementById(id);
        if (!modal) return;
        modal.classList.add('is-open');
        modal.removeAttribute('hidden');
        document.documentElement.classList.add('modal-open');
        const focusTarget = modal.querySelector('[autofocus], button, input, select, textarea, a[href]');
        if (focusTarget) focusTarget.focus({ preventScroll: true });
    }

    function closeModal(modal) {
        if (!modal) return;
        modal.classList.remove('is-open');
        modal.setAttribute('hidden', '');
        document.documentElement.classList.remove('modal-open');
    }

    ui.modal = {
        open: openModal,
        close: closeModal,
        init(root = document) {
            root.querySelectorAll('[data-modal-open]').forEach((button) => {
                if (button.dataset.modalOpenBound) return;
                button.dataset.modalOpenBound = 'true';
                button.addEventListener('click', () => openModal(button.getAttribute('data-modal-open')));
            });

            root.querySelectorAll('[data-modal-close]').forEach((button) => {
                if (button.dataset.modalCloseBound) return;
                button.dataset.modalCloseBound = 'true';
                button.addEventListener('click', () => closeModal(button.closest('.modal, [role="dialog"]')));
            });
        }
    };

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            document.querySelectorAll('.modal.is-open, [role="dialog"].is-open').forEach(closeModal);
        }
    });
    document.addEventListener('DOMContentLoaded', () => ui.modal.init());
    document.addEventListener('htmx:afterSwap', (event) => ui.modal.init(event.target));
})();
