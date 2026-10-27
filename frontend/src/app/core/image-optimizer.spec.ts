import { fitWithin, formatSize, ImageOptimizationError, MAX_ORIGINAL_SIZE, optimizeImage } from './image-optimizer';

describe('image optimizer', () => {
  it('scales the longest side down to the limit, keeping the proportions', () => {
    expect(fitWithin(4032, 3024, 1600)).toEqual({ width: 1600, height: 1200 });
    expect(fitWithin(1080, 1920, 1600)).toEqual({ width: 900, height: 1600 });
  });

  it('never enlarges a small image', () => {
    expect(fitWithin(640, 480, 1600)).toEqual({ width: 640, height: 480 });
  });

  it('formats sizes the French way', () => {
    expect(formatSize(184_320)).toBe('180 Ko');
    expect(formatSize(4.2 * 1024 * 1024)).toBe('4,2 Mo');
    expect(formatSize(10)).toBe('1 Ko');
  });

  it('refuses files that are not images', async () => {
    const pdf = new File(['%PDF'], 'devis.pdf', { type: 'application/pdf' });

    await expect(optimizeImage(pdf)).rejects.toEqual(new ImageOptimizationError('not-an-image'));
  });

  it('refuses originals too large to decode safely', async () => {
    const huge = new File([new Uint8Array(MAX_ORIGINAL_SIZE + 1)], 'photo.jpg', { type: 'image/jpeg' });

    await expect(optimizeImage(huge)).rejects.toEqual(new ImageOptimizationError('too-large'));
  });
});
