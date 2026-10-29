/* Bloo site. Small and optional: the page reads and works with scripting off; this only adds the demo controls and motion. */
(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  // Reveal on scroll, once per element.
  const reveals = $$('.reveal');
  if ('IntersectionObserver' in window && !reduce) {
    const io = new IntersectionObserver(entries => entries.forEach(e => {
      if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
    }), { threshold: 0.12 });
    reveals.forEach((el, i) => { el.style.setProperty('--d', (i % 4) * 0.06 + 's'); io.observe(el); });
  } else {
    reveals.forEach(el => el.classList.add('in'));
  }

  // Card spotlight: the soft light that follows the pointer across a card.
  $$('.card').forEach(card => card.addEventListener('pointermove', e => {
    const r = card.getBoundingClientRect();
    card.style.setProperty('--cx', e.clientX - r.left + 'px');
    card.style.setProperty('--cy', e.clientY - r.top + 'px');
  }));

  // The demo car: the lock switch, the cabin slider and the climate button drive the status text.
  const lock = $('#lockSwitch');
  const lockStatus = $('#lockStatus');
  if (lock) lock.addEventListener('click', () => {
    const on = lock.getAttribute('aria-checked') !== 'true';
    lock.setAttribute('aria-checked', String(on));
    lock.setAttribute('aria-label', on ? 'Doors locked' : 'Doors unlocked');
    lockStatus.textContent = on ? 'Locked' : 'Unlocked';
  });

  const slider = $('#tempSlider');
  const temp = $('#dialTemp');
  if (slider) slider.addEventListener('input', () => { temp.textContent = slider.value + '°'; });

  const climate = $('#climateBtn');
  const climateLabel = $('#climateLabel');
  if (climate) climate.addEventListener('click', () => {
    const running = climateLabel.textContent === 'Stop climate';
    climateLabel.textContent = running ? 'Start climate' : 'Stop climate';
  });

  // Light and dark preview, driven by the segmented button.
  const preview = $('#preview');
  $$('.segmented button').forEach(btn => btn.addEventListener('click', () => {
    $$('.segmented button').forEach(b => b.setAttribute('aria-pressed', String(b === btn)));
    preview.classList.toggle('dark', btn.dataset.theme === 'dark');
    preview.classList.toggle('light', btn.dataset.theme !== 'dark');
  }));
})();
