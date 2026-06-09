(function () {
    const ADDRESS_API = 'https://provinces.open-api.vn/api/v1';
    const currency = new Intl.NumberFormat('vi-VN');
    const moneyFields = {
        electricityPrice: { suffix: '/ kWh' },
        waterPrice: { suffix: '/ m³' },
        serviceFee: { suffix: '/ tháng' },
        internetFee: { suffix: '/ tháng' },
        trashFee: { suffix: '/ tháng' },
        managementFee: { suffix: '/ tháng' }
    };

    document.addEventListener('DOMContentLoaded', function () {
        if (window.SmartRentI18n && !window.SmartRentI18n.state.ready) {
            window.addEventListener('smartrent:i18n-ready', init, { once: true });
            return;
        }
        init();
    });

    function init() {
        initPropertyFilters();
        document.querySelectorAll('[data-property-create-form]').forEach(initPropertyForm);
    }

    function t(key, fallback, params) {
        if (window.SmartRentI18n && typeof window.SmartRentI18n.t === 'function') {
            return window.SmartRentI18n.t(key, params || {}, fallback);
        }
        return fallback;
    }

    function initPropertyFilters() {
        const page = document.querySelector('[data-properties-page]');
        if (!page) return;

        const search = page.querySelector('[data-property-search]');
        const filters = Array.from(page.querySelectorAll('[data-property-filter]'));
        const cards = Array.from(page.querySelectorAll('[data-property-card]'));
        const noResults = page.querySelector('[data-property-no-results]');
        let activeFilter = 'all';

        const apply = function () {
            const q = normalize(search ? search.value : '');
            let visibleCount = 0;

            cards.forEach(function (card) {
                const haystack = normalize(card.dataset.search || '');
                const matchesSearch = !q || haystack.includes(q);
                const matchesFilter =
                    activeFilter === 'all' ||
                    (activeFilter === 'available' && card.dataset.hasAvailable === 'true') ||
                    (activeFilter === 'active' && card.dataset.active === 'true');
                const visible = matchesSearch && matchesFilter;
                card.hidden = !visible;
                if (visible) visibleCount += 1;
            });

            if (noResults) noResults.hidden = visibleCount > 0;
        };

        if (search) search.addEventListener('input', apply);
        filters.forEach(function (btn) {
            btn.addEventListener('click', function () {
                activeFilter = btn.dataset.propertyFilter || 'all';
                filters.forEach(function (item) { item.classList.toggle('active', item === btn); });
                apply();
            });
        });

        apply();
    }

    function initPropertyForm(form) {
        const provinceSelect = form.querySelector('[data-province-select]');
        const districtSelect = form.querySelector('[data-district-select]');
        const wardSelect = form.querySelector('[data-ward-select]');
        const provinceName = form.querySelector('[data-province-name]');
        const districtName = form.querySelector('[data-district-name]');
        const wardName = form.querySelector('[data-ward-name]');
        const fullAddress = form.querySelector('[data-full-address]');
        const addressPreview = form.querySelector('[data-address-preview]');
        const addressStatus = form.querySelector('[data-address-status]');
        const submitButton = form.querySelector('[data-submit-button]');
        const addExtraButton = form.querySelector('[data-add-extra-fee]');
        const extraFeesList = form.querySelector('[data-extra-fees-list]');
        const hasAddressDropdowns = Boolean(provinceSelect && districtSelect && wardSelect);

        const initial = {
            provinceCode: form.dataset.provinceCode || '',
            provinceName: form.dataset.provinceName || '',
            districtCode: form.dataset.districtCode || '',
            districtName: form.dataset.districtName || '',
            wardCode: form.dataset.wardCode || '',
            wardName: form.dataset.wardName || ''
        };

        setHidden(provinceName, initial.provinceName);
        setHidden(districtName, initial.districtName);
        setHidden(wardName, initial.wardName);
        initNumericFields(form);

        if (hasAddressDropdowns) {
            setAddressStatus(t('properties.form.loadingCities', 'Loading province/city list...'));
            loadProvinces()
                .then(function (provinces) {
                    fillOptions(provinceSelect, provinces, t('properties.form.chooseCity', 'Choose province/city'), initial.provinceCode);
                    provinceSelect.disabled = false;
                    refreshPrettySelect(provinceSelect);
                    syncSelectedName(provinceSelect, provinceName);
                    if (initial.provinceCode) return loadDistricts(initial.provinceCode, initial.districtCode);
                    setAddressStatus('');
                    return null;
                })
                .then(function () {
                    if (initial.districtCode) return loadWards(initial.districtCode, initial.wardCode);
                    return null;
                })
                .then(function () {
                    syncSelectedName(districtSelect, districtName);
                    syncSelectedName(wardSelect, wardName);
                    updateFullAddress();
                    setAddressStatus('');
                })
                .catch(function () {
                    setAddressStatus(t('properties.form.addressLoadFailed', 'Could not load address data. Please try again later.'), true);
                });

            provinceSelect.addEventListener('change', function () {
                clearError('provinceCode', form);
                syncSelectedName(provinceSelect, provinceName);
                resetSelect(districtSelect, t('properties.form.chooseDistrict', 'Choose district'));
                resetSelect(wardSelect, t('properties.form.chooseWard', 'Choose ward'));
                setHidden(districtName, '');
                setHidden(wardName, '');
                updateFullAddress();

                if (!provinceSelect.value) return;
                setAddressStatus(t('properties.form.loadingDistricts', 'Loading districts...'));
                loadDistricts(provinceSelect.value, '')
                    .then(function () { setAddressStatus(''); })
                    .catch(function () {
                        setAddressStatus(t('properties.form.districtLoadFailed', 'Could not load districts. Please try again.'), true);
                    });
            });

            districtSelect.addEventListener('change', function () {
                clearError('districtCode', form);
                syncSelectedName(districtSelect, districtName);
                resetSelect(wardSelect, t('properties.form.chooseWard', 'Choose ward'));
                setHidden(wardName, '');
                updateFullAddress();

                if (!districtSelect.value) return;
                setAddressStatus(t('properties.form.loadingWards', 'Loading wards...'));
                loadWards(districtSelect.value, '')
                    .then(function () { setAddressStatus(''); })
                    .catch(function () {
                        setAddressStatus(t('properties.form.wardLoadFailed', 'Could not load wards. Please try again.'), true);
                    });
            });

            wardSelect.addEventListener('change', function () {
                clearError('wardCode', form);
                syncSelectedName(wardSelect, wardName);
                updateFullAddress();
            });
        } else {
            updateFullAddress();
            setAddressStatus('');
        }

        form.querySelectorAll('[data-field], textarea').forEach(function (field) {
            field.addEventListener('input', function () {
                clearError(field.dataset.field || field.name, form);
                if (field.name === 'streetAddress') updateFullAddress();
            });
        });

        if (addExtraButton && extraFeesList) {
            addExtraButton.addEventListener('click', function () {
                extraFeesList.appendChild(createExtraFeeRow());
                reindexExtraFees(extraFeesList);
                initNumericFields(form);
                refreshPrettySelect(extraFeesList);
                refreshIcons();
            });

            extraFeesList.addEventListener('click', function (event) {
                const remove = event.target.closest('[data-remove-extra-fee]');
                if (!remove) return;
                const row = remove.closest('[data-extra-fee-row]');
                if (row) row.remove();
                reindexExtraFees(extraFeesList);
            });

            reindexExtraFees(extraFeesList);
            refreshPrettySelect(extraFeesList);
        }

        form.addEventListener('submit', function (event) {
            updateFullAddress();
            sanitizeNumericFields(form);
            if (!validatePropertyForm(form)) {
                event.preventDefault();
                const firstInvalid = form.querySelector('.has-error input, .has-error select, .has-error textarea, .is-invalid');
                if (firstInvalid) firstInvalid.focus({ preventScroll: false });
                return;
            }

            if (submitButton) {
                submitButton.disabled = true;
                submitButton.classList.add('is-loading');
                const label = submitButton.querySelector('span');
                if (label) {
                    label.textContent = submitButton.dataset.submitLoadingLabel || form.dataset.submitLoadingLabel || t('common.creating', 'Creating...');
                }
            }
        });

        function loadDistricts(provinceCode, selectedCode) {
            districtSelect.disabled = true;
            resetSelect(districtSelect, t('action.loading', 'Loading...'));
            return fetchJson(`${ADDRESS_API}/p/${provinceCode}?depth=2`).then(function (province) {
                fillOptions(districtSelect, province.districts || [], t('properties.form.chooseDistrict', 'Choose district'), selectedCode);
                districtSelect.disabled = false;
                refreshPrettySelect(districtSelect);
                syncSelectedName(districtSelect, districtName);
                updateFullAddress();
            });
        }

        function loadWards(districtCode, selectedCode) {
            wardSelect.disabled = true;
            resetSelect(wardSelect, t('action.loading', 'Loading...'));
            return fetchJson(`${ADDRESS_API}/d/${districtCode}?depth=2`).then(function (district) {
                fillOptions(wardSelect, district.wards || [], t('properties.form.chooseWard', 'Choose ward'), selectedCode);
                wardSelect.disabled = false;
                refreshPrettySelect(wardSelect);
                syncSelectedName(wardSelect, wardName);
                updateFullAddress();
            });
        }

        function updateFullAddress() {
            const street = valueOf(form.querySelector('[name="streetAddress"]'));
            const parts = [street, valueOf(wardName), valueOf(districtName), valueOf(provinceName)]
                .map(function (item) { return item.trim(); })
                .filter(Boolean);
            const address = parts.join(', ');
            setHidden(fullAddress, address);
            if (addressPreview) {
                addressPreview.textContent = address || t('properties.form.addressPreviewEmpty', 'Address will be generated after all information is selected.');
            }
        }

        function setAddressStatus(message, isError) {
            if (!addressStatus) return;
            addressStatus.textContent = message || '';
            addressStatus.classList.toggle('is-error', Boolean(isError));
        }
    }

    function initNumericFields(scope) {
        scope.querySelectorAll('[data-money-input]').forEach(function (input) {
            bindMoneyInput(input);
            sanitizeNumberInput(input, { fromInitial: true });
            updateMoneyPreview(input);
        });

        scope.querySelectorAll('[data-invoice-day]').forEach(function (input) {
            bindInvoiceDayInput(input);
            sanitizeNumberInput(input, { fromInitial: true });
        });
    }

    function bindMoneyInput(input) {
        if (!input || input.dataset.moneyReady === 'true') return;
        input.dataset.moneyReady = 'true';
        input.addEventListener('keydown', preventNonDigitKey);
        input.addEventListener('input', function () {
            sanitizeNumberInput(input);
            updateMoneyPreview(input);
            clearMoneyErrorIfValid(input);
        });
        input.addEventListener('blur', function () {
            sanitizeNumberInput(input);
            updateMoneyPreview(input);
            clearMoneyErrorIfValid(input);
        });
    }

    function bindInvoiceDayInput(input) {
        if (!input || input.dataset.invoiceReady === 'true') return;
        input.dataset.invoiceReady = 'true';
        input.addEventListener('keydown', preventNonDigitKey);
        input.addEventListener('input', function () {
            sanitizeNumberInput(input);
            if (validateInvoiceDay(input, { silent: true })) clearError('invoiceDay', input.form);
        });
        input.addEventListener('blur', function () {
            sanitizeNumberInput(input);
            if (validateInvoiceDay(input, { silent: true })) clearError('invoiceDay', input.form);
        });
    }

    function preventNonDigitKey(event) {
        if (event.ctrlKey || event.metaKey || event.altKey) return;
        const allowed = ['Backspace', 'Delete', 'Tab', 'Escape', 'Enter', 'ArrowLeft', 'ArrowRight', 'Home', 'End'];
        if (allowed.includes(event.key)) return;
        if (/^\d$/.test(event.key)) return;
        event.preventDefault();
    }

    function sanitizeNumberInput(input, options) {
        if (!input) return '';
        let value = String(input.value || '').trim();
        if (options && options.fromInitial) {
            value = value.replace(/^(\d+)[.,]0{1,2}$/, '$1');
        }
        const clean = value.replace(/\D/g, '').replace(/^0+(?=\d)/, '');
        input.value = clean;
        return clean;
    }

    function sanitizeNumericFields(scope) {
        scope.querySelectorAll('[data-money-input], [data-invoice-day]').forEach(function (input) {
            sanitizeNumberInput(input);
        });
    }

    function formatCurrencyVND(value, suffix) {
        const raw = String(value || '0').replace(/\D/g, '');
        const amount = raw ? Number(raw) : 0;
        return `${currency.format(Number.isFinite(amount) ? amount : 0)} VNĐ${suffix ? ' ' + suffix : ''}`;
    }

    function updateMoneyPreview(input) {
        const preview = findMoneyPreview(input);
        if (!preview) return;
        const suffix = input.dataset.moneySuffix || '';
        const value = sanitizeNumberInput(input);
        preview.textContent = formatCurrencyVND(value || '0', suffix);
        preview.classList.toggle('is-empty', !value);
    }

    function validateMoneyField(input, options) {
        if (!input) return true;
        const settings = options || {};
        const value = sanitizeNumberInput(input);
        const name = input.dataset.field;
        const required = settings.required !== false;
        const invalid = (required && !value) || (value && !/^\d+$/.test(value));
        const message = !value ? t('properties.form.validation.amountRequired', 'Please enter an amount.')
            : t('properties.form.validation.nonNegativeInteger', 'Amount must be a non-negative integer.');

        if (invalid) {
            if (!settings.silent) {
                if (name) setError(name, message, input.form);
                else setExtraFieldError(input, message);
            }
            return false;
        }

        if (!settings.silent) {
            if (name) clearError(name, input.form);
            else setExtraFieldError(input, '');
        }
        return true;
    }

    function validateInvoiceDay(input, options) {
        if (!input) return true;
        const value = sanitizeNumberInput(input);
        const day = Number(value);
        const ok = Boolean(value) && Number.isInteger(day) && day >= 1 && day <= 31;
        if (!ok && !(options && options.silent)) {
            setError('invoiceDay', t('properties.form.validation.invoiceDay', 'Invoice issue day must be from 1 to 31.'), input.form);
        }
        return ok;
    }

    function clearMoneyErrorIfValid(input) {
        if (validateMoneyField(input, { silent: true })) {
            if (input.dataset.field) clearError(input.dataset.field, input.form);
            else setExtraFieldError(input, '');
        }
    }

    function findMoneyPreview(input) {
        if (!input) return null;
        if (input.dataset.field) {
            return (input.form || document).querySelector(`[data-money-preview-for="${input.dataset.field}"]`);
        }
        const row = input.closest('[data-extra-fee-row]');
        return row ? row.querySelector('[data-extra-money-preview]') : null;
    }

    function setExtraFieldError(input, message) {
        const row = input.closest('[data-extra-fee-row]');
        const error = row ? row.querySelector('[data-extra-error]') : null;
        input.classList.toggle('is-invalid', Boolean(message));
        if (error) error.textContent = message || '';
    }

    function loadProvinces() {
        return fetchJson(`${ADDRESS_API}/`);
    }

    function fetchJson(url) {
        return fetch(url, { headers: { Accept: 'application/json' } }).then(function (response) {
            if (!response.ok) throw new Error('address_http_' + response.status);
            return response.json();
        });
    }

    function fillOptions(select, items, placeholder, selectedCode) {
        if (!select) return;
        select.innerHTML = '';
        const empty = document.createElement('option');
        empty.value = '';
        empty.textContent = placeholder;
        select.appendChild(empty);

        items.forEach(function (item) {
            const option = document.createElement('option');
            option.value = String(item.code);
            option.textContent = item.name;
            option.dataset.name = item.name;
            if (selectedCode && String(selectedCode) === String(item.code)) option.selected = true;
            select.appendChild(option);
        });
        refreshPrettySelect(select);
    }

    function resetSelect(select, placeholder) {
        fillOptions(select, [], placeholder, '');
        if (select) {
            select.disabled = true;
            refreshPrettySelect(select);
        }
    }

    function refreshPrettySelect(root) {
        if (window.SmartRentSelect && typeof window.SmartRentSelect.refresh === 'function') {
            window.SmartRentSelect.refresh(root || document);
        }
    }

    function syncSelectedName(select, target) {
        if (!select || !target) return;
        const selected = select.options[select.selectedIndex];
        target.value = selected && selected.value ? (selected.dataset.name || selected.textContent || '') : '';
    }

    function validatePropertyForm(form) {
        clearAllErrors(form);
        initNumericFields(form);
        let ok = true;

        ok = requireField(form, 'propertyName', t('properties.form.validation.propertyNameRequired', 'Please enter property name.')) && ok;
        ok = requireField(form, 'streetAddress', t('properties.form.validation.streetRequired', 'Please enter street address.')) && ok;

        if (getField(form, 'provinceCode') || getField(form, 'districtCode') || getField(form, 'wardCode')) {
            ok = requireField(form, 'provinceCode', t('properties.form.validation.cityRequired', 'Please choose province/city.')) && ok;
            ok = requireField(form, 'districtCode', t('properties.form.validation.districtRequired', 'Please choose district.')) && ok;
            ok = requireField(form, 'wardCode', t('properties.form.validation.wardRequired', 'Please choose ward.')) && ok;
        } else {
            if (getField(form, 'city')) {
                ok = requireField(form, 'city', t('properties.form.validation.cityInputRequired', 'Please enter province/city.')) && ok;
            }
            if (getField(form, 'district')) {
                ok = requireField(form, 'district', t('properties.form.validation.districtInputRequired', 'Please enter district.')) && ok;
            }
        }

        Object.keys(moneyFields).forEach(function (name) {
            const field = getField(form, name);
            if (field) ok = validateMoneyField(field, { required: true }) && ok;
        });

        const invoiceDay = getField(form, 'invoiceDay');
        if (invoiceDay) ok = validateInvoiceDay(invoiceDay) && ok;

        form.querySelectorAll('[data-extra-fee-row]').forEach(function (row) {
            const amount = row.querySelector('[data-extra-field="amount"]');
            const name = row.querySelector('[data-extra-field="name"]');
            const hasName = name && String(name.value || '').trim();
            const hasAmount = amount && String(amount.value || '').trim();
            if (!hasName && !hasAmount) {
                if (amount) setExtraFieldError(amount, '');
                return;
            }
            ok = validateMoneyField(amount, { required: Boolean(hasName || hasAmount) }) && ok;
        });

        if (!ok && window.toast) {
            window.toast(t('validation.invalidInfo', 'Please check the fields marked with errors.'), 'err');
        }

        return ok;
    }

    function requireField(form, name, message) {
        const field = getField(form, name);
        if (!field || !String(field.value || '').trim()) {
            setError(name, message, form);
            return false;
        }
        return true;
    }

    function getField(scope, name) {
        return scope.querySelector(`[data-field="${name}"]`);
    }

    function setError(name, message, scope) {
        const root = scope || document;
        const field = root.querySelector(`[data-field="${name}"]`);
        const error = root.querySelector(`[data-error-for="${name}"]`);
        const wrap = field ? field.closest('.property-form-field') : null;
        if (wrap) wrap.classList.add('has-error');
        if (error) error.textContent = message;
    }

    function clearError(name, scope) {
        if (!name) return;
        const root = scope || document;
        const field = root.querySelector(`[data-field="${name}"]`);
        const error = root.querySelector(`[data-error-for="${name}"]`);
        const wrap = field ? field.closest('.property-form-field') : null;
        if (wrap) wrap.classList.remove('has-error');
        if (error) error.textContent = '';
    }

    function clearAllErrors(scope) {
        scope.querySelectorAll('.property-form-field.has-error').forEach(function (field) {
            field.classList.remove('has-error');
        });
        scope.querySelectorAll('[data-error-for], [data-extra-error]').forEach(function (error) {
            error.textContent = '';
        });
        scope.querySelectorAll('.is-invalid').forEach(function (field) {
            field.classList.remove('is-invalid');
        });
    }

    function createExtraFeeRow() {
        const row = document.createElement('div');
        row.className = 'property-extra-create-row';
        row.dataset.extraFeeRow = '';
        row.innerHTML = [
            '<label><span>' + escapeHtml(t('properties.form.feeName', 'Fee name')) + '</span><input type="text" maxlength="60" placeholder="' + escapeHtml(t('properties.form.feeNamePlaceholder', 'Example: Parking fee')) + '" data-extra-field="name" /></label>',
            '<label><span>' + escapeHtml(t('properties.form.amount', 'Amount')) + '</span><input type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="off" placeholder="0" data-money-input data-extra-field="amount" /><span class="property-money-preview" data-extra-money-preview></span><small class="property-extra-error" data-extra-error></small></label>',
            '<label><span>' + escapeHtml(t('properties.form.billingCycle', 'Billing cycle')) + '</span><select data-extra-field="cycle"><option value="monthly">' + escapeHtml(t('properties.form.monthly', 'Monthly')) + '</option><option value="once">' + escapeHtml(t('properties.form.once', 'One-time')) + '</option></select></label>',
            '<button type="button" class="property-extra-remove" data-remove-extra-fee title="' + escapeHtml(t('properties.form.deleteRow', 'Delete row')) + '"><i data-lucide="trash-2" class="w-4 h-4"></i></button>'
        ].join('');
        return row;
    }

    function reindexExtraFees(scope) {
        const root = scope || document;
        root.querySelectorAll('[data-extra-fee-row]').forEach(function (row, index) {
            const controls = row.querySelectorAll('input, select');
            controls.forEach(function (control, controlIndex) {
                const fallback = controlIndex === 0 ? 'name' : (controlIndex === 1 ? 'amount' : 'cycle');
                const field = control.dataset.extraField || fallback;
                control.dataset.extraField = field;
                control.name = `extraFees[${index}].${field}`;
            });
        });
    }

    function valueOf(element) {
        return element ? String(element.value || '') : '';
    }

    function setHidden(element, value) {
        if (element) element.value = value || '';
    }

    function normalize(value) {
        return String(value || '')
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .toLowerCase()
            .trim();
    }

    function refreshIcons() {
        if (window.lucide && typeof window.lucide.createIcons === 'function') {
            window.lucide.createIcons();
        }
    }

    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
})();
