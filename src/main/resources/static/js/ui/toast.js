(function () {
    const ui = window.SmartRentUI = window.SmartRentUI || {};

    ui.createToast = function (message, kind = 'ok', timeout = 3500) {
        const wrap = document.getElementById('toast-stack') || (() => {
            const el = document.createElement('div');
            el.id = 'toast-stack';
            el.className = 'fixed bottom-6 left-1/2 -translate-x-1/2 z-50 flex flex-col gap-2';
            document.body.appendChild(el);
            return el;
        })();
        const toast = document.createElement('div');
        toast.className = 'toast animate-slide-in-right ' + (kind === 'err' ? 'toast-err' : 'toast-ok');
        toast.setAttribute('role', kind === 'err' ? 'alert' : 'status');
        toast.textContent = message;
        wrap.appendChild(toast);
        setTimeout(() => toast.remove(), timeout);
        return toast;
    };
})();
