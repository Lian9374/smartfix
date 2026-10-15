/* Progressive enhancement: native swipe/scroll and links also work without JS. */
(() => {
  document.querySelectorAll('[data-announcement-carousel]').forEach((root) => {
    const track = root.querySelector('[data-announcement-track]');
    const controls = root.querySelector('[data-announcement-controls]');
    if (!track || !controls) return;
    const slides = [...track.children];
    const previous = controls.querySelector('[data-announcement-prev]');
    const next = controls.querySelector('[data-announcement-next]');
    const position = controls.querySelector('[data-announcement-position]');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let index = 0;
    let scrollTimer;
    const left = (slide) => slide.offsetLeft - slides[0].offsetLeft;
    const renderControls = () => {
      previous.disabled = index === 0;
      next.disabled = index === slides.length - 1;
      const label = `${index + 1} / ${slides.length}`;
      if (position.textContent !== label) position.textContent = label;
    };
    const update = () => {
      index = slides.reduce((closest, slide, candidate) =>
        Math.abs(left(slide) - track.scrollLeft) < Math.abs(left(slides[closest]) - track.scrollLeft)
          ? candidate : closest, 0);
      renderControls();
    };
    const go = (target, animate = true) => {
      target = Math.max(0, Math.min(slides.length - 1, target));
      const smooth = animate && !reducedMotion.matches && Math.abs(target - index) === 1;
      index = target;
      renderControls();
      track.scrollTo({ left: left(slides[target]), behavior: smooth ? 'smooth' : 'instant' });
    };
    previous.addEventListener('click', () => go(index - 1));
    next.addEventListener('click', () => go(index + 1));
    track.addEventListener('keydown', (event) => {
      // Preserve arrow keys on links or any controls added inside a notice.
      if (event.target !== track) return;
      const targets = { ArrowLeft: index - 1, ArrowRight: index + 1, Home: 0, End: slides.length - 1 };
      if (!(event.key in targets)) return;
      event.preventDefault();
      go(targets[event.key]);
    });
    track.addEventListener('scroll', () => {
      clearTimeout(scrollTimer);
      scrollTimer = setTimeout(update, 120);
    }, { passive: true });
    if ('ResizeObserver' in window) {
      new ResizeObserver(() => { go(index, false); }).observe(track);
    } else {
      window.addEventListener('resize', () => go(index, false));
    }
    controls.hidden = false;
    root.classList.add('is-enhanced');
    update();
  });
})();
