import {
  HankenGrotesk_400Regular,
  HankenGrotesk_500Medium,
  HankenGrotesk_600SemiBold,
} from '@expo-google-fonts/hanken-grotesk';
import { SpaceGrotesk_500Medium, SpaceGrotesk_600SemiBold, SpaceGrotesk_700Bold } from '@expo-google-fonts/space-grotesk';

/**
 * The faces `expo-font` loads, keyed by the names `theme.json`'s `Fonts` uses. The imports have to be
 * static for Metro to bundle them, so this can't be built from `Fonts`; `font-faces.test.ts` holds the
 * two to the same set instead, since native bundles whatever `Fonts` names and RN only what loads here.
 */
export const FontFaces = {
  SpaceGrotesk_500Medium,
  SpaceGrotesk_600SemiBold,
  SpaceGrotesk_700Bold,
  HankenGrotesk_400Regular,
  HankenGrotesk_500Medium,
  HankenGrotesk_600SemiBold,
};
