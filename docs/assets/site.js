/* Bloo site: small, dependency-free, and every effect is optional (the page is fully usable with scripting off). */
(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  // a soft glow that follows the pointer
  if (!reduce) addEventListener('pointermove', e => {
    document.documentElement.style.setProperty('--mx', e.clientX + 'px');
    document.documentElement.style.setProperty('--my', e.clientY + 'px');
  }, { passive: true });

  // reveal on scroll
  const io = 'IntersectionObserver' in window ? new IntersectionObserver(es => es.forEach(e => {
    if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
  }), { threshold: .15 }) : null;
  $$('.reveal').forEach((el, i) => { el.style.setProperty('--d', (i % 4) * 0.07 + 's'); io ? io.observe(el) : el.classList.add('in'); });

  // rotating word in the headline
  const rot = $('.rot');
  if (rot && !reduce) {
    const words = (rot.dataset.words || '').split('|').filter(Boolean);
    let i = 0; const span = rot.firstElementChild;
    if (words.length > 1) setInterval(() => {
      span.className = 'out';
      setTimeout(() => { i = (i + 1) % words.length; span.textContent = words[i]; span.className = 'in'; }, 340);
    }, 2400);
  }

  // count-up numbers
  $$('[data-count]').forEach(el => {
    const to = +el.dataset.count; const done = v => { el.textContent = v + (el.dataset.suffix || ''); };
    if (reduce || !io) return done(to);
    new IntersectionObserver(([e], ob) => {
      if (!e.isIntersecting) return; ob.disconnect();
      const t0 = performance.now();
      const tick = t => { const p = Math.min(1, (t - t0) / 1200); done(Math.round(to * (1 - Math.pow(1 - p, 3)))); if (p < 1) requestAnimationFrame(tick); };
      requestAnimationFrame(tick);
    }, { threshold: .6 }).observe(el);
  });

  // card spotlight
  $$('.card').forEach(c => c.addEventListener('pointermove', e => {
    const r = c.getBoundingClientRect();
    c.style.setProperty('--cx', e.clientX - r.left + 'px'); c.style.setProperty('--cy', e.clientY - r.top + 'px');
  }));

  // tilt on the hero phones
  const stage = $('.stage');
  if (stage && !reduce) {
    stage.addEventListener('pointermove', e => {
      const r = stage.getBoundingClientRect(); const x = (e.clientX - r.left) / r.width - .5; const y = (e.clientY - r.top) / r.height - .5;
      $$('.phone', stage).forEach((p, i) => { const k = (i + 1) * 5; p.style.translate = `${x * k}px ${y * k * .6}px`; });
    });
    stage.addEventListener('pointerleave', () => $$('.phone', stage).forEach(p => (p.style.translate = '')));
  }

  // the lock switch in the Control card
  const sw = $('#lockSwitch');
  if (sw) sw.addEventListener('click', () => {
    const on = sw.getAttribute('aria-pressed') !== 'true'; sw.setAttribute('aria-pressed', on);
    $('#lockState').textContent = on ? 'Locked' : 'Unlocked';
  });

  // climate dial
  const dial = $('#dial');
  if (dial) { const t = $('#dialTemp'); let f = 72; dial.addEventListener('click', () => { f = f >= 78 ? 62 : f + 2; t.textContent = f + '°'; dial.style.setProperty('--deg', (f - 58) * 17 + 'deg'); }); }

  // light / dark showcase toggle
  const stack = $('.shot-stack');
  $$('.seg button').forEach(b => b.addEventListener('click', () => {
    $$('.seg button').forEach(x => x.setAttribute('aria-pressed', x === b));
    stack.classList.toggle('dark', b.dataset.theme === 'dark');
  }));
})();
