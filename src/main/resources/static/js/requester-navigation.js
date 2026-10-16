(() => {
  const header = document.querySelector('.appbar--requester');
  if (!header) return;
  const toggle = header.querySelector('[data-nav-toggle]');
  const panel = header.querySelector('[data-nav-panel]');
  const narrow = matchMedia('(max-width:900px)');
  let queued = false;
  const update = () => { header.classList.toggle('is-scrolled', scrollY > 8);queued = false; };
  addEventListener('scroll', () => {
    if (!queued) { queued = true;requestAnimationFrame(update); }
  }, { passive:true });
  update();
  if (!toggle || !panel) return;
  // Reuse the existing menu controller so ARIA state and keyboard focus stay in sync.
  document.addEventListener('pointerdown', event => {
    if (narrow.matches && panel.classList.contains('is-open') && !header.contains(event.target)) toggle.click();
  });
  const label = () => toggle.setAttribute('aria-label', toggle.getAttribute('aria-expanded') === 'true' ? 'Close navigation menu' : 'Open navigation menu');
  new MutationObserver(label).observe(toggle, { attributes:true,attributeFilter:['aria-expanded'] });
  label();
})();
