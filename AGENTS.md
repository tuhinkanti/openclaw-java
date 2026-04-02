# AGENTS.md

- Prefer minimal Java: records, switch expressions, `var`, fail-fast guards, and small methods.
- Keep nesting shallow. Extract private methods instead of stacking conditionals.
- Use built-in JDK features before adding framework code.
- Keep plugins and tools read-only by default unless mutation is explicitly required.
- Favor the smallest change that preserves tests and clarity.
