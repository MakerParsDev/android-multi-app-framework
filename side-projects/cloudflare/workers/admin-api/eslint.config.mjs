import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: [".test-dist/**"],
  },
  ...tseslint.configs.recommended,
);
