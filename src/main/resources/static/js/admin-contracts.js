(function () {
    document.addEventListener('DOMContentLoaded', function () {
        if (window.SmartRentI18n && !window.SmartRentI18n.state.ready) {
            window.addEventListener('smartrent:i18n-ready', init, { once: true });
            return;
        }
        init();
    });

    function init() {
        document.querySelectorAll('[data-contract-form]').forEach(initContractForm);
    }

    function t(key, fallback, params) {
        if (window.SmartRentI18n && typeof window.SmartRentI18n.t === 'function') {
            return window.SmartRentI18n.t(key, params || {}, fallback);
        }
        return fallback;
    }

    function initContractForm(form) {
        const start = form.querySelector('[data-contract-start]');
        const end = form.querySelector('[data-contract-end]');
        const duration = form.querySelector('[data-contract-duration]');
        const durationButtons = Array.from(form.querySelectorAll('[data-duration-months]'));
        const message = form.querySelector('[data-contract-form-message]');
        const submit = form.querySelector('[data-contract-submit]');
        const imageInput = form.querySelector('[data-contract-image-input]');
        const imageName = form.querySelector('[data-contract-image-name]');
        const imagePreview = form.querySelector('[data-contract-image-preview]');
        const removeImage = form.querySelector('[data-contract-remove-image]');

        durationButtons.forEach(function (button) {
            button.addEventListener('click', function () {
                const months = Number(button.dataset.durationMonths || 0);
                if (!months) return;
                if (duration) duration.value = String(months);
                durationButtons.forEach(function (item) {
                    item.classList.toggle('is-active', item === button);
                });
                syncEndDate();
                validate();
            });
        });

        if (start) {
            start.addEventListener('change', function () {
                syncEndDate();
                validate();
            });
            start.addEventListener('input', validate);
        }
        if (end) {
            end.addEventListener('input', validate);
            end.addEventListener('change', validate);
        }

        if (imageInput) {
            imageInput.addEventListener('change', function () {
                renderImagePreview(imageInput, imageName, imagePreview);
                if (removeImage && imageInput.files && imageInput.files.length) {
                    removeImage.checked = false;
                }
            });
        }

        form.addEventListener('submit', function (event) {
            if (!validate()) {
                event.preventDefault();
                return;
            }
            if (submit) {
                submit.disabled = true;
                submit.classList.add('is-loading');
            }
        });

        validate();

        function syncEndDate() {
            if (!start || !end || !duration || !start.value) return;
            const months = Number(duration.value || 0);
            if (!months) return;
            const date = parseDate(start.value);
            if (!date) return;
            date.setMonth(date.getMonth() + months);
            date.setDate(date.getDate() - 1);
            end.value = formatDate(date);
        }

        function validate() {
            const errors = [];
            const startDate = start ? parseDate(start.value) : null;
            const endDate = end ? parseDate(end.value) : null;

            setFieldError(form, 'startDate', '');
            setFieldError(form, 'endDate', '');

            if (!startDate) {
                errors.push(t('contracts.detail.validation.startDateRequired', 'Please enter contract start date.'));
                setFieldError(form, 'startDate', t('validation.required', 'Required.'));
            }
            if (!endDate) {
                errors.push(t('contracts.detail.validation.endDateRequired', 'Please enter contract end date.'));
                setFieldError(form, 'endDate', t('validation.required', 'Required.'));
            }
            if (startDate && endDate && endDate <= startDate) {
                errors.push(t('contracts.detail.validation.endAfterStart', 'End date must be after start date.'));
                setFieldError(form, 'endDate', t('contracts.detail.validation.endAfterStart', 'End date must be after start date.'));
            }
            if (startDate) {
                const maxStart = new Date();
                maxStart.setFullYear(maxStart.getFullYear() + 5);
                if (startDate > maxStart) {
                    errors.push(t('contracts.detail.validation.startTooFar', 'Start date is too far. Please check again.'));
                    setFieldError(form, 'startDate', t('contracts.detail.validation.startTooFarShort', 'Start date is too far.'));
                }
            }

            if (message) {
                message.hidden = errors.length === 0;
                message.textContent = errors[0] || '';
            }
            if (submit) submit.disabled = errors.length > 0;
            return errors.length === 0;
        }
    }

    function renderImagePreview(input, nameTarget, previewTarget) {
        const files = Array.from(input.files || []);
        if (!files.length) {
            if (nameTarget) nameTarget.textContent = t('contracts.detail.noNewImages', 'No new images selected');
            if (previewTarget) {
                previewTarget.hidden = true;
                previewTarget.innerHTML = '';
            }
            return;
        }
        const invalid = files.find(function (candidate) {
            return !candidate.type || !candidate.type.startsWith('image/');
        });
        if (invalid) {
            input.value = '';
            if (window.toast) window.toast(t('common.imageFileInvalid', 'Please choose a JPG, PNG, or WEBP image file.'), 'err');
            return;
        }
        const totalSize = files.reduce(function (sum, candidate) { return sum + candidate.size; }, 0);
        if (nameTarget) {
            nameTarget.textContent = files.length === 1
                ? files[0].name + ' · ' + formatSize(files[0].size)
                : t('contracts.detail.imageCount', '{count} images · {size}', { count: files.length, size: formatSize(totalSize) });
        }
        if (!previewTarget) return;
        previewTarget.innerHTML = '';
        files.forEach(function (candidate, index) {
            const url = URL.createObjectURL(candidate);
            const item = document.createElement('figure');
            item.className = 'contract-upload-preview-item';
            const img = document.createElement('img');
            img.src = url;
            img.alt = candidate.name;
            img.onload = function () { URL.revokeObjectURL(url); };
            const caption = document.createElement('figcaption');
            caption.textContent = String(index + 1);
            item.appendChild(img);
            item.appendChild(caption);
            previewTarget.appendChild(item);
        });
        previewTarget.hidden = false;
        return;

        const file = input.files && input.files[0];
        if (!file) {
            if (nameTarget) nameTarget.textContent = t('contracts.detail.noNewImages', 'No new images selected');
            if (previewTarget) {
                previewTarget.hidden = true;
                previewTarget.innerHTML = '';
            }
            return;
        }
        if (!file.type || !file.type.startsWith('image/')) {
            input.value = '';
            if (window.toast) window.toast(t('common.imageFileInvalid', 'Please choose a JPG, PNG, or WEBP image file.'), 'err');
            return;
        }
        if (nameTarget) {
            nameTarget.textContent = file.name + ' · ' + formatSize(file.size);
        }
        if (!previewTarget) return;
        const url = URL.createObjectURL(file);
        previewTarget.innerHTML = '';
        const img = document.createElement('img');
        img.src = url;
        img.alt = file.name;
        img.onload = function () { URL.revokeObjectURL(url); };
        previewTarget.appendChild(img);
        previewTarget.hidden = false;
    }

    function setFieldError(form, field, text) {
        const node = form.querySelector('[data-contract-error="' + field + '"]');
        if (!node) return;
        node.textContent = text || '';
        node.hidden = !text;
    }

    function parseDate(value) {
        if (!value) return null;
        const date = new Date(value + 'T00:00:00');
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function formatDate(date) {
        const yyyy = date.getFullYear();
        const mm = String(date.getMonth() + 1).padStart(2, '0');
        const dd = String(date.getDate()).padStart(2, '0');
        return yyyy + '-' + mm + '-' + dd;
    }

    function formatSize(size) {
        const mb = size / (1024 * 1024);
        if (mb >= 1) return mb.toFixed(mb >= 10 ? 0 : 1) + ' MB';
        return Math.max(1, Math.round(size / 1024)) + ' KB';
    }
})();
