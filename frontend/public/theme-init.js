// Applies the saved theme before Angular starts, so the page never flashes in the wrong colours.
// Same storage key and values as ThemeStore (src/app/core/theme.ts).
(function () {
  var mode = 'system';
  try {
    mode = localStorage.getItem('ecom.theme') || 'system';
  } catch (e) {
    // storage unavailable (private browsing, blocked cookies): follow the system
  }
  var dark = mode === 'dark' || (mode !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
})();
