import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import { buildExercise, CONFIG_FIELDS, TYPE_OPTIONS, validateConfig } from '@/app/exercise-editor';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import type { Exercise, ExerciseType } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';

function slugify(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+|-+$)/g, '');
}

type Props = {
  onCreate: (exercise: Exercise) => void;
  onCancel: () => void;
};

/**
 * A mini version of exercise-editor.tsx's own form, embedded inline wherever a quick-add is offered
 * (workout-editor.tsx's exercise/circuit pickers) rather than navigating to a separate screen — the
 * caller's own in-progress draft (a workout being built) is unpersisted local state, and navigating
 * away and back would need real plumbing to avoid losing it.
 */
export function NewExerciseForm({ onCreate, onCancel }: Props) {
  const theme = useTheme();
  const [name, setName] = useState('');
  const [type, setType] = useState<ExerciseType>('reps');
  const [values, setValues] = useState<Record<string, string>>({});
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);

  const setField = (key: string, text: string) => setValues((prev) => ({ ...prev, [key]: text }));

  const create = () => {
    if (!name.trim()) {
      setError('Name is required.');
      return;
    }
    const id = slugify(name);
    if (!id) {
      setError('Could not derive an id from that name.');
      return;
    }
    // Same check the full editor runs — this form writes to the store just as directly, so skipping
    // it here would leave the 0-sets hole open through the quick-add path.
    const configError = validateConfig(type, values);
    if (configError) {
      setError(configError);
      return;
    }
    onCreate(buildExercise(id, name.trim(), type, values, notes));
  };

  return (
    <View style={[styles.container, { borderColor: theme.border }]}>
      <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
        Name
      </ThemedText>
      <TextInput
        value={name}
        onChangeText={setName}
        placeholder="e.g. Front Lever"
        placeholderTextColor={theme.textSecondary}
        autoFocus
        style={[styles.input, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
      />

      <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
        Type
      </ThemedText>
      <View style={styles.typeRow}>
        {TYPE_OPTIONS.map((option) => {
          const active = option.type === type;
          return (
            <Pressable
              key={option.type}
              onPress={() => setType(option.type)}
              style={[
                styles.typePill,
                active
                  ? { backgroundColor: theme.text }
                  : { backgroundColor: theme.background, borderWidth: 1, borderColor: theme.border },
              ]}>
              <ThemedText type="small" style={{ color: active ? theme.onAccent : theme.textSecondary }}>
                {option.label}
              </ThemedText>
            </Pressable>
          );
        })}
      </View>

      <View style={styles.configGrid}>
        {CONFIG_FIELDS[type].map((field) => (
          <View key={field.key} style={styles.configField}>
            <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
              {field.label} {field.unit ? `(${field.unit})` : ''}
              {field.optional ? ' · optional' : ''}
            </ThemedText>
            <TextInput
              value={values[field.key] ?? ''}
              onChangeText={(text) => setField(field.key, text)}
              keyboardType="numeric"
              placeholder="0"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
            />
          </View>
        ))}
      </View>

      <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
        Notes · optional
      </ThemedText>
      <TextInput
        value={notes}
        onChangeText={setNotes}
        placeholder="Coaching cue…"
        placeholderTextColor={theme.textSecondary}
        style={[styles.input, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
      />

      {error && (
        <ThemedText type="small" style={[styles.error, { color: theme.accentText }]}>
          {error}
        </ThemedText>
      )}

      <View style={styles.buttonRow}>
        <Pressable onPress={onCancel} style={[styles.cancelButton, { borderColor: theme.border }]}>
          <ThemedText type="small" themeColor="textSecondary">
            Cancel
          </ThemedText>
        </Pressable>
        <Pressable onPress={create} style={[styles.createButton, { backgroundColor: theme.accent }]}>
          <ThemedText type="small" style={{ color: theme.onAccent }}>
            Create & add
          </ThemedText>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.two + 4,
    gap: 2,
  },
  label: {
    marginTop: Spacing.two - 2,
    marginBottom: 4,
  },
  input: {
    height: 40,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: Spacing.one + 4,
    fontSize: 14,
  },
  typeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.one,
  },
  typePill: {
    paddingHorizontal: Spacing.two - 2,
    paddingVertical: 6,
    borderRadius: 999,
  },
  configGrid: {
    marginTop: 2,
  },
  configField: {},
  error: {
    marginTop: Spacing.two - 2,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: Spacing.one + 2,
    marginTop: Spacing.two + 2,
  },
  cancelButton: {
    flex: 1,
    height: 40,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  createButton: {
    flex: 1.4,
    height: 40,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
