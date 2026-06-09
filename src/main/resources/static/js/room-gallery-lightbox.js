(function () {
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-gallery-root]').forEach(initGallery);
    });

    function initGallery(root) {
        const triggers = Array.from(root.querySelectorAll('[data-gallery-image]'));
        const images = triggers
            .map(function (trigger) {
                const img = trigger.querySelector('img');
                return {
                    src: trigger.dataset.gallerySrc || (img ? img.currentSrc || img.src : ''),
                    alt: trigger.dataset.galleryAlt || (img ? img.alt : '')
                };
            })
            .filter(function (item) { return item.src; });

        if (!images.length) return;

        const lightbox = createLightbox();
        let index = 0;

        triggers.forEach(function (trigger, triggerIndex) {
            trigger.addEventListener('click', function () {
                open(triggerIndex);
            });
        });

        lightbox.close.addEventListener('click', close);
        lightbox.backdrop.addEventListener('click', function (event) {
            if (event.target === lightbox.backdrop) close();
        });
        lightbox.prev.addEventListener('click', function () { move(-1); });
        lightbox.next.addEventListener('click', function () { move(1); });

        document.addEventListener('keydown', function (event) {
            if (!lightbox.backdrop.classList.contains('is-open')) return;
            if (event.key === 'Escape') close();
            if (event.key === 'ArrowLeft') move(-1);
            if (event.key === 'ArrowRight') move(1);
        });

        function open(nextIndex) {
            index = Math.max(0, Math.min(nextIndex, images.length - 1));
            render();
            lightbox.backdrop.classList.add('is-open');
            document.body.classList.add('room-lightbox-open');
            lightbox.close.focus({ preventScroll: true });
        }

        function close() {
            lightbox.backdrop.classList.remove('is-open');
            document.body.classList.remove('room-lightbox-open');
        }

        function move(delta) {
            index = (index + delta + images.length) % images.length;
            render();
        }

        function render() {
            const current = images[index];
            lightbox.image.src = current.src;
            lightbox.image.alt = current.alt || '';
            lightbox.counter.textContent = (index + 1) + ' / ' + images.length;
            lightbox.prev.hidden = images.length <= 1;
            lightbox.next.hidden = images.length <= 1;
        }
    }

    function createLightbox() {
        let backdrop = document.querySelector('[data-room-lightbox]');
        if (!backdrop) {
            backdrop = document.createElement('div');
            backdrop.className = 'room-lightbox';
            backdrop.dataset.roomLightbox = '';
            backdrop.innerHTML = [
                '<button type="button" class="room-lightbox-close" aria-label="Đóng ảnh"><i data-lucide="x" class="w-5 h-5"></i></button>',
                '<div class="room-lightbox-counter tnum"></div>',
                '<button type="button" class="room-lightbox-nav is-prev" aria-label="Ảnh trước"><i data-lucide="chevron-left" class="w-6 h-6"></i></button>',
                '<img class="room-lightbox-image" alt="" />',
                '<button type="button" class="room-lightbox-nav is-next" aria-label="Ảnh tiếp theo"><i data-lucide="chevron-right" class="w-6 h-6"></i></button>'
            ].join('');
            document.body.appendChild(backdrop);
            if (window.lucide && typeof window.lucide.createIcons === 'function') {
                window.lucide.createIcons();
            }
        }

        return {
            backdrop: backdrop,
            close: backdrop.querySelector('.room-lightbox-close'),
            prev: backdrop.querySelector('.room-lightbox-nav.is-prev'),
            next: backdrop.querySelector('.room-lightbox-nav.is-next'),
            image: backdrop.querySelector('.room-lightbox-image'),
            counter: backdrop.querySelector('.room-lightbox-counter')
        };
    }
})();
