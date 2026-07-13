# Code Conventions

**Language**: Kotlin preferred over Java. Use newest stable tooling.

**Commits**: Conventional Commits (feat/fix/docs/chore/test/ci/refactor/build), optional scope, imperative summary.
- No `Co-Authored-By` trailer
- Mark robot-authored commits with `:robot:` emoji right after type (e.g., `feat: :robot: add feature`)
- Linear history; work on short-lived feature branches

**Shell Scripts**: Avoid inline Bash with complex escaping/quoting/heredocs — these require prompts.
Use dedicated file tools (read/edit/write, Serena). Throwaway scripts go in `tmp/` (gitignored).

**Markdown**: No bold/italic for inline emphasis in prose (READMEs, specs, docs).
Convey emphasis with sentence structure; reserve markup for headings, lists, code spans/blocks.

**TDD Pattern**: Write tests first for pure-logic units (`ScheduleEngine`, `ColloquialTimeFormatter`).
Pragmatic tests elsewhere; full TDD may tighten later.

**Serena Usage**: Prefer symbolic tools (get_symbols_overview, find_symbol with include_body, replace_symbol_body)
over Read/Edit for Kotlin files. Edit for small XML/config is fine.
