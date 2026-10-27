import { TestBed } from '@angular/core/testing';
import { ThemeStore } from './theme';

describe('ThemeStore', () => {
  beforeEach(() => localStorage.clear());

  function store(): ThemeStore {
    return TestBed.inject(ThemeStore);
  }

  it('follows the system until the visitor chooses', () => {
    expect(store().mode()).toBe('system');
  });

  it('applies and remembers an explicit choice', () => {
    const theme = store();

    theme.setMode('dark');
    TestBed.tick();

    expect(theme.theme()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(localStorage.getItem('ecom.theme')).toBe('dark');
  });

  it('forgets the choice when going back to the system theme', () => {
    const theme = store();
    theme.setMode('light');

    theme.setMode('system');

    expect(localStorage.getItem('ecom.theme')).toBeNull();
  });

  it('restores the saved choice', () => {
    localStorage.setItem('ecom.theme', 'light');

    expect(store().mode()).toBe('light');
  });
});
