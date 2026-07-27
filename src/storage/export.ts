import * as Sharing from 'expo-sharing';

import { sessionFile, storagePaths } from '@/storage/paths';

async function share(uri: string, dialogTitle: string): Promise<void> {
  const available = await Sharing.isAvailableAsync();
  if (!available) throw new Error('Sharing is not available on this device');
  await Sharing.shareAsync(uri, { mimeType: 'application/x-yaml', dialogTitle });
}

// Both are `async` for a specific reason, not style: resolving `.uri` constructs an expo-file-system
// File/Directory, which has no web implementation and throws *synchronously* — before `share()`'s own
// async body is ever entered. Without `async` here that throw escapes past the returned promise
// entirely, so the `.catch()` every caller already has never runs and it surfaces as an unhandled
// error. `async` turns it into a rejection those handlers can actually see.
export async function exportLibrary(): Promise<void> {
  return share(storagePaths.libraryFile.uri, 'Export exercises.yaml');
}

export async function exportSession(sessionId: string): Promise<void> {
  return share(sessionFile(sessionId).uri, `Export session ${sessionId}`);
}
