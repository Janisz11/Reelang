/** Mirrors util/FileValidator.kt from the Android app. */

export const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
export const MAX_VIDEO_BYTES = 100 * 1024 * 1024;

export type ValidationResult =
  | { kind: "ok" }
  | { kind: "needs-compression" }
  | { kind: "error"; message: string };

export function validateFile(mimeType: string, sizeBytes: number): ValidationResult {
  if (sizeBytes <= 0) return { kind: "error", message: "File is empty. Please choose a valid file." };

  if (mimeType.startsWith("image/")) {
    return sizeBytes > MAX_IMAGE_BYTES ? { kind: "needs-compression" } : { kind: "ok" };
  }

  if (!mimeType.startsWith("video/")) {
    return { kind: "error", message: "Only video or image files are accepted." };
  }

  return sizeBytes > MAX_VIDEO_BYTES
    ? { kind: "error", message: "Video is too large (max 100MB). Please choose a shorter clip." }
    : { kind: "ok" };
}

/** Downscales an oversized image to fit MAX_IMAGE_BYTES, matching the Android compression step. */
export async function compressImage(file: File): Promise<File> {
  const bitmap = await createImageBitmap(file);
  const maxDimension = 1920;
  const scale = Math.min(1, maxDimension / Math.max(bitmap.width, bitmap.height));

  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);

  const context = canvas.getContext("2d");
  if (!context) return file;
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();

  for (const quality of [0.85, 0.7, 0.55, 0.4]) {
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", quality));
    if (blob && blob.size <= MAX_IMAGE_BYTES) {
      return new File([blob], file.name.replace(/\.[^.]+$/, ".jpg"), { type: "image/jpeg" });
    }
  }

  return file;
}
