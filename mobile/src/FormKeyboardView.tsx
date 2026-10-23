import type { ReactNode } from 'react';
import { KeyboardAvoidingView, Platform } from 'react-native';
import { styles as s } from './theme';

// Keyboard adjustments belong inside a screen, not around the native navigator.
export function FormKeyboardView({ children }: { children: ReactNode }) {
  return <KeyboardAvoidingView style={s.root} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>{children}</KeyboardAvoidingView>;
}
