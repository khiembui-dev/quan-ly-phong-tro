(function () {
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-notification-form]').forEach(initNotificationForm);
  });

  function initNotificationForm(form) {
    const modeInputs = Array.from(form.querySelectorAll('input[name="audienceMode"]'));
    const panels = Array.from(form.querySelectorAll('[data-audience-panel]'));
    const recipientCount = form.querySelector('[data-recipient-count]');
    const recipientError = form.querySelector('[data-recipient-error]');
    const submitButton = form.querySelector('[data-notification-submit]');
    const allCount = Number(form.dataset.allRecipientCount || 0);
    const titleInput = form.querySelector('[data-notification-title]');
    const bodyInput = form.querySelector('[data-notification-body]');
    const linkInput = form.querySelector('[data-notification-link]');

    modeInputs.forEach(function (input) {
      input.addEventListener('change', function () {
        syncAudience();
        updateRecipients();
      });
    });

    form.querySelectorAll('[data-recipient-checkbox]').forEach(function (input) {
      input.addEventListener('change', updateRecipients);
    });

    form.querySelectorAll('[data-recipient-search]').forEach(function (input) {
      input.addEventListener('input', function () {
        filterRecipients(input.dataset.recipientSearch, input.value);
      });
    });

    form.querySelectorAll('[data-select-visible]').forEach(function (button) {
      button.addEventListener('click', function () {
        toggleVisibleRecipients(button.dataset.selectVisible, button);
      });
    });

    bindPreviewInput(titleInput, form.querySelector('[data-preview-title]'), 'Tiêu đề thông báo');
    bindPreviewInput(
      bodyInput,
      form.querySelector('[data-preview-body]'),
      'Nội dung bạn nhập sẽ được hiển thị tại đây để kiểm tra trước khi gửi.'
    );

    if (linkInput) {
      linkInput.addEventListener('input', function () {
        const previewLink = form.querySelector('[data-preview-link]');
        if (previewLink) previewLink.hidden = !linkInput.value.trim();
      });
    }

    bindCounter(titleInput, form.querySelector('[data-title-count]'));
    bindCounter(bodyInput, form.querySelector('[data-body-count]'));
    initImageUploader(form);

    form.addEventListener('submit', function (event) {
      if (currentRecipientCount() > 0) return;
      event.preventDefault();
      if (recipientError) recipientError.hidden = false;
      const activePanel = form.querySelector('[data-audience-panel]:not([hidden])');
      if (activePanel) activePanel.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });

    form.addEventListener('reset', function () {
      window.setTimeout(function () {
        form.querySelectorAll('[data-recipient-option]').forEach(function (option) {
          option.hidden = false;
        });
        form.querySelectorAll('[data-recipient-search]').forEach(function (input) {
          input.value = '';
        });
        syncAudience();
        updateRecipients();
        updatePreview(titleInput, form.querySelector('[data-preview-title]'), 'Tiêu đề thông báo');
        updatePreview(
          bodyInput,
          form.querySelector('[data-preview-body]'),
          'Nội dung bạn nhập sẽ được hiển thị tại đây để kiểm tra trước khi gửi.'
        );
        const previewLink = form.querySelector('[data-preview-link]');
        if (previewLink) previewLink.hidden = true;
        resetImageUploader(form);
      }, 0);
    });

    syncAudience();
    updateRecipients();

    function activeMode() {
      const selected = modeInputs.find(function (input) { return input.checked; });
      return selected ? selected.value : 'ALL';
    }

    function syncAudience() {
      const mode = activeMode();
      panels.forEach(function (panel) {
        panel.hidden = panel.dataset.audiencePanel !== mode;
      });
      if (recipientError) recipientError.hidden = true;
    }

    function currentRecipientCount() {
      const mode = activeMode();
      if (mode === 'ALL') return allCount;
      const tenantIds = new Set();
      form.querySelectorAll('[data-recipient-option="' + mode + '"] [data-recipient-checkbox]:checked')
        .forEach(function (input) {
          if (input.dataset.tenantId) tenantIds.add(input.dataset.tenantId);
        });
      return tenantIds.size;
    }

    function updateRecipients() {
      const count = currentRecipientCount();
      if (recipientCount) recipientCount.textContent = String(count);
      if (submitButton) submitButton.disabled = count === 0;
      if (recipientError && count > 0) recipientError.hidden = true;
    }

    function filterRecipients(mode, rawQuery) {
      const query = normalize(rawQuery);
      form.querySelectorAll('[data-recipient-option="' + mode + '"]').forEach(function (option) {
        option.hidden = !!query && !normalize(option.dataset.searchText || '').includes(query);
      });
    }

    function toggleVisibleRecipients(mode, button) {
      const visibleInputs = Array.from(
        form.querySelectorAll('[data-recipient-option="' + mode + '"]:not([hidden]) [data-recipient-checkbox]')
      );
      const shouldSelect = visibleInputs.some(function (input) { return !input.checked; });
      visibleInputs.forEach(function (input) { input.checked = shouldSelect; });
      button.textContent = shouldSelect ? 'Bỏ chọn đang hiển thị' : 'Chọn tất cả đang hiển thị';
      updateRecipients();
    }
  }

  function bindPreviewInput(input, output, fallback) {
    if (!input || !output) return;
    input.addEventListener('input', function () {
      updatePreview(input, output, fallback);
    });
  }

  function updatePreview(input, output, fallback) {
    if (!input || !output) return;
    output.textContent = input.value.trim() || fallback;
  }

  function bindCounter(input, output) {
    if (!input || !output) return;
    const render = function () { output.textContent = String(input.value.length); };
    input.addEventListener('input', render);
    render();
  }

  function initImageUploader(form) {
    const uploader = form.querySelector('[data-notification-uploader]');
    const input = form.querySelector('[data-notification-image]');
    const trigger = form.querySelector('[data-notification-upload-trigger]');
    const remove = form.querySelector('[data-notification-image-remove]');
    if (!uploader || !input || !trigger) return;

    trigger.addEventListener('click', function () { input.click(); });
    input.addEventListener('change', function () {
      showImage(form, input.files && input.files[0] ? input.files[0] : null);
    });

    ['dragenter', 'dragover'].forEach(function (eventName) {
      uploader.addEventListener(eventName, function (event) {
        event.preventDefault();
        uploader.classList.add('is-dragging');
      });
    });
    ['dragleave', 'drop'].forEach(function (eventName) {
      uploader.addEventListener(eventName, function (event) {
        event.preventDefault();
        uploader.classList.remove('is-dragging');
      });
    });
    uploader.addEventListener('drop', function (event) {
      const file = event.dataTransfer && event.dataTransfer.files
        ? event.dataTransfer.files[0]
        : null;
      if (!file || !file.type.startsWith('image/')) return;
      try {
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;
      } catch (ignored) {
        return;
      }
      showImage(form, file);
    });

    if (remove) {
      remove.addEventListener('click', function () {
        input.value = '';
        showImage(form, null);
      });
    }
  }

  function showImage(form, file) {
    const selected = form.querySelector('[data-notification-upload-selected]');
    const thumb = form.querySelector('[data-notification-image-thumb]');
    const preview = form.querySelector('[data-preview-image]');
    const name = form.querySelector('[data-notification-image-name]');
    const size = form.querySelector('[data-notification-image-size]');
    const oldUrl = form.dataset.notificationImageUrl;
    if (oldUrl) URL.revokeObjectURL(oldUrl);
    delete form.dataset.notificationImageUrl;

    if (!file) {
      if (selected) selected.hidden = true;
      if (thumb) thumb.removeAttribute('src');
      if (preview) {
        preview.hidden = true;
        preview.removeAttribute('src');
      }
      return;
    }

    const url = URL.createObjectURL(file);
    form.dataset.notificationImageUrl = url;
    if (selected) selected.hidden = false;
    if (thumb) thumb.src = url;
    if (preview) {
      preview.src = url;
      preview.hidden = false;
    }
    if (name) name.textContent = file.name;
    if (size) size.textContent = formatBytes(file.size);
  }

  function resetImageUploader(form) {
    const input = form.querySelector('[data-notification-image]');
    if (input) input.value = '';
    showImage(form, null);
  }

  function formatBytes(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
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
})();
