(function () {
    document.addEventListener('DOMContentLoaded', function () {
        setupCreateTenantForm();

        const root = document.querySelector('[data-tenant-filters]');
        const rows = Array.from(document.querySelectorAll('.tenant-table-row'));
        if (!root || !rows.length) return;

        const searchInput = root.querySelector('[data-tenant-filter-search]');
        const propertySelect = root.querySelector('[data-tenant-filter-property]');
        const occupancySelect = root.querySelector('[data-tenant-filter-occupancy]');
        const statusSelect = root.querySelector('[data-tenant-filter-status]');
        const empty = document.querySelector('[data-tenant-filter-empty]');

        const propertyNames = Array.from(new Set(rows
            .map(function (row) { return (row.dataset.tenantProperty || '').trim(); })
            .filter(Boolean)))
            .sort(function (a, b) { return a.localeCompare(b, 'vi'); });

        propertyNames.forEach(function (name) {
            const option = document.createElement('option');
            option.value = name;
            option.textContent = name;
            propertySelect.appendChild(option);
        });

        function applyFilters() {
            const query = normalize(searchInput.value || '');
            const property = propertySelect.value || '';
            const occupancy = occupancySelect.value || '';
            const status = statusSelect.value || '';
            let visibleCount = 0;

            rows.forEach(function (row) {
                const matchesQuery = !query || normalize(row.dataset.tenantSearch || '').includes(query);
                const matchesProperty = !property || row.dataset.tenantProperty === property;
                const matchesOccupancy = !occupancy || row.dataset.tenantOccupancy === occupancy;
                const matchesStatus = !status || row.dataset.tenantStatus === status;
                const isVisible = matchesQuery && matchesProperty && matchesOccupancy && matchesStatus;

                row.hidden = !isVisible;
                if (isVisible) visibleCount += 1;
            });

            if (empty) empty.hidden = visibleCount > 0;
        }

        [searchInput, propertySelect, occupancySelect, statusSelect].forEach(function (control) {
            control.addEventListener('input', applyFilters);
            control.addEventListener('change', applyFilters);
        });

        applyFilters();
    });

    function setupCreateTenantForm() {
        const form = document.querySelector('[data-create-tenant-form]');
        if (!form) return;

        const fields = {
            fullName: form.querySelector('[data-create-field="fullName"]'),
            email: form.querySelector('[data-create-field="email"]'),
            password: form.querySelector('[data-create-field="password"]'),
            phone: form.querySelector('[data-create-field="phone"]')
        };

        Object.keys(fields).forEach(function (name) {
            const input = fields[name];
            if (!input) return;
            input.addEventListener('input', function () {
                clearFieldError(form, name);
            });
        });

        form.addEventListener('submit', function (event) {
            clearAllErrors(form);

            const errors = validateCreateTenant(fields);
            const firstErrorField = Object.keys(errors)[0];
            if (!firstErrorField) return;

            event.preventDefault();
            Object.keys(errors).forEach(function (name) {
                setFieldError(form, name, errors[name]);
            });
            fields[firstErrorField]?.focus();
        });
    }

    function validateCreateTenant(fields) {
        const errors = {};
        const fullName = (fields.fullName?.value || '').trim();
        const email = (fields.email?.value || '').trim();
        const password = (fields.password?.value || '').trim();
        const phone = (fields.phone?.value || '').trim();
        const digits = phone.replace(/\D+/g, '');

        if (!fullName) {
            errors.fullName = msg('tenants.validation.fullNameRequired', 'Vui lòng nhập họ và tên.');
        }
        if (!email) {
            errors.email = msg('tenants.validation.emailRequired', 'Vui lòng nhập email.');
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            errors.email = msg('tenants.validation.emailInvalid', 'Email chưa đúng định dạng.');
        }
        if (!password) {
            errors.password = msg('tenants.validation.passwordRequired', 'Vui lòng nhập mật khẩu.');
        } else if (password.length < 8 || password.length > 64) {
            errors.password = msg('tenants.validation.passwordLength', 'Mật khẩu phải từ 8 đến 64 ký tự.');
        }
        if (!phone) {
            errors.phone = msg('tenants.validation.phoneRequired', 'Vui lòng nhập số điện thoại.');
        } else if (digits.length < 8) {
            errors.phone = msg('tenants.validation.phoneInvalid', 'Số điện thoại chưa đúng định dạng.');
        }

        return errors;
    }

    function setFieldError(form, name, message) {
        const error = form.querySelector('[data-create-error-for="' + name + '"]');
        const input = form.querySelector('[data-create-field="' + name + '"]');
        if (error) {
            error.textContent = message;
            error.classList.remove('hidden');
        }
        input?.setAttribute('aria-invalid', 'true');
        input?.closest('.input-glass')?.classList.add('is-invalid');
    }

    function clearFieldError(form, name) {
        const error = form.querySelector('[data-create-error-for="' + name + '"]');
        const input = form.querySelector('[data-create-field="' + name + '"]');
        if (error) {
            error.textContent = '';
            error.classList.add('hidden');
        }
        input?.removeAttribute('aria-invalid');
        input?.closest('.input-glass')?.classList.remove('is-invalid');
    }

    function clearAllErrors(form) {
        ['fullName', 'email', 'password', 'phone'].forEach(function (name) {
            clearFieldError(form, name);
        });
    }

    function normalize(value) {
        return String(value || '')
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd')
            .replace(/Đ/g, 'd')
            .toLowerCase()
            .replace(/\s+/g, ' ')
            .trim();
    }

    function msg(key, fallback) {
        return window.SmartRentI18n ? window.SmartRentI18n.t(key, {}, fallback) : fallback;
    }
})();
