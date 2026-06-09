(function () {
    const currency = new Intl.NumberFormat('vi-VN');

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-room-create-form]').forEach(initRoomCreateForm);
    });

    function initRoomCreateForm(form) {
        const propertySelect = form.querySelector('[data-property-select]');
        const fullAddress = form.querySelector('[data-full-address]');
        const addressPreview = form.querySelector('[data-address-preview]');
        const featureList = form.querySelector('[data-room-feature-list]');
        const addFeatureButton = form.querySelector('[data-add-room-feature]');
        const roomInfoList = form.querySelector('[data-room-info-list]');
        const addRoomInfoButton = form.querySelector('[data-add-room-info]');
        const inheritToggle = form.querySelector('[data-inherit-toggle]');
        const roomFeeList = form.querySelector('[data-room-fee-list]');
        const addRoomFeeButton = form.querySelector('[data-add-room-fee]');
        const submitButton = form.querySelector('[data-submit-button]');
        const imageInput = form.querySelector('[data-room-image-input]');
        const imagePreview = form.querySelector('[data-room-image-preview]');

        initNumericFields(form);
        updateFullAddress();

        if (propertySelect) {
            propertySelect.addEventListener('change', function () {
                clearError(form, 'propertyId');
                applyPropertyDefaults(propertySelect, { propertyChanged: true });
            });
            applyPropertyDefaults(propertySelect);
        }

        if (inheritToggle) {
            inheritToggle.addEventListener('change', function () {
                setTariffControlsState(form);
            });
            setTariffControlsState(form);
        }

        const addressLineInput = field(form, 'addressLine');
        if (addressLineInput) {
            addressLineInput.addEventListener('input', function () {
                clearError(form, 'addressLine');
            });
        }

        if (addFeatureButton && featureList) {
            addFeatureButton.addEventListener('click', function () {
                const row = createFeatureRow();
                featureList.appendChild(row);
                reindexFeatures(featureList);
                refreshPrettySelect(row);
                refreshIcons();
            });

            featureList.addEventListener('click', function (event) {
                const addAfter = event.target.closest('[data-add-room-feature-row]');
                if (addAfter) {
                    const row = createFeatureRow();
                    const current = addAfter.closest('[data-room-feature-row]');
                    if (current) {
                        current.insertAdjacentElement('afterend', row);
                    } else {
                        featureList.appendChild(row);
                    }
                    reindexFeatures(featureList);
                    refreshPrettySelect(row);
                    refreshIcons();
                    const input = row.querySelector('[data-room-feature-name]');
                    if (input) input.focus({ preventScroll: false });
                    return;
                }

                const remove = event.target.closest('[data-remove-room-feature]');
                if (!remove) return;
                const row = remove.closest('[data-room-feature-row]');
                if (row) row.remove();
                reindexFeatures(featureList);
            });

            reindexFeatures(featureList);
        }

        if (addRoomInfoButton && roomInfoList) {
            addRoomInfoButton.addEventListener('click', function () {
                const row = createRoomInfoRow();
                roomInfoList.appendChild(row);
                reindexRoomInfo(roomInfoList);
                refreshIcons();
                const input = row.querySelector('[data-room-info-text]');
                if (input) input.focus({ preventScroll: false });
            });

            roomInfoList.addEventListener('click', function (event) {
                const addAfter = event.target.closest('[data-add-room-info-row]');
                if (addAfter) {
                    const row = createRoomInfoRow();
                    const current = addAfter.closest('[data-room-info-row]');
                    if (current) {
                        current.insertAdjacentElement('afterend', row);
                    } else {
                        roomInfoList.appendChild(row);
                    }
                    reindexRoomInfo(roomInfoList);
                    refreshIcons();
                    const input = row.querySelector('[data-room-info-text]');
                    if (input) input.focus({ preventScroll: false });
                    return;
                }

                const remove = event.target.closest('[data-remove-room-info]');
                if (!remove) return;
                const row = remove.closest('[data-room-info-row]');
                if (row) row.remove();
                reindexRoomInfo(roomInfoList);
            });

            reindexRoomInfo(roomInfoList);
        }

        if (addRoomFeeButton && roomFeeList) {
            addRoomFeeButton.addEventListener('click', function () {
                const row = createRoomFeeRow('', '');
                roomFeeList.appendChild(row);
                initNumericFields(row);
                reindexRoomFees(roomFeeList);
                setTariffControlsState(form);
                refreshIcons();
                const input = row.querySelector('[data-room-fee-name]');
                if (input) input.focus({ preventScroll: false });
            });

            roomFeeList.addEventListener('click', function (event) {
                const addAfter = event.target.closest('[data-add-room-fee-row]');
                if (addAfter) {
                    const row = createRoomFeeRow('', '');
                    const current = addAfter.closest('[data-room-fee-row]');
                    if (current) {
                        current.insertAdjacentElement('afterend', row);
                    } else {
                        roomFeeList.appendChild(row);
                    }
                    initNumericFields(row);
                    reindexRoomFees(roomFeeList);
                    setTariffControlsState(form);
                    refreshIcons();
                    const input = row.querySelector('[data-room-fee-name]');
                    if (input) input.focus({ preventScroll: false });
                    return;
                }

                const remove = event.target.closest('[data-remove-room-fee]');
                if (!remove) return;
                const row = remove.closest('[data-room-fee-row]');
                if (row) row.remove();
                reindexRoomFees(roomFeeList);
            });

            reindexRoomFees(roomFeeList);
        }

        if (imageInput && imagePreview) {
            imageInput.addEventListener('change', function () {
                renderImagePreview(imageInput, imagePreview);
            });
        }

        form.addEventListener('submit', function (event) {
            sanitizeNumericFields(form);
            updateFullAddress();
            if (!validateForm(form)) {
                event.preventDefault();
                const firstInvalid = form.querySelector('.has-error input, .has-error select, .has-error textarea, .is-invalid');
                if (firstInvalid) firstInvalid.focus({ preventScroll: false });
                return;
            }
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.classList.add('is-loading');
                const label = submitButton.querySelector('span');
                if (label) label.textContent = form.dataset.submitLoadingText || 'Đang xử lý...';
            }
        });

        function updateFullAddress() {
            const addressLine = field(form, 'addressLine');
            const address = addressLine ? String(addressLine.value || '').trim() : '';
            if (fullAddress) fullAddress.value = address;
            if (addressPreview) {
                addressPreview.textContent = address || 'Chọn cơ sở để lấy địa chỉ.';
            }
        }
    }

    function applyPropertyDefaults(select, options) {
        if (!select || !select.form) return;
        options = options || {};
        const selected = select.options[select.selectedIndex];
        const form = select.form;
        const addressLine = field(form, 'addressLine');
        const addressPreview = form.querySelector('[data-address-preview]');
        const tariff = tariffFor(select);
        if (!selected || !selected.value) {
            if (addressLine && select.dataset.keepAddress !== 'true') addressLine.value = '';
            if (addressPreview) addressPreview.textContent = 'Chọn cơ sở để lấy địa chỉ.';
            renderInheritedSummary(form, null);
            setTariffControlsState(form);
            return;
        }
        if (addressLine) {
            addressLine.value = (tariff && tariff.address) || selected.dataset.address || '';
            if (addressLine.value) clearError(form, 'addressLine');
        }
        if (addressPreview) {
            addressPreview.textContent = (tariff && tariff.address) || selected.dataset.address || 'Cơ sở này chưa có địa chỉ.';
        }
        const values = tariff || {
            serviceFee: selected.dataset.service,
            electricUnit: selected.dataset.electric,
            waterUnit: selected.dataset.water,
            fees: []
        };
        if (isInheriting(form)) {
            setMoneyInputValue(form, 'serviceFee', values.serviceFee);
            setMoneyInputValue(form, 'electricUnit', values.electricUnit);
            setMoneyInputValue(form, 'waterUnit', values.waterUnit);
        } else {
            ['serviceFee', 'electricUnit', 'waterUnit'].forEach(function (name) {
                const input = form.querySelector(`[name="${name}"]`);
                if (input) updateMoneyPreview(input);
            });
        }
        renderInheritedSummary(form, tariff);
        setTariffControlsState(form);
    }

    function isInheriting(form) {
        const toggle = form.querySelector('[data-inherit-toggle]');
        return !toggle || toggle.checked;
    }

    function setMoneyInputValue(form, name, value) {
        const input = form.querySelector(`[name="${name}"]`);
        if (!input || value == null || value === '') return;
        input.value = digits(value);
        updateMoneyPreview(input);
    }

    function tariffFor(select) {
        if (!select || !select.value) return null;
        const tariffs = window.SmartRentRoomTariffs || {};
        return tariffs[select.value] || null;
    }

    function renderInheritedSummary(form, tariff) {
        const nameTarget = form.querySelector('[data-inherited-property-name]');
        const noteTarget = form.querySelector('[data-inherited-note]');
        const rateGrid = form.querySelector('[data-inherited-rate-grid]');
        const extraList = form.querySelector('[data-inherited-extra-fees]');
        if (nameTarget) nameTarget.textContent = tariff ? tariff.name : 'Chưa chọn cơ sở';
        if (noteTarget) {
            noteTarget.textContent = tariff
                ? 'Các khoản này được khóa khi đang kế thừa.'
                : 'Chọn cơ sở để xem đầy đủ phí kế thừa.';
        }
        if (rateGrid) {
            rateGrid.innerHTML = '';
            [
                ['Điện', tariff ? tariff.electricUnit : 0, '/ kWh'],
                ['Nước', tariff ? tariff.waterUnit : 0, '/ m³'],
                ['Dịch vụ', tariff ? tariff.serviceFee : 0, '/ tháng']
            ].forEach(function (item) {
                rateGrid.appendChild(createInheritedRate(item[0], item[1], item[2]));
            });
        }
        if (extraList) {
            extraList.innerHTML = '';
            const fees = tariff && Array.isArray(tariff.fees) ? tariff.fees : [];
            if (fees.length === 0) {
                const empty = document.createElement('span');
                empty.className = 'room-inherited-empty';
                empty.textContent = 'Chưa có chi phí khác.';
                extraList.appendChild(empty);
            } else {
                fees.forEach(function (fee) {
                    extraList.appendChild(createInheritedFee(fee.name, fee.amount));
                });
            }
        }
    }

    function createInheritedRate(label, value, suffix) {
        const item = document.createElement('div');
        item.className = 'room-inherited-rate';
        item.innerHTML = `<span>${escapeHtml(label)}</span><strong class="tnum">${formatVnd(value)}</strong><small>${escapeHtml(suffix)}</small>`;
        return item;
    }

    function createInheritedFee(name, value) {
        const item = document.createElement('span');
        item.className = 'room-inherited-fee';
        item.innerHTML = `<span>${escapeHtml(name || 'Chi phí')}</span><strong class="tnum">${formatVnd(value)}</strong>`;
        return item;
    }

    function setTariffControlsState(form) {
        const toggle = form.querySelector('[data-inherit-toggle]');
        const inherit = !toggle || toggle.checked;
        form.querySelectorAll('[data-inherited-lock]').forEach(function (control) {
            control.disabled = inherit;
            control.classList.toggle('is-disabled', inherit);
        });
        const state = form.querySelector('[data-inherit-state]');
        if (state) {
            state.textContent = inherit ? 'Đang kế thừa' : 'Đang dùng giá riêng';
            state.classList.toggle('is-custom', !inherit);
        }
        if (window.SmartRentSelect && typeof window.SmartRentSelect.refresh === 'function') {
            window.SmartRentSelect.refresh(form);
        }
    }

    function formatVnd(value) {
        const raw = digits(value);
        const amount = raw ? Number(raw) : 0;
        return `${currency.format(Number.isFinite(amount) ? amount : 0)} ₫`;
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function initNumericFields(scope) {
        scope.querySelectorAll('[data-money-input]').forEach(function (input) {
            if (input.dataset.moneyReady !== 'true') {
                input.dataset.moneyReady = 'true';
                input.addEventListener('keydown', preventNonDigitKey);
                input.addEventListener('input', function () {
                    sanitizeIntegerInput(input);
                    updateMoneyPreview(input);
                    clearMoneyError(input);
                });
                input.addEventListener('blur', function () {
                    sanitizeIntegerInput(input);
                    updateMoneyPreview(input);
                    clearMoneyError(input);
                });
            }
            sanitizeIntegerInput(input);
            updateMoneyPreview(input);
        });

        scope.querySelectorAll('[data-integer-input]').forEach(function (input) {
            if (input.dataset.integerReady === 'true') return;
            input.dataset.integerReady = 'true';
            input.addEventListener('keydown', preventNonDigitKey);
            input.addEventListener('input', function () {
                sanitizeIntegerInput(input);
                clearError(input.form, input.dataset.field);
            });
        });

        scope.querySelectorAll('[data-decimal-input]').forEach(function (input) {
            if (input.dataset.decimalReady === 'true') return;
            input.dataset.decimalReady = 'true';
            input.addEventListener('keydown', function (event) {
                if (event.ctrlKey || event.metaKey || event.altKey) return;
                const allowed = ['Backspace', 'Delete', 'Tab', 'Escape', 'Enter', 'ArrowLeft', 'ArrowRight', 'Home', 'End', '.', ','];
                if (allowed.includes(event.key) || /^\d$/.test(event.key)) return;
                event.preventDefault();
            });
            input.addEventListener('input', function () {
                sanitizeDecimalInput(input);
                clearError(input.form, input.dataset.field);
            });
        });
    }

    function preventNonDigitKey(event) {
        if (event.ctrlKey || event.metaKey || event.altKey) return;
        const allowed = ['Backspace', 'Delete', 'Tab', 'Escape', 'Enter', 'ArrowLeft', 'ArrowRight', 'Home', 'End'];
        if (allowed.includes(event.key) || /^\d$/.test(event.key)) return;
        event.preventDefault();
    }

    function sanitizeNumericFields(scope) {
        scope.querySelectorAll('[data-money-input], [data-integer-input]').forEach(sanitizeIntegerInput);
        scope.querySelectorAll('[data-decimal-input]').forEach(sanitizeDecimalInput);
        scope.querySelectorAll('[data-money-input]').forEach(function (input) {
            if (!input.value) input.value = '0';
        });
    }

    function sanitizeIntegerInput(input) {
        if (!input) return '';
        input.value = digits(input.value).replace(/^0+(?=\d)/, '');
        return input.value;
    }

    function sanitizeDecimalInput(input) {
        if (!input) return '';
        let value = String(input.value || '').replace(',', '.').replace(/[^\d.]/g, '');
        const firstDot = value.indexOf('.');
        if (firstDot !== -1) {
            value = value.slice(0, firstDot + 1) + value.slice(firstDot + 1).replace(/\./g, '');
        }
        value = value.replace(/^0+(?=\d)/, '');
        input.value = value;
        return value;
    }

    function digits(value) {
        return String(value || '').replace(/\D/g, '');
    }

    function updateMoneyPreview(input) {
        const preview = moneyPreviewFor(input);
        if (!preview) return;
        const value = digits(input.value);
        const amount = value ? Number(value) : 0;
        const suffix = input.dataset.moneySuffix || '';
        preview.textContent = `${currency.format(Number.isFinite(amount) ? amount : 0)} VNĐ${suffix ? ' ' + suffix : ''}`;
        preview.classList.toggle('is-empty', !value);
    }

    function moneyPreviewFor(input) {
        const row = input.closest('[data-room-fee-row]');
        if (row) return row.querySelector('[data-room-fee-money-preview]');
        if (input.dataset.field) {
            return input.form.querySelector(`[data-money-preview-for="${input.dataset.field}"]`);
        }
        return null;
    }

    function clearMoneyError(input) {
        if (input.dataset.field) clearError(input.form, input.dataset.field);
    }

    function validateForm(form) {
        clearAllErrors(form);
        let ok = true;

        ok = requireField(form, 'propertyId', 'Vui lòng chọn cơ sở.') && ok;
        ok = requireField(form, 'addressLine', 'Vui lòng chọn cơ sở có địa chỉ.') && ok;

        const area = field(form, 'areaSqm');
        const areaValue = area ? Number(String(area.value || '').replace(',', '.')) : 0;
        if (!area || !area.value || !Number.isFinite(areaValue) || areaValue <= 0) {
            setError(form, 'areaSqm', 'Diện tích phải lớn hơn 0.');
            ok = false;
        }

        const maxOccupants = field(form, 'maxOccupants');
        if (maxOccupants && !maxOccupants.disabled && maxOccupants.value && Number(maxOccupants.value) <= 0) {
            setError(form, 'maxOccupants', 'Số người tối đa phải lớn hơn 0.');
            ok = false;
        }

        ['priceMonthly', 'depositAmount', 'serviceFee', 'electricUnit', 'waterUnit'].forEach(function (name) {
            const input = field(form, name);
            if (!input) return;
            if (input.disabled) return;
            const value = sanitizeIntegerInput(input);
            if (value && !/^\d+$/.test(value)) {
                setError(form, name, 'Giá trị tiền phải là số nguyên không âm.');
                ok = false;
            }
        });

        form.querySelectorAll('[data-room-fee-row]').forEach(function (row) {
            const name = row.querySelector('[data-room-fee-name]');
            const amount = row.querySelector('[data-room-fee-amount]');
            const error = row.querySelector('[data-room-fee-error]');
            row.classList.remove('has-error');
            if (!name || name.disabled || !amount || amount.disabled) {
                if (error) error.textContent = '';
                return;
            }
            const rawName = String(name.value || '').trim();
            const rawAmount = sanitizeIntegerInput(amount);
            if (!rawName && rawAmount) {
                row.classList.add('has-error');
                if (error) error.textContent = 'Nhập tên chi phí.';
                ok = false;
                return;
            }
            if (rawName && !rawAmount) {
                row.classList.add('has-error');
                if (error) error.textContent = 'Nhập số tiền chi phí.';
                ok = false;
                return;
            }
            if (rawAmount && !/^\d+$/.test(rawAmount)) {
                row.classList.add('has-error');
                if (error) error.textContent = 'Số tiền phải là số nguyên không âm.';
                ok = false;
            } else if (rawAmount && Number(rawAmount) <= 0) {
                row.classList.add('has-error');
                if (error) error.textContent = 'Số tiền phải lớn hơn 0.';
                ok = false;
            } else if (error) {
                error.textContent = '';
            }
        });

        const status = form.querySelector('[name="status"]');
        const tenant = field(form, 'currentTenantId');
        if (status && status.value === 'OCCUPIED' && (!tenant || !tenant.value)) {
            setError(form, 'currentTenantId', 'Phòng đang ở cần chọn khách thuê.');
            ok = false;
        }

        if (!ok && window.toast) {
            window.toast('Vui lòng kiểm tra các trường đã đánh dấu lỗi.', 'err');
        }
        return ok;
    }

    function requireField(form, name, message) {
        const input = field(form, name);
        if (!input || !String(input.value || '').trim()) {
            setError(form, name, message);
            return false;
        }
        return true;
    }

    function field(form, name) {
        return form.querySelector(`[data-field="${name}"]`);
    }

    function setError(form, name, message) {
        if (!name) return;
        const input = field(form, name);
        const error = form.querySelector(`[data-error-for="${name}"]`);
        const wrap = input ? input.closest('.room-form-field') : null;
        if (wrap) wrap.classList.add('has-error');
        if (error) error.textContent = message || '';
    }

    function clearError(form, name) {
        if (!form || !name) return;
        const input = field(form, name);
        const error = form.querySelector(`[data-error-for="${name}"]`);
        const wrap = input ? input.closest('.room-form-field') : null;
        if (wrap) wrap.classList.remove('has-error');
        if (error) error.textContent = '';
    }

    function clearAllErrors(form) {
        form.querySelectorAll('.room-form-field.has-error').forEach(function (item) {
            item.classList.remove('has-error');
        });
        form.querySelectorAll('[data-error-for]').forEach(function (item) {
            item.textContent = '';
        });
        form.querySelectorAll('.is-invalid').forEach(function (item) {
            item.classList.remove('is-invalid');
        });
        form.querySelectorAll('[data-room-fee-row].has-error').forEach(function (item) {
            item.classList.remove('has-error');
        });
        form.querySelectorAll('[data-room-fee-error]').forEach(function (item) {
            item.textContent = '';
        });
    }

    function createFeatureRow() {
        const row = document.createElement('div');
        row.className = 'room-extra-row room-feature-row';
        row.dataset.roomFeatureRow = '';
        row.innerHTML = [
            '<label><span>Tên tiện ích/nội thất</span><input type="text" maxlength="80" data-room-feature-name placeholder="VD: Tủ quần áo, máy lạnh" /></label>',
            '<label><span>Nhóm</span><select data-room-feature-category><option value="FURNITURE">Nội thất</option><option value="UTILITY" selected>Tiện ích</option><option value="RULE">Quy định</option><option value="OTHER">Khác</option></select></label>',
            '<div class="room-feature-actions"><button type="button" class="room-feature-add" data-add-room-feature-row title="Thêm tiếp"><i data-lucide="plus" class="w-4 h-4"></i></button><button type="button" class="room-extra-remove" data-remove-room-feature title="Xóa dòng"><i data-lucide="trash-2" class="w-4 h-4"></i></button></div>'
        ].join('');
        return row;
    }

    function reindexFeatures(root) {
        root.querySelectorAll('[data-room-feature-row]').forEach(function (row, index) {
            const name = row.querySelector('[data-room-feature-name]');
            const category = row.querySelector('[data-room-feature-category]');
            if (name) name.name = `customAmenityNames[${index}]`;
            if (category) category.name = `customAmenityCategories[${index}]`;
        });
    }

    function createRoomInfoRow() {
        const row = document.createElement('div');
        row.className = 'room-info-row';
        row.dataset.roomInfoRow = '';
        row.innerHTML = [
            '<label><span>Nội dung</span><input type="text" maxlength="100" data-room-info-text placeholder="VD: 1 phòng ngủ, tầng 3, tối đa 2 xe" /></label>',
            '<div class="room-info-actions"><button type="button" class="room-info-add" data-add-room-info-row title="Thêm tiếp"><i data-lucide="plus" class="w-4 h-4"></i></button><button type="button" class="room-extra-remove" data-remove-room-info title="Xóa dòng"><i data-lucide="trash-2" class="w-4 h-4"></i></button></div>'
        ].join('');
        return row;
    }

    function reindexRoomInfo(root) {
        root.querySelectorAll('[data-room-info-row]').forEach(function (row, index) {
            const input = row.querySelector('[data-room-info-text]');
            if (input) input.name = `roomInfoTexts[${index}]`;
        });
    }

    function createRoomFeeRow(name, amount) {
        const row = document.createElement('div');
        row.className = 'room-extra-row room-tariff-fee-row';
        row.dataset.roomFeeRow = '';
        row.innerHTML = [
            `<label><span>Tên chi phí</span><input type="text" maxlength="80" data-room-fee-name data-inherited-lock placeholder="VD: Internet" value="${escapeHtml(name || '')}" /></label>`,
            `<label><span>Số tiền</span><input type="text" inputmode="numeric" data-money-input data-room-fee-amount data-inherited-lock placeholder="0" value="${escapeHtml(amount == null || amount === '' ? '' : digits(amount))}" /><span class="property-money-preview" data-room-fee-money-preview></span><small class="property-extra-error" data-room-fee-error></small></label>`,
            '<div class="room-feature-actions"><button type="button" class="room-feature-add" data-add-room-fee-row data-inherited-lock title="Thêm tiếp"><i data-lucide="plus" class="w-4 h-4"></i></button><button type="button" class="room-extra-remove" data-remove-room-fee data-inherited-lock title="Xóa dòng"><i data-lucide="trash-2" class="w-4 h-4"></i></button></div>'
        ].join('');
        return row;
    }

    function reindexRoomFees(root) {
        root.querySelectorAll('[data-room-fee-row]').forEach(function (row, index) {
            const name = row.querySelector('[data-room-fee-name]');
            const amount = row.querySelector('[data-room-fee-amount]');
            if (name) name.name = `extraFeeNames[${index}]`;
            if (amount) amount.name = `extraFeeAmounts[${index}]`;
        });
    }

    function renderImagePreview(input, target) {
        target.innerHTML = '';
        const files = Array.from(input.files || []);
        if (files.length === 0) return;
        files.forEach(function (file) {
            const item = document.createElement('div');
            item.className = 'room-upload-preview-item';

            const thumb = document.createElement('span');
            thumb.className = 'room-upload-preview-thumb';
            if (file.type && file.type.startsWith('image/')) {
                const img = document.createElement('img');
                img.src = URL.createObjectURL(file);
                img.alt = file.name;
                img.onload = function () { URL.revokeObjectURL(img.src); };
                thumb.appendChild(img);
            } else {
                thumb.innerHTML = '<i data-lucide="image" class="w-4 h-4"></i>';
            }

            const copy = document.createElement('span');
            copy.className = 'room-upload-preview-copy';
            const name = document.createElement('strong');
            name.textContent = file.name;
            const size = document.createElement('small');
            size.textContent = formatFileSize(file.size);
            copy.appendChild(name);
            copy.appendChild(size);

            item.appendChild(thumb);
            item.appendChild(copy);
            target.appendChild(item);
        });
        refreshIcons();
    }

    function formatFileSize(size) {
        const mb = size / (1024 * 1024);
        if (mb >= 1) return mb.toFixed(mb >= 10 ? 0 : 1) + ' MB';
        return Math.max(1, Math.round(size / 1024)) + ' KB';
    }

    function refreshIcons() {
        if (window.lucide && typeof window.lucide.createIcons === 'function') {
            window.lucide.createIcons();
        }
    }

    function refreshPrettySelect(root) {
        if (window.SmartRentSelect && typeof window.SmartRentSelect.refresh === 'function') {
            window.SmartRentSelect.refresh(root || document);
        }
    }
})();
