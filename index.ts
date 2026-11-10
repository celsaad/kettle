/**
 * The RN app's entry since routing moved native (#309): the shell still starts the runtime and lays the
 * surface out under everything, and the surface draws nothing. M7 deletes both, and this file with them
 * (docs/native-migration-plan.md).
 */
import { registerRootComponent } from 'expo';

registerRootComponent(() => null);
