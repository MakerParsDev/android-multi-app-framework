import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: ["lib/**"],
  },
  ...tseslint.configs.recommended,
);
