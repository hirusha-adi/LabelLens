// Reads the text on a label photo, in the browser. The photo never leaves the page:
// the recognition engine and its language data are served by this site (see
// vite.config.ts) and run in a worker here.

export interface Reading {
  /** Resolves with the text found. Rejects if reading fails or is cancelled. */
  text: Promise<string>;
  cancel: () => void;
}

export const readLabel = (image: Blob, onProgress?: (fraction: number) => void): Reading => {
  let cancelled = false;
  let stop: (() => void) | null = null;

  const text = (async () => {
    // Loaded on first use: most visits never scan, and the engine is several megabytes.
    const { createWorker } = await import("tesseract.js");
    const worker = await createWorker("eng", 1, {
      workerPath: "/ocr/worker.min.js",
      corePath: "/ocr",
      langPath: "/ocr",
      gzip: true,
      logger: (message) => message.status === "recognizing text" && onProgress?.(message.progress),
    });
    stop = () => void worker.terminate();
    if (cancelled) {
      stop();
      throw new DOMException("Cancelled", "AbortError");
    }
    try {
      const result = await worker.recognize(image);
      return result.data.text.replace(/\s+/g, " ").trim();
    } finally {
      await worker.terminate();
    }
  })();

  return {
    text,
    cancel: () => {
      cancelled = true;
      stop?.();
    },
  };
};
