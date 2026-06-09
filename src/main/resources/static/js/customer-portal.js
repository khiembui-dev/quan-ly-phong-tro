(function () {
    function textFromTarget(id) {
        return document.getElementById(id)?.textContent?.trim() || '';
    }

    document.querySelectorAll('[data-copy-target]').forEach((button) => {
        if (button.closest('[data-payment-version="bank-v2"]')) return;
        button.addEventListener('click', async () => {
            const value = textFromTarget(button.dataset.copyTarget);
            if (!value) return;
            try {
                await navigator.clipboard.writeText(value);
                const original = button.textContent;
                button.textContent = 'Đã sao chép';
                window.setTimeout(() => { button.textContent = original; }, 1400);
            } catch (error) {
                window.toast?.('Không thể sao chép. Vui lòng chọn và sao chép thủ công.', 'err');
            }
        });
    });

    const checkout = document.querySelector('[data-payment-checkout="true"]');
    if (checkout && checkout.dataset.paymentVersion !== 'bank-v2') {
        const state = document.getElementById('payment-check-state');
        const checkButtons = Array.from(checkout.querySelectorAll('[data-payment-check]'));

        function renderPaymentState(status, message) {
            if (!state) return;
            const paid = status === 'PAID';
            const error = status === 'ERROR' || status === 'UNAVAILABLE';
            state.className = 'customer-payment-state ' + (paid ? 'is-success' : (error ? 'is-error' : 'is-pending'));
            state.innerHTML = [
                `<i data-lucide="${paid ? 'badge-check' : (error ? 'circle-alert' : 'clock-3')}"></i>`,
                '<div>',
                `<b>${paid ? 'Thanh toán thành công' : (error ? 'Chưa thể kiểm tra' : 'Chưa thấy giao dịch')}</b>`,
                `<span>${message || ''}</span>`,
                '</div>'
            ].join('');
            window.lucide?.createIcons({ attrs: { 'stroke-width': 1.7 } });
        }

        async function checkPayment() {
            const url = checkout.dataset.checkUrl;
            if (!url) return;
            checkButtons.forEach((button) => {
                button.disabled = true;
                button.classList.add('is-loading');
            });
            renderPaymentState('PENDING', 'Đang kiểm tra lịch sử giao dịch...');
            try {
                const response = await fetch(url, {
                    method: 'GET',
                    credentials: 'include',
                    headers: { Accept: 'application/json' }
                });
                const payload = await response.json();
                if (!response.ok || !payload.success) throw new Error(payload.message || 'check_failed');
                const result = payload.data || {};
                renderPaymentState(result.status, result.message);
                if (result.status === 'PAID') {
                    checkButtons.forEach((button) => { button.hidden = true; });
                    window.setTimeout(() => window.location.reload(), 1000);
                }
            } catch (error) {
                renderPaymentState('ERROR', 'Có lỗi khi kiểm tra. Vui lòng thử lại sau.');
            } finally {
                checkButtons.forEach((button) => {
                    button.disabled = false;
                    button.classList.remove('is-loading');
                });
            }
        }

        checkButtons.forEach((button) => button.addEventListener('click', checkPayment));
    }

    const filterRoot = document.querySelector('[data-notification-filters]');
    if (filterRoot) {
        const items = Array.from(document.querySelectorAll('[data-notification-type]'));
        const empty = document.querySelector('[data-notification-empty]');
        filterRoot.querySelectorAll('[data-notification-filter]').forEach((button) => {
            button.addEventListener('click', () => {
                const filter = button.dataset.notificationFilter;
                filterRoot.querySelectorAll('[data-notification-filter]').forEach((item) => {
                    item.classList.toggle('is-active', item === button);
                });
                let visible = 0;
                items.forEach((item) => {
                    const show = filter === 'all' || item.dataset.notificationType === filter;
                    item.hidden = !show;
                    if (show) visible += 1;
                });
                if (empty) empty.hidden = visible !== 0;
            });
        });
    }
})();
