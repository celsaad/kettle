import * as Sharing from 'expo-sharing';

import { sessionFile, storagePaths } from '@/storage/paths';

async function share(uri: string, dialogTitle: string): Promise<void> {
  const available = await Sharing.isAvailableAsync();
  if (!available) throw new Error('Sharing is not available on this device');
  await Sharing.shareAsync(uri, { mimeType: 'application/x-yaml', dialogTitle });
}

export function exportLibrary(): Promise<void> {
  return share(storagePaths.libraryFile.uri, 'Export exercises.yaml');
}

export function exportSession(sessionId: string): Promise<void> {
  return share(sessionFile(sessionId).uri, `Export session ${sessionId}`);
}
