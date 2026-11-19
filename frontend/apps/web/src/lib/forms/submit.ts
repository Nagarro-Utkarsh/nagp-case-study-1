export async function submitForm(
  event: SubmitEvent,
  action: () => Promise<void>,
  onError: (error: unknown) => void
): Promise<void> {
  event.preventDefault();
  try {
    await action();
  } catch (error) {
    onError(error);
  }
}
