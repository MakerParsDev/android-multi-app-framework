import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: [".test-dist/**"],
  },
  ...tseslint.configs.recommended,
  {
    rules: {
      // Allows destructuring protected/server-owned fields out of a client
      // request body purely to exclude them from a `...rest` spread (see
      // handleAdminSaveScheduledEvent), without requiring each one to be
      // referenced.
      "@typescript-eslint/no-unused-vars": ["error", { ignoreRestSiblings: true }],
    },
  },
);
