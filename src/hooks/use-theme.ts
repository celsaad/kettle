import { useAppTheme } from '@/hooks/theme-context';

export function useTheme() {
  return useAppTheme().colors;
}
