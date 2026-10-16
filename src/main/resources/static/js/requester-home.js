/* Manual highlights carousel: native scrolling is the no-JS fallback. */
(() => {
  const root = document.querySelector('[data-home-carousel]');
  if (!root) return;
  const track = root.querySelector('[data-home-track]');
  const slides = [...track.querySelectorAll('[data-home-slide]')];
  const controls = root.querySelector('[data-home-controls]');
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  if (controls && slides.length > 1) {
    const previous = controls.querySelector('[data-home-prev]');
    const next = controls.querySelector('[data-home-next]');
    const position = controls.querySelector('[data-home-position]');
    const dots = [...controls.querySelectorAll('[data-home-go]')];
    let index = 0;
    let timer;
    let drag = null;
    const left = i => slides[i].offsetLeft - slides[0].offsetLeft;
    const render = () => {
      previous.disabled = index === 0;
      next.disabled = index === slides.length - 1;
      dots.forEach((dot, i) => dot.setAttribute('aria-pressed', String(i === index)));
      const label = `${index + 1} / ${slides.length}`;
      if (position.textContent !== label) position.textContent = label;
    };
    const go = (target, animate = true) => {
      target = Math.max(0, Math.min(slides.length - 1, target));
      const smooth = animate && !motion.matches && Math.abs(target - index) === 1;
      index = target;
      render();
      track.scrollTo({ left: left(target), behavior: smooth ? 'smooth' : 'instant' });
    };
    const update = () => {
      index = slides.reduce((nearest, _, candidate) =>
        Math.abs(track.scrollLeft - left(candidate)) < Math.abs(track.scrollLeft - left(nearest)) ? candidate : nearest, 0);
      render();
    };
    previous.addEventListener('click', () => go(index - 1));
    next.addEventListener('click', () => go(index + 1));
    dots.forEach(dot => dot.addEventListener('click', () => go(Number(dot.dataset.homeGo))));
    track.addEventListener('keydown', event => {
      if (event.target !== track) return;
      const keys = { ArrowLeft: index - 1, ArrowRight: index + 1, Home: 0, End: slides.length - 1 };
      if (!(event.key in keys)) return;
      event.preventDefault();
      go(keys[event.key]);
    });
    track.addEventListener('scroll', () => {
      if (drag && drag.active) return;
      clearTimeout(timer);
      timer = setTimeout(update, 120);
    }, { passive: true });
    // Touch uses the browser's native scrolling. Desktop users can also drag.
    track.addEventListener('pointerdown', event => {
      if (event.pointerType !== 'mouse' || event.button !== 0 || event.target.closest('a,button,input')) return;
      drag = { id: event.pointerId, x: event.clientX, y: event.clientY, left: track.scrollLeft, index, active: false };
    });
    track.addEventListener('pointermove', event => {
      if (!drag || event.pointerId !== drag.id) return;
      const dx = event.clientX - drag.x;
      if (!drag.active && Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(event.clientY - drag.y)) {
        drag.active = true;
        clearTimeout(timer);
        track.setPointerCapture(event.pointerId);
        track.classList.add('is-dragging');
      }
      if (drag.active) { event.preventDefault(); track.scrollLeft = drag.left - dx; }
    });
    const finishDrag = event => {
      if (!drag || event.pointerId !== drag.id) return;
      const finished = drag;
      drag = null;
      if (!finished.active) return;
      const distance = track.scrollLeft - finished.left;
      const nearest = slides.reduce((best, _, candidate) =>
        Math.abs(track.scrollLeft - left(candidate)) < Math.abs(track.scrollLeft - left(best)) ? candidate : best, 0);
      track.classList.remove('is-dragging');
      if (track.hasPointerCapture(event.pointerId)) track.releasePointerCapture(event.pointerId);
      // A short deliberate swipe advances once; long drags settle at the closest slide.
      go(event.type === 'pointercancel' ? nearest : nearest === finished.index && Math.abs(distance) > 40
        ? finished.index + Math.sign(distance) : nearest);
    };
    track.addEventListener('pointerup', finishDrag);
    track.addEventListener('pointercancel', finishDrag);
    track.addEventListener('lostpointercapture', finishDrag);
    track.addEventListener('pointerleave', () => { if (drag && !drag.active) drag = null; });
    if ('ResizeObserver' in window) new ResizeObserver(() => go(index, false)).observe(track);
    else window.addEventListener('resize', () => go(index, false));
    root.classList.add('is-enhanced');
    root.setAttribute('aria-roledescription', 'carousel');
    controls.hidden = false;
    render();
  }
  const welcome = slides[0];
  const pointer = matchMedia('(hover: hover) and (pointer: fine)');
  const reset = () => {
    welcome.style.removeProperty('--home-art-x');
    welcome.style.removeProperty('--home-art-y');
  };
  welcome.addEventListener('pointermove', event => {
    if (motion.matches || !pointer.matches) return;
    const rect = welcome.getBoundingClientRect();
    welcome.style.setProperty('--home-art-x', `${(event.clientX - rect.left - rect.width / 2) / rect.width * 8}px`);
    welcome.style.setProperty('--home-art-y', `${(event.clientY - rect.top - rect.height / 2) / rect.height * 6}px`);
  });
  welcome.addEventListener('pointerleave', reset);
  motion.addEventListener('change', reset);
})();
