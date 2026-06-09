(function () {
  const maxFiles = 12;

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-customer-ticket-page]').forEach(function (page) {
      initPage(page);
      initTicketModal(page);
    });
    document.querySelectorAll('[data-ticket-uploader]').forEach(initUploader);
    document.querySelectorAll('[data-customer-ticket-form]').forEach(initCreateForm);
    document.querySelectorAll('[data-customer-ticket-reply]').forEach(initReplyForm);
  });

  function initPage(page) {
    const search = page.querySelector('[data-customer-ticket-search]');
    const rows = Array.from(page.querySelectorAll('.support-ticket-row'));
    if (!search || rows.length === 0) return;

    search.addEventListener('input', function () {
      const needle = normalize(search.value);
      let visible = 0;
      rows.forEach(function (row) {
        const matched = !needle || normalize(row.dataset.ticketSearch || '').includes(needle);
        row.hidden = !matched;
        if (matched) visible += 1;
      });
      page.classList.toggle('is-search-empty', visible === 0);
    });
  }

  function initCreateForm(form) {
    ['title', 'description'].forEach(function (name) {
      const field = form.querySelector('[data-ticket-field="' + name + '"]');
      if (!field) return;
      field.addEventListener('input', function () {
        if (value(field)) setError(form, name, '');
      });
    });

    form.addEventListener('submit', function (event) {
      const title = form.querySelector('[data-ticket-field="title"]');
      const description = form.querySelector('[data-ticket-field="description"]');
      let ok = true;

      if (!value(title)) {
        setError(form, 'title', 'Vui lòng nhập tiêu đề.');
        ok = false;
      } else {
        setError(form, 'title', '');
      }

      if (!value(description)) {
        setError(form, 'description', 'Vui lòng nhập nội dung.');
        ok = false;
      } else {
        setError(form, 'description', '');
      }

      if (!ok) {
        event.preventDefault();
        window.toast?.('Vui lòng kiểm tra thông tin ticket.', 'err');
        (title && !value(title) ? title : description)?.focus();
      }
    });
  }

  function initReplyForm(form) {
    form.addEventListener('submit', function (event) {
      const body = form.querySelector('textarea[name="body"]');
      const fileInput = form.querySelector('[data-ticket-file-input]');
      const hasBody = !!value(body);
      const hasFiles = !!(fileInput && fileInput.files && fileInput.files.length);

      if (hasBody || hasFiles) return;

      event.preventDefault();
      window.toast?.('Vui lòng nhập phản hồi hoặc đính kèm ảnh.', 'err');
      body?.focus();
    });
  }

  function initTicketModal(page) {
    const modal = page.querySelector('[data-ticket-modal]');
    if (!modal) return;

    const dialog = modal.querySelector('.support-modal-dialog');
    const initialFocus = modal.querySelector('[data-ticket-modal-initial]');
    const openers = page.querySelectorAll('[data-ticket-modal-open]');
    const closers = modal.querySelectorAll('[data-ticket-modal-close]');
    let lastFocus = null;

    openers.forEach(function (button) {
      button.addEventListener('click', function () {
        openModal(button);
      });
    });

    closers.forEach(function (button) {
      button.addEventListener('click', function (event) {
        event.preventDefault();
        closeModal();
      });
    });

    modal.addEventListener('click', function (event) {
      if (dialog && !dialog.contains(event.target)) closeModal();
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && !modal.hidden) closeModal();
    });

    if (window.location.hash === '#new-ticket') {
      openModal();
    }

    function openModal(source) {
      lastFocus = source || document.activeElement;
      modal.hidden = false;
      modal.classList.add('is-open');
      document.body.classList.add('support-modal-open');
      window.lucide?.createIcons?.();

      window.requestAnimationFrame(function () {
        (initialFocus || modal.querySelector('input, select, textarea, button'))?.focus();
      });
    }

    function closeModal() {
      modal.classList.remove('is-open');
      modal.hidden = true;
      document.body.classList.remove('support-modal-open');
      if (lastFocus && typeof lastFocus.focus === 'function') {
        lastFocus.focus();
      }
    }
  }

  function initUploader(uploader) {
    if (uploader.dataset.ready === 'true') return;
    uploader.dataset.ready = 'true';

    const input = uploader.querySelector('[data-ticket-file-input]');
    const button = uploader.querySelector('[data-ticket-upload-button]');
    const list = uploader.querySelector('[data-ticket-file-list]');
    const counter = uploader.querySelector('[data-ticket-file-count]');
    if (!input || !button || !list || !counter) return;

    let selected = [];

    button.addEventListener('click', function () {
      input.click();
    });

    input.addEventListener('change', function () {
      addFiles(input.files);
      syncFiles();
      render();
    });

    ['dragenter', 'dragover'].forEach(function (name) {
      uploader.addEventListener(name, function (event) {
        event.preventDefault();
        uploader.classList.add('is-dragging');
      });
    });

    ['dragleave', 'drop'].forEach(function (name) {
      uploader.addEventListener(name, function (event) {
        event.preventDefault();
        uploader.classList.remove('is-dragging');
      });
    });

    uploader.addEventListener('drop', function (event) {
      addFiles(event.dataTransfer ? event.dataTransfer.files : []);
      syncFiles();
      render();
    });

    function addFiles(files) {
      Array.from(files || []).forEach(function (file) {
        if (!file || !file.type || !file.type.startsWith('image/')) return;
        if (selected.length >= maxFiles) return;
        const key = [file.name, file.size, file.lastModified].join(':');
        if (selected.some(function (item) { return item.key === key; })) return;
        selected.push({ key: key, file: file, url: URL.createObjectURL(file) });
      });
    }

    function removeFile(key) {
      selected = selected.filter(function (item) {
        if (item.key === key) {
          URL.revokeObjectURL(item.url);
          return false;
        }
        return true;
      });
      syncFiles();
      render();
    }

    function syncFiles() {
      try {
        const transfer = new DataTransfer();
        selected.forEach(function (item) { transfer.items.add(item.file); });
        input.files = transfer.files;
      } catch (ignored) {
        // Native file input remains usable where DataTransfer assignment is blocked.
      }
    }

    function render() {
      list.innerHTML = '';
      list.hidden = selected.length === 0;
      counter.textContent = selected.length ? selected.length + ' ảnh đã chọn' : 'Chưa chọn ảnh';

      selected.forEach(function (item) {
        const thumb = document.createElement('div');
        thumb.className = 'support-upload-thumb';
        thumb.innerHTML =
          '<img src="' + escapeAttr(item.url) + '" alt="Ảnh đã chọn">' +
          '<span>' + escapeHtml(shortName(item.file.name)) + '</span>' +
          '<button type="button" aria-label="Xóa ảnh">×</button>';
        thumb.querySelector('button').addEventListener('click', function () {
          removeFile(item.key);
        });
        list.appendChild(thumb);
      });
    }
  }

  function setError(form, field, message) {
    const input = form.querySelector('[data-ticket-field="' + field + '"]');
    const error = form.querySelector('[data-ticket-error="' + field + '"]');
    if (input) input.classList.toggle('is-invalid', !!message);
    if (error) error.textContent = message;
  }

  function value(input) {
    return input && input.value ? input.value.trim() : '';
  }

  function shortName(name) {
    const value = String(name || 'Ảnh');
    return value.length > 20 ? value.slice(0, 10) + '...' + value.slice(-7) : value;
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

  function escapeHtml(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }
})();
