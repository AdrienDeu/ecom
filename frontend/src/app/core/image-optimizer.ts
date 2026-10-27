/** Longest side of a stored product image: sharp on high-density screens, light to download. */
export const MAX_IMAGE_SIDE = 1600;
export const IMAGE_QUALITY = 0.82;
/** Larger originals are refused before decoding, which could exhaust the tab's memory. */
export const MAX_ORIGINAL_SIZE = 40 * 1024 * 1024;

export type ImageProblem = 'not-an-image' | 'too-large' | 'unreadable';

export class ImageOptimizationError extends Error {
  constructor(readonly problem: ImageProblem) {
    super(problem);
  }
}

export interface OptimizedImage {
  file: File;
  width: number;
  height: number;
  originalSize: number;
}

/** Dimensions scaled down (never up) so that the longest side is at most `max`. */
export function fitWithin(width: number, height: number, max: number): { width: number; height: number } {
  const scale = Math.min(1, max / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/** "180 Ko", "4,2 Mo". */
export function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} Ko`;
  }
  return `${(bytes / (1024 * 1024)).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} Mo`;
}

/**
 * Turns any image the browser can read (JPEG, PNG, WebP, GIF, BMP, AVIF, HEIC on Safari…) into a product image the
 * shop accepts: upright (EXIF orientation applied), at most MAX_IMAGE_SIDE pixels, re-encoded as WebP — or JPEG
 * where the browser cannot encode WebP. Re-encoding also drops the photo's metadata (location, camera).
 */
export async function optimizeImage(file: File): Promise<OptimizedImage> {
  if (file.type && !file.type.startsWith('image/')) {
    throw new ImageOptimizationError('not-an-image');
  }
  if (file.size > MAX_ORIGINAL_SIZE) {
    throw new ImageOptimizationError('too-large');
  }
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    throw new ImageOptimizationError('unreadable');
  }
  const { width, height } = fitWithin(bitmap.width, bitmap.height, MAX_IMAGE_SIDE);
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (!context) {
    bitmap.close();
    throw new ImageOptimizationError('unreadable');
  }
  context.imageSmoothingQuality = 'high';
  context.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();

  let blob = await toBlob(canvas, 'image/webp');
  if (blob?.type !== 'image/webp') {
    // No WebP encoder (Safari): JPEG, on white since JPEG has no transparency
    context.globalCompositeOperation = 'destination-over';
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, width, height);
    blob = await toBlob(canvas, 'image/jpeg');
  }
  if (!blob) {
    throw new ImageOptimizationError('unreadable');
  }
  const extension = blob.type === 'image/webp' ? 'webp' : 'jpg';
  const name = `${file.name.replace(/\.[^.]*$/, '') || 'image'}.${extension}`;
  return { file: new File([blob], name, { type: blob.type }), width, height, originalSize: file.size };
}

function toBlob(canvas: HTMLCanvasElement, type: string): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, type, IMAGE_QUALITY));
}
