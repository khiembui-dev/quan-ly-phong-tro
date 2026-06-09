(function () {
    const VND = new Intl.NumberFormat('vi-VN');

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-billing-page]').forEach(initBillingPage);
        document.querySelectorAll('[data-billing-detail]').forEach(initBillingDetail);
    });

    function initBillingPage(page) {
        const search = page.querySelector('[data-billing-search]');
        const rows = Array.from(page.querySelectorAll('[data-billing-row]'));
        const visibleCount = page.querySelector('[data-billing-visible-count]');
        const clientEmpty = page.querySelector('[data-billing-client-empty]');

        function applySearchFilter() {
            const needle = normalize(search ? search.value : '');
            let visible = 0;
            rows.forEach(function (row) {
                const matched = !needle || normalize(row.dataset.search || '').includes(needle);
                row.hidden = !matched;
                if (matched) visible += 1;
            });
            if (visibleCount) visibleCount.textContent = String(visible);
            if (clientEmpty) {
                clientEmpty.hidden = rows.length === 0 || visible !== 0;
            }
        }

        if (search) {
            search.addEventListener('input', applySearchFilter);
            applySearchFilter();
        } else if (clientEmpty) {
            clientEmpty.hidden = true;
        }

        page.querySelectorAll('[data-billing-auto-submit]').forEach(function (control) {
            control.addEventListener('change', function () {
                const form = control.closest('form');
                if (form) form.submit();
            });
        });
    }

    function initBillingDetail(detail) {
        initBillingRow(detail);
    }

    function initBillingRow(root) {
        const meterInputs = Array.from(root.querySelectorAll('[data-meter-input]'));
        if (!meterInputs.length) return;

        meterInputs.forEach(function (input) {
            sanitizeNumberInput(input);
            input.addEventListener('input', function () {
                sanitizeNumberInput(input);
                recalc(root);
            });
        });
        const note = root.querySelector('textarea[name="note"]');
        if (note) {
            note.addEventListener('input', function () {
                recalc(root);
            });
        }

        root.querySelectorAll('[data-billing-reading-form], [data-billing-detail-form], [data-issue-invoice-form]').forEach(function (form) {
            form.addEventListener('submit', function (event) {
                recalc(root);
                if (root.classList.contains('has-meter-error')) {
                    event.preventDefault();
                    toastError(errorTarget(root)?.textContent || 'Vui lòng kiểm tra chỉ số điện nước.');
                    return;
                }
                const button = event.submitter || form.querySelector('button[type="submit"]');
                if (button) {
                    button.disabled = true;
                    button.classList.add('is-loading');
                    button.innerHTML = '<span class="billing-spinner"></span> Đang lưu';
                }
            });
        });

        recalc(root);
    }

    function recalc(root) {
        const electricPrevInput = root.querySelector('[data-meter-input="electric-prev"]');
        const waterPrevInput = root.querySelector('[data-meter-input="water-prev"]');
        const electricInput = root.querySelector('[data-meter-input="electric"]');
        const waterInput = root.querySelector('[data-meter-input="water"]');
        if (!electricInput || !waterInput) return;

        const electricPrev = number(electricPrevInput ? electricPrevInput.value : root.dataset.electricPrev);
        const waterPrev = number(waterPrevInput ? waterPrevInput.value : root.dataset.waterPrev);
        const electricCurr = number(electricInput.value);
        const waterCurr = number(waterInput.value);
        const electricUnit = number(root.dataset.electricUnit);
        const waterUnit = number(root.dataset.waterUnit);
        const rent = number(root.dataset.rent);
        const fixedFee = number(root.dataset.fixedFee);
        const discount = number(root.dataset.discount);
        const lateFee = number(root.dataset.lateFee);
        const occupied = root.dataset.occupied !== 'false';

        const errors = [];
        if (isBlank(electricPrevInput ? electricPrevInput.value : root.dataset.electricPrev)) errors.push('Vui lòng nhập điện cũ.');
        if (isBlank(waterPrevInput ? waterPrevInput.value : root.dataset.waterPrev)) errors.push('Vui lòng nhập nước cũ.');
        if (isBlank(electricInput.value)) errors.push('Vui lòng nhập điện mới.');
        if (isBlank(waterInput.value)) errors.push('Vui lòng nhập nước mới.');
        if (electricCurr < electricPrev) errors.push('Điện mới không được nhỏ hơn điện cũ.');
        if (waterCurr < waterPrev) errors.push('Nước mới không được nhỏ hơn nước cũ.');

        const electricUsage = Math.max(0, electricCurr - electricPrev);
        const waterUsage = Math.max(0, waterCurr - waterPrev);
        const electricAmount = Math.round(electricUsage * electricUnit);
        const waterAmount = Math.round(waterUsage * waterUnit);
        const total = occupied ? Math.max(0, rent + fixedFee + electricAmount + waterAmount + lateFee - discount) : 0;

        setText(root, '[data-electric-usage]', VND.format(electricUsage));
        setText(root, '[data-water-usage]', VND.format(waterUsage));
        setText(root, '[data-electric-amount]', formatVnd(electricAmount));
        setText(root, '[data-water-amount]', formatVnd(waterAmount));
        setText(root, '[data-total-amount]', formatVnd(total));
        syncIssueForm(root, {
            electricPrev,
            electricCurr,
            waterPrev,
            waterCurr,
            valid: errors.length === 0 && occupied,
        });

        const error = errorTarget(root);
        root.classList.toggle('has-meter-error', errors.length > 0);
        if (error) {
            error.textContent = errors[0] || '';
            error.hidden = errors.length === 0;
        }
    }

    function syncIssueForm(root, values) {
        const form = root.querySelector('[data-issue-invoice-form]');
        if (!form) return;
        setIssueField(form, 'electricPrev', values.electricPrev);
        setIssueField(form, 'electricCurr', values.electricCurr);
        setIssueField(form, 'waterPrev', values.waterPrev);
        setIssueField(form, 'waterCurr', values.waterCurr);
        const note = root.querySelector('textarea[name="note"]');
        setIssueField(form, 'note', note ? note.value : '');
        const button = form.querySelector('[data-issue-button]');
        if (button) {
            button.disabled = !values.valid;
            button.title = values.valid ? '' : 'Nhập đủ chỉ số điện nước hợp lệ trước khi tạo hóa đơn';
        }
    }

    function setIssueField(form, name, value) {
        const field = form.querySelector(`[data-issue-field="${name}"]`);
        if (field) field.value = value == null ? '' : String(value);
    }

    function sanitizeNumberInput(input) {
        const before = input.value;
        const cleaned = before.replace(/[^\d]/g, '').replace(/^0+(?=\d)/, '');
        if (before !== cleaned) input.value = cleaned;
    }

    function setText(root, selector, value) {
        root.querySelectorAll(selector).forEach(function (node) {
            node.textContent = value;
        });
    }

    function errorTarget(root) {
        return root.querySelector('[data-row-error]');
    }

    function isBlank(value) {
        return value == null || String(value).trim() === '';
    }

    function number(value) {
        if (value == null || value === '') return 0;
        const parsed = Number(String(value).replace(/[^\d.-]/g, ''));
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function formatVnd(value) {
        return VND.format(Math.round(number(value))) + ' ₫';
    }

    function normalize(value) {
        return String(value || '')
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function toastError(message) {
        if (typeof window.toast === 'function') window.toast(message, 'err');
        else window.alert(message);
    }
})();
