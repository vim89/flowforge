# Rendering Mermaid and Diagrams

Mermaid in IntelliJ IDEA

- IDEA’s built-in Markdown preview does not render Mermaid. Options:
  - Install a Mermaid plugin (if available for your IDEA version), or
  - Use VS Code with the official Mermaid extension for authoring/preview, or
  - Use GitHub’s renderer (push a branch and view on GitHub), or
  - Export to SVG/PNG using the Mermaid CLI and commit the assets.

Validate and export with Mermaid CLI

- Install Node + Mermaid CLI (one-time):
  - npm install -g @mermaid-js/mermaid-cli
- Validate + export to SVG using .mmd sources we ship:
  - mmdc -i docs/diagrams/compile-time-contracts/src/flowchart.mmd -o docs/diagrams/compile-time-contracts/flowchart.svg
  - mmdc -i docs/diagrams/compile-time-contracts/src/scala2-uml.mmd -o docs/diagrams/compile-time-contracts/scala2-uml.svg
  - mmdc -i docs/diagrams/compile-time-contracts/src/scala3-uml.mmd -o docs/diagrams/compile-time-contracts/scala3-uml.svg
  - Tip: add these as an npm script or Make target for quick local checks.

Online editors

- Mermaid Live Editor: paste the code block and preview; it flags syntax errors immediately.

Alternative formats

- PlantUML (well-supported in IDEA with plugins)
- draw.io/diagrams.net (GUI authoring; export to SVG and commit alongside the .drawio source)
- Excalidraw (hand-drawn style; export to PNG/SVG)

Our practice

- Keep a Mermaid source in the repo (for diffs), and export an SVG for environments that can’t render Mermaid (IDEA Markdown, PDF, etc.).
- Place exported assets under `docs/diagrams/**.svg` next to the Mermaid source.

Optionality diagram

- Source: `docs/diagrams/compile-time-contracts/optionality.md`
- Export: `mmdc -i docs/diagrams/compile-time-contracts/optionality.md -o docs/diagrams/compile-time-contracts/optionality.svg -b transparent`
- Link both `.md` and `.svg` in docs for best UX.
