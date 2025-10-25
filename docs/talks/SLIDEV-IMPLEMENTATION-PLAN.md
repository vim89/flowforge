# Slidev Implementation Plan for ScalaIO 2025 Talk
## Complete Deep-Dive Study & Implementation Roadmap

> **Objective:** Build a professional, interactive presentation for "Compile-Time Contracts & Fiber-Safe Data Pipelines" using Slidev with 100% precision and accuracy.

---

## Part 1: Slidev Deep-Dive Summary

### 1.1 Core Capabilities Identified

**What Slidev Excels At:**
- ✅ **Markdown-first authoring** - perfect for our existing `ScalaIO-2025-Main-Talk.md` content
- ✅ **Native Mermaid support** - all 7 diagrams will render without conversion
- ✅ **Syntax highlighting** via Shiki - Scala code will look professional
- ✅ **Presenter mode** - built-in speaker notes and dual-screen support
- ✅ **Line-by-line code highlighting** - perfect for S05 red→green demo
- ✅ **Click animations** (`v-click`, `v-clicks`) - progressive disclosure for complex concepts
- ✅ **Export to PDF/PPTX** - deliverable for conference organizers
- ✅ **Monaco Editor** (optional) - live code editing if needed
- ✅ **Drawing tools** - live annotations during Q&A
- ✅ **Recording** - practice runs and post-conference sharing

### 1.2 Technology Stack

**Under the Hood:**
- **Vue 3** - reactive components for interactive elements
- **Vite** - fast hot module replacement during development
- **UnoCSS** - utility-first CSS for custom styling
- **Shiki** - code highlighting with 200+ language support
- **KaTeX** - LaTeX math rendering (not needed for our talk)
- **Mermaid** - diagram rendering engine (CRITICAL for our 7 diagrams)

### 1.3 File Structure

```
flowforge-slidev/
├── slides.md              # Main presentation file (single source of truth)
├── package.json           # Dependencies and scripts
├── components/            # Custom Vue components (optional)
├── layouts/               # Custom layouts (optional)
├── public/                # Static assets (logos, images)
├── setup/                 # Setup scripts (optional)
└── vite.config.ts         # Build configuration (optional)
```

---

## Part 2: Feature Mapping to Our Content

### 2.1 Slides → Slidev Layouts Mapping

| Slide ID | Content Type | Recommended Slidev Layout | Rationale |
|----------|--------------|---------------------------|-----------|
| Title | Talk title + subtitle | `cover` | Built-in layout for opening slide |
| S01a | About me (bullets) | `default` | Simple bullet list with avatar |
| S01b | Walmart context | `default` | Context-setting bullets |
| S01c | DE workflow diagram | `default` | Full-width Mermaid diagram |
| S01 | Scar story | `quote` or `statement` | Emphasize verbatim narrative |
| S02 | Boundaries + table | `default` | Decision matrix table |
| S02a | Boundary checkpoint | `fact` | Emphasize key distinctions |
| S03 | Policy table + code | `two-cols` | Code left, table right |
| S04 | 2 Mermaid diagrams + code | `default` with `v-clicks` | Progressive diagram reveal |
| S05 | Red→green code demo | `default` with line highlighting | MONEY SLIDE - annotated code |
| S06 | Typestate diagram + code | `two-cols` | Diagram left, code right |
| S07 | Config safety | `default` | Bullets + code snippet |
| S08 | DQ validation | `default` | Bullets + code snippet |
| S09a | Effect boundary | `default` | Concept explanation |
| S09b | Kleisli + sequence diagram | `default` | Code + Mermaid |
| S10 | Fiber-safe execution | `default` | Concept + bullets |
| S11 | Batteries included | `default` | Code example + bullets |
| S12 | Engine portability | `default` | Quick Q&A format |
| S13 | Architecture diagram | `default` | Full-width Mermaid |
| S13a | Boundary checkpoint | `fact` | Interactive pop quiz |
| S14 | Outcome takeaways | `default` with `v-clicks` | Progressive bullet reveal |
| S15 | Remember WHY | `image-left` or `default` | Visual circles + text |
| S16 | Invitation | `statement` | Call to action |

### 2.2 Mermaid Diagram Integration

**All 7 diagrams render natively in Slidev:**

1. **S01c - DE Workflow (TB flowchart)** → ` ```mermaid` block, full slide
2. **S04 - TypeRepr Recursion** → ` ```mermaid` block with `v-click` for step-by-step
3. **S04 - Evidence Flow** → ` ```mermaid` block, separate slide or `v-after`
4. **S06 - Typestate Builder (LR flowchart)** → ` ```mermaid` block in `two-cols` layout
5. **S09b - Kleisli Sequence** → ` ```mermaid` block, full slide
6. **S13 - Architecture (layered flowchart)** → ` ```mermaid` block, full slide

**Diagram Styling:**
- Use frontmatter `class: mermaid-large` for full-screen diagrams
- Use `style` tags in Mermaid for custom colors (e.g., highlighted "compile-time safety" box)

### 2.3 Code Highlighting Strategy

**Scala Code Blocks:**
```markdown
\`\`\`scala {1-3|5-7}{maxHeight:'400px'}
sealed trait SchemaPolicy
object SchemaPolicy {
  sealed trait Exact extends SchemaPolicy
  ...
}
\`\`\`
```

**Features to Use:**
- `{1-3|5-7}` - Line highlighting per click (perfect for S05 red→green)
- `{maxHeight:'400px'}` - Prevent code overflow
- `{lines:true}` - Line numbers for reference
- `// [!code highlight]` - Inline highlighting markers

**S05 Red→Green Demo Approach:**
```markdown
---
layout: default
---

# S05 – Red → Green Migration Demo

## Setup
\`\`\`scala {all}
case class Contract(id: Long, email: String)
case class Producer(id: Long, email: String, age: Int)
\`\`\`

<v-click>

## ❌ RED: Exact Policy
\`\`\`scala {1|3-7}{lines:true}
val _ = implicitly[SchemaConforms[Producer, Contract, SchemaPolicy.Exact]]

// Compiler error:
// [error] Compile-time contract drift (policy: SchemaPolicy.Exact)
// [error] Out: Producer vs Contract: Contract
// [error] Extra: age: Int
\`\`\`

</v-click>

<v-click>

## ✅ GREEN: Backward Policy
\`\`\`scala {1}
val _ = implicitly[SchemaConforms[Producer, Contract, SchemaPolicy.Backward]]
// Compiles successfully! ✅
\`\`\`

</v-click>
```

### 2.4 Speaker Notes Integration

**Slidev Syntax:**
```markdown
---
layout: default
---

# Slide Content Here

<!--
Speaker notes go in HTML comments at the end of the slide.
These appear in presenter mode only.
Can be multi-line and include reminders, timing, delivery tips.
-->
```

**For Our Talk:**
- Copy verbatim notes from `speaker-notes-ScalaIO-2025.md`
- Include timing targets (e.g., "Target end: 06:30")
- Include delivery tips (e.g., "Pause. Beat. Let it land.")
- Include transitions (e.g., "Transition: Time to see this in action")

---

## Part 3: Slidev Configuration & Setup

### 3.1 Headmatter (Global Configuration)

**Place at top of `slides.md`:**
```yaml
---
theme: default
# OR use a tech-focused theme like 'seriph', 'apple-basic', 'shibainu'
title: 'Compile-Time Contracts & Fiber-Safe Data Pipelines'
author: 'Vitthal Mirji'
keywords: 'Scala, Data Engineering, Compile-Time, Contracts, Fiber-Safe, FlowForge'
presenter: true
download: true
exportFilename: 'ScalaIO-2025-FlowForge-Talk'
record: dev
monaco: false  # Disable Monaco unless we need live editing
drawings:
  enabled: true
  persist: false
  presenterOnly: false
lineNumbers: true
colorSchema: 'dark'  # Dark theme for code-heavy presentation
fonts:
  sans: 'Inter'
  serif: 'Robot Slab'
  mono: 'Fira Code'
highlighter: shiki
aspectRatio: '16/9'
canvasWidth: 980
---
```

**Key Decisions:**
- **Theme:** Start with `default`, evaluate `seriph` (clean, minimal) or `apple-basic` (Apple Keynote style)
- **Presenter mode:** Enabled for speaker notes
- **Download:** Allow PDF export
- **Monaco:** Disabled (we don't need live code editing)
- **Drawings:** Enabled for Q&A annotations
- **Line numbers:** Enabled for code credibility
- **Color schema:** Dark (easier on eyes for code)
- **Fonts:** Inter (sans), Fira Code (mono for code)
- **Highlighter:** Shiki (best Scala support)

### 3.2 Per-Slide Frontmatter

**Example for S05 (Money Slide):**
```markdown
---
layout: default
class: text-center
background: '#1a1a1a'  # Dark background for code slide
clicks: 3              # 3 click animations (setup, red, green)
---
```

**Example for S01 (Scar Story):**
```markdown
---
layout: quote
author: 'Personal Experience'
class: text-xl leading-relaxed
---
```

### 3.3 Navigation & Shortcuts

**Default Slidev Shortcuts:**
- **Right Arrow / Space** - Next slide
- **Left Arrow** - Previous slide
- **Up / Down** - Navigate slides (skip clicks)
- **O** - Overview mode (see all slides)
- **G** - Goto slide (type number)
- **D** - Dark mode toggle
- **F** - Fullscreen
- **P** - Presenter mode
- **E** - Integrated editor
- **C** - Camera view (for recording)

**Custom Shortcuts (if needed):**
Can configure in `vite.config.ts` or via Vue components.

---

## Part 4: Animation & Transition Strategy

### 4.1 Click Animations for Progressive Disclosure

**Use `v-clicks` for bullet lists:**
```markdown
<v-clicks>

- **Sources:** Compile-time prevents schema drift
- **Quality:** Both technical & business DQ rules validated
- **Transform:** Pure functions for performance
- **Effects:** Side effects kept at edges—fiber-safe

</v-clicks>
```

**Use `v-click` for individual elements:**
```markdown
<div v-click>

## First concept appears

</div>

<div v-click>

## Second concept appears

</div>
```

**Use `v-after` for synchronized reveals:**
```markdown
<div v-click>

Contracts prove the schema *before* you run.

</div>

<div v-after>

Now let's talk about keeping pipelines *composable* and *testable*.

</div>
```

### 4.2 Slide Transitions

**Global transition (in headmatter):**
```yaml
transition: slide-left
```

**Per-slide override:**
```yaml
---
transition: fade
---
```

**Recommended Transitions:**
- **Default:** `slide-left` (forward momentum)
- **Section breaks (S02a, S13a):** `fade` (pause for emphasis)
- **Money slide (S05):** `slide-up` (building anticipation)
- **Final slide (S16):** `view-transition` (modern, polished)

### 4.3 Diagram Animations

**Progressive Mermaid reveals:**
```markdown
---
clicks: 3
---

# S04 – TypeRepr Recursion

<div v-click="1">

\`\`\`mermaid
flowchart TD
    Start[Type Inspection] --> CheckType{Type Kind?}
\`\`\`

</div>

<div v-click="2">

(Add more nodes with second click)

</div>
```

**OR use Mermaid's built-in styling:**
- Define nodes with classes
- Reveal via CSS transitions (advanced)

---

## Part 5: Presenter Mode Configuration

### 5.1 Activating Presenter Mode

**During Development:**
```bash
pnpm run dev
# Then navigate to http://localhost:3030/presenter
```

**During Presentation:**
- Press `P` key to open presenter view
- Or click presenter button in nav bar
- Displays on secondary screen/window

### 5.2 Presenter View Features

**Left Pane:** Current slide (synced with main display)
**Right Pane:** Next slide (preview)
**Bottom Pane:** Speaker notes (from HTML comments)
**Top Bar:**
- Timer (elapsed time)
- Current slide number / Total slides
- Navigation controls

### 5.3 Speaker Notes Format

**Best Practices for Our Talk:**
```markdown
---
layout: default
---

# S01 – Scars, Beliefs & Stakes

[Slide content here]

<!--
**Target end: 06:30**

**Deliver verbatim (60 seconds):**
"Three months ago, an upstream team renamed `amount` to `amt`..."

**[Pause. Beat. Let it land.]**

**Transition:** "By the end of this talk, you'll know how to move drift..."
-->
```

**Structure:**
1. **Timing target** (bold, first line)
2. **Delivery instructions** (bold, action-oriented)
3. **Verbatim quotes** (blockquote or italics)
4. **Transitions** (bold, last line)

### 5.4 Rehearsal Workflow

1. **Run presenter mode** on laptop screen
2. **Mirror main view** to external display (or use second window)
3. **Practice with timer** - target 35 min for S01a-S16
4. **Use navigation bar** to jump to specific slides during rehearsal
5. **Test all click animations** - ensure smooth flow

---

## Part 6: Styling & Theming Strategy

### 6.1 Theme Selection

**Recommended Themes (in order of preference):**

1. **`default`** - Clean, minimal, professional
   - ✅ Good code highlighting
   - ✅ Mermaid support out of box
   - ✅ Easy to customize
   - ❌ May feel generic

2. **`seriph`** - Modern, clean, tech-focused
   - ✅ Beautiful typography
   - ✅ Great for code-heavy talks
   - ✅ Professional appearance
   - ❌ Darker aesthetic (may need adjustments)

3. **`apple-basic`** - Apple Keynote style
   - ✅ Polished, conference-ready
   - ✅ Clean layouts
   - ❌ May be too minimal for diagrams

**Recommendation:** Start with `seriph`, fall back to `default` if customization needed.

### 6.2 Custom Styling (UnoCSS)

**Global styles (in `<style>` tag at end of slides.md):**
```vue
<style>
/* Mermaid diagrams - larger, centered */
.mermaid-large {
  @apply text-2xl;
}

.mermaid-large svg {
  max-width: 100% !important;
  max-height: 85vh !important;
  margin: 0 auto;
}

/* Code blocks - dark theme consistency */
.slidev-code {
  @apply bg-gray-900 text-gray-100 rounded-lg p-4;
}

/* Highlighted text */
.highlight-yellow {
  @apply bg-yellow-200 text-gray-900 px-2 py-1 rounded;
}

/* Money slide callout */
.money-slide {
  @apply border-4 border-green-500 p-6 rounded-xl;
}
</style>
```

### 6.3 Per-Slide Classes

**Add to frontmatter:**
```yaml
---
class: text-center mermaid-large
---
```

**OR inline with MDC syntax:**
```markdown
::div{.highlight-yellow}
This text will be highlighted
::
```

---

## Part 7: Export & Deployment

### 7.1 Export Formats

**PDF Export (for organizers):**
```bash
pnpm run export
# Generates slides-export.pdf
```

**PPTX Export (if needed):**
```bash
pnpm run export --format pptx
```

**PNG Export (individual slides):**
```bash
pnpm run export --format png
```

**Static HTML (for sharing):**
```bash
pnpm run build
# Generates dist/ folder - deploy to Netlify/Vercel
```

### 7.2 Export Configuration

**In headmatter:**
```yaml
export:
  format: pdf
  timeout: 30000  # 30s per slide (for heavy Mermaid diagrams)
  dark: true
  withClicks: false  # Export with all clicks expanded
  withToc: true
```

### 7.3 Deployment Options

**For Conference Organizers:**
- ✅ PDF export (most compatible)
- ✅ Static HTML build (upload to S3/Netlify)
- ❌ Live Slidev instance (requires Node.js runtime)

**For Personal Sharing:**
- ✅ Deploy to Vercel/Netlify (static HTML)
- ✅ GitHub Pages (static HTML)
- ✅ YouTube recording (use built-in recording feature)

---

## Part 8: Implementation Phases (Granular Steps)

### Phase 1: Project Setup (15 min)

**Step 1.1 - Install Slidev CLI**
```bash
cd /Users/vim/IdeaProjects/flowforge
pnpm create slidev flowforge-scalaio-2025
cd flowforge-scalaio-2025
```

**Step 1.2 - Verify Installation**
```bash
pnpm run dev
# Open http://localhost:3030
```

**Step 1.3 - Test Mermaid Support**
Create test slide in `slides.md`:
```markdown
---
# slide 1
---

\`\`\`mermaid
flowchart LR
  A[Test] --> B[Mermaid]
\`\`\`
```

**Step 1.4 - Test Code Highlighting**
```markdown
---
# slide 2
---

\`\`\`scala {1-3|5-7}
sealed trait SchemaPolicy
object SchemaPolicy {
  sealed trait Exact extends SchemaPolicy
}
\`\`\`
```

**Acceptance Criteria:**
- ✅ Slidev dev server runs
- ✅ Mermaid diagram renders
- ✅ Scala code highlights correctly
- ✅ Click animations work

---

### Phase 2: Headmatter Configuration (10 min)

**Step 2.1 - Configure Global Settings**
Replace first slide frontmatter with:
```yaml
---
theme: seriph
title: 'Compile-Time Contracts & Fiber-Safe Data Pipelines'
author: 'Vitthal Mirji'
keywords: 'Scala, Data Engineering, Compile-Time, Contracts, Fiber-Safe, FlowForge'
presenter: true
download: true
exportFilename: 'ScalaIO-2025-FlowForge-Talk'
record: dev
monaco: false
drawings:
  enabled: true
  persist: false
lineNumbers: true
colorSchema: 'dark'
fonts:
  sans: 'Inter'
  mono: 'Fira Code'
highlighter: shiki
aspectRatio: '16/9'
transition: slide-left
---
```

**Step 2.2 - Test Theme**
- Reload dev server
- Verify theme applies
- Test dark mode toggle (D key)

**Step 2.3 - Test Presenter Mode**
- Navigate to `/presenter`
- Verify dual-pane view
- Test speaker notes (add test comment to slide)

**Acceptance Criteria:**
- ✅ Theme loads correctly
- ✅ Fonts render (Inter, Fira Code)
- ✅ Presenter mode works
- ✅ Dark mode applies

---

### Phase 3: Title & Intro Slides (20 min)

**Step 3.1 - Title Slide**
```markdown
---
layout: cover
class: text-center
---

# Compile-Time Contracts & Fiber-Safe Data Pipelines

Let the compiler refuse broken migrations; let fibers keep retries safe.

<div class="pt-12">
  <span class="text-sm opacity-75">
    ScalaIO Paris 2025 | Vitthal Mirji | Staff Data Engineer @ Walmart
  </span>
</div>

<!--
Opening slide. Keep visible for 30 seconds before advancing.
-->
```

**Step 3.2 - S01a (About Me)**
```markdown
---
layout: default
---

# About Me

<v-clicks>

- Staff Data Engineer @ Walmart, Offices in India
- From Mumbai, India
- 10+ years building data platforms at scale (Deloitte → Walmart)
- I cook good Indian food 🍛
- Visit: https://vitthalmirji.com

</v-clicks>

<!--
**Target end: 00:45**
Personal touch: "I cook good Indian food" (humanizes, builds rapport)
Keep crisp—transition to Walmart context quickly
-->
```

**Step 3.3 - S01b (Walmart Context)**
```markdown
---
layout: default
---

# About Walmart (Context)

<v-clicks>

- Global retail scale: diverse sources, strict SLAs/compliance, mixed batch & streaming workloads
- Practical constraints: schema evolution under change, idempotency at edges, reproducible rollbacks
- These constraints motivated compile‑time guarantees and fiber‑safe execution

</v-clicks>

<!--
**Target end: 01:30**
Set the stage: global retail scale, strict SLAs, mixed batch/streaming
Emphasize constraints that motivated compile-time + fiber-safe approach
Transition: "Before diving into solutions, let's get everyone up to speed on data engineering reality."
-->
```

**Acceptance Criteria:**
- ✅ Title slide uses `cover` layout
- ✅ S01a bullets reveal progressively
- ✅ Speaker notes appear in presenter mode
- ✅ Timing aligns with targets

---

### Phase 4: Data Engineering Workflow Diagram (S01c) (30 min)

**Step 4.1 - Create Slide with Mermaid**
```markdown
---
layout: default
class: mermaid-large
clicks: 6
---

# Data Engineering Reality (Getting Up to Speed)

<div v-click="1">

\`\`\`mermaid
flowchart TB
    subgraph Modeling[Schema Design Phase]
        DM[Data Modelers & Stewards]
        PM[Product Managers & Stakeholders]
        DM <-->|Define schemas, ER models| PM
    end
\`\`\`

</div>

<div v-click="2">

(Add Collaboration subgraph)

</div>

<!-- Continue for all 6 phases -->

<!--
**Target end: 04:00**
**WHY THIS SLIDE:** Many Scala devs aren't full-time data engineers—this brings them up to speed
Walk through the diagram slowly... (copy from speaker notes)
-->
```

**Step 4.2 - Add Styled Highlights**
Add to diagram:
```mermaid
style Note1 fill:#fffacd,stroke:#f4a300
style Effects fill:#e6f3ff,stroke:#4a90e2
```

**Step 4.3 - Test Rendering**
- Verify all subgraphs render
- Test click-by-click reveal
- Ensure highlights apply

**Step 4.4 - Add Callout Bullets**
```markdown
<div v-click="7" class="mt-8">

**Where compile-time contracts & fiber-safe execution fit:**
<v-clicks>

- **Sources:** Compile-time prevents schema drift across many varied sources
- **Quality:** Both technical & business DQ rules validated at compile + runtime
- **Transform:** Pure functions for performance
- **Effects:** Side effects (logging, notifications, audit) kept at edges—fiber-safe
- **Sink:** Strong persistence guarantees with idempotent retries
- **CI/CD:** Compile-fail tests gate deployments

</v-clicks>

</div>
```

**Acceptance Criteria:**
- ✅ Mermaid diagram renders correctly
- ✅ All subgraphs visible
- ✅ Highlights apply (yellow, blue)
- ✅ Click animations work (6 clicks for diagram, then bullets)
- ✅ Full-width display (mermaid-large class)

---

### Phase 5: Scar Story (S01) (15 min)

**Step 5.1 - Create Quote Layout**
```markdown
---
layout: quote
class: text-xl leading-relaxed
---

# The Scar Story

> "Three months ago, an upstream team renamed `amount` to `amt` in a microservice we consume. No one told us. Our Spark job didn't crash—it just wrote `null` for every transaction amount. For three weeks.
>
> We only caught it during month‑end reconciliation when Finance couldn't close the books. We burned a weekend backfilling 47 million rows. I had to present a postmortem to leadership explaining why a column rename cost us significant engineer time and delayed financial reporting by 5 days.
>
> **If the build had failed on that schema diff, none of that would have happened.**"

<div v-click class="mt-8 text-2xl font-bold text-center">

[Pause. Beat. Let it land.]

</div>

<!--
**Target end: 06:30**
**Deliver verbatim (60 seconds)**
**[Pause. Beat. Let it land.]**
**Promise:** "By the end of this talk, you'll know how to move drift, effect leaks, and rollback risk to compile/build time—so you never present that postmortem."
-->
```

**Acceptance Criteria:**
- ✅ Quote layout applies
- ✅ Text is large, readable (text-xl)
- ✅ "Pause" appears on click
- ✅ Speaker notes include verbatim delivery instruction

---

### Phase 6: All Remaining Slides (Iterative)

**For each slide (S02 through S16):**

1. **Create slide section** in `slides.md`
2. **Add frontmatter** (layout, clicks, class)
3. **Port content** from `ScalaIO-2025-Main-Talk.md`
4. **Add click animations** where appropriate
5. **Embed Mermaid diagrams** (7 total across all slides)
6. **Add code blocks** with Scala syntax highlighting
7. **Add speaker notes** from `speaker-notes-ScalaIO-2025.md`
8. **Test slide** in dev server
9. **Refine styling** if needed
10. **Move to next slide**

**Priority Order:**
1. **S05 (Money Slide)** - Red→green demo with line highlighting
2. **S04** - TypeRepr + Evidence diagrams (complex)
3. **S13** - Architecture diagram (large)
4. **S02** - Decision matrix table (formatting)
5. **All others** - Standard layouts

---

### Phase 7: Code Highlighting & Animations (S05 Focus) (45 min)

**Step 7.1 - Create S05 Slide Structure**
```markdown
---
layout: default
class: money-slide
clicks: 3
---

# S05 – Red → Green Migration Demo

<div class="text-center text-3xl font-bold mb-4">
"This is where we get Friday night back."
</div>
```

**Step 7.2 - Setup Code Block**
```markdown
## Setup - Define types with drift

\`\`\`scala {all}{lines:true}
// Contract expects: id, email
case class Contract(id: Long, email: String)

// Producer has EXTRA field: age
case class Producer(
  id: Long,
  email: String,
  age: Int
)
\`\`\`
```

**Step 7.3 - RED: Exact Policy (Click 1)**
```markdown
<div v-click="1">

## ❌ RED: Exact policy REFUSES the drift

\`\`\`scala {1|3-7}{lines:true}
val _ = implicitly[SchemaConforms[Producer, Contract, SchemaPolicy.Exact]]

// Compiler error:
// [error] Compile-time contract drift (policy: SchemaPolicy.Exact)
// [error] Out: Producer vs Contract: Contract
// [error] Extra: age: Int
// [error] Missing: (none)
// [error] Mismatched: (none)
\`\`\`

<div class="text-center mt-4 text-red-500 font-bold">
Compiler refuses to proceed!
</div>

</div>
```

**Step 7.4 - GREEN: Backward Policy (Click 2)**
```markdown
<div v-click="2">

## ✅ GREEN: Backward policy ALLOWS the migration

\`\`\`scala {1}{lines:true}
val _ = implicitly[SchemaConforms[Producer, Contract, SchemaPolicy.Backward]]
// Compiles successfully! ✅
\`\`\`

</div>
```

**Step 7.5 - Key Takeaway (Click 3)**
```markdown
<div v-click="3" class="mt-8 p-4 bg-green-900 rounded-lg">

**Key takeaway:** The compiler pinpoints the exact field (`age: Int`) that drifted—no log grepping at 2 AM. You choose when to relax the policy for safe migrations.

</div>
```

**Step 7.6 - Add Speaker Notes**
```markdown
<!--
**Target end: 18:15**
**THIS IS THE MONEY SLIDE. "This is where we get Friday night back."**

Walk through code slowly:
1. Setup: "Contract expects id, email. Producer has an EXTRA field: age."
2. ❌ RED: "We attempt Exact policy. Watch what happens."
   - Show compiler error on screen
   - Keep error visible for 10 seconds—let audience read it
   - Point at the error: "Extra: age: Int. Compiler refuses to proceed."
3. ✅ GREEN: "Now we relax to Backward policy."
   - Show code: SchemaPolicy.Backward
   - Show success message: "Compiles successfully! ✅"

Transition: "Let's see what you get when you scaffold a new project."
-->
```

**Acceptance Criteria:**
- ✅ Three click animations work smoothly
- ✅ Code highlights correctly (Scala syntax)
- ✅ Line numbers appear
- ✅ Line highlighting works (`{1|3-7}`)
- ✅ Green/red styling applies
- ✅ Speaker notes guide delivery

---

### Phase 8: Mermaid Diagram Optimization (All 7 Diagrams) (60 min)

**For each Mermaid diagram:**

1. **Copy from `ScalaIO-2025-Main-Talk.md`**
2. **Test rendering** in Slidev
3. **Fix bracket escaping** if needed (use `#91;` `#93;`)
4. **Add custom styling** (fill colors, stroke)
5. **Adjust size** (use `mermaid-large` class or custom CSS)
6. **Test click animations** for progressive reveals
7. **Add speaker notes** for diagram walkthrough

**Specific Diagram Fixes:**

**S04 - Evidence Flow Diagram:**
- Bracket escaping already applied: `TypedSource#91;Out#93;`
- Test rendering
- If still breaks, try: `"TypedSource[Out]"` (quoted node labels)

**S01c - DE Workflow:**
- Large diagram (6 subgraphs)
- Use progressive reveal (6 clicks)
- Highlight boxes: `style Note1 fill:#fffacd`

**S13 - Architecture:**
- Layered flowchart
- Full-screen display
- Pause on slide for 15 seconds (per speaker notes)

---

### Phase 9: Speaker Notes Transfer (90 min)

**For each slide:**

1. **Open `speaker-notes-ScalaIO-2025.md`**
2. **Find corresponding slide section** (e.g., S01, S02, etc.)
3. **Copy speaker notes** (everything from "Target end" to "Transition")
4. **Paste into HTML comment** at end of slide
5. **Format for readability** (bold targets, italics for quotes)
6. **Test in presenter mode** - verify notes appear

**Example Transfer:**

**From `speaker-notes-ScalaIO-2025.md`:**
```markdown
### S02 – Boundaries We Must Name
<span style="color:#777;font-size:12px">Target end: 08:30</span>
- **Show decision matrix table**—let audience absorb it for 10 seconds
- **Read key rows aloud:**
  - "Schema drift within your repo? ✅ Compile-time macro evidence..."
...
```

**To `slides.md`:**
```markdown
---
# S02 slide
---

# Slide content here

<!--
**Target end: 08:30**

Show decision matrix table—let audience absorb it for 10 seconds

Read key rows aloud:
- "Schema drift within your repo? ✅ Compile-time macro evidence. ❌ Runtime is too late."
- "Corrupt file (missing rows)? ❌ Compile-time can't help. ✅ Runtime DQ checks."

Emphasize: "Compiler buys sleep; runtime guardrails deal with the world's messiness."

Transition: "Keep these boundaries in mind—every section will point back here."
-->
```

**Acceptance Criteria for ALL slides:**
- ✅ Speaker notes transferred for all 20+ slides
- ✅ Timing targets included
- ✅ Delivery instructions clear
- ✅ Transitions included
- ✅ Notes appear correctly in presenter mode

---

### Phase 10: Testing & Refinement (120 min)

**Step 10.1 - Full Walkthrough (60 min)**
- Open presenter mode
- Navigate slide-by-slide
- Test all click animations
- Verify all Mermaid diagrams render
- Check all code highlighting
- Read all speaker notes
- Time each section (target 35 min total)

**Step 10.2 - Identify Issues**
Create checklist:
- [ ] Slides with rendering issues
- [ ] Mermaid diagrams not displaying
- [ ] Code blocks with syntax errors
- [ ] Click animations not working
- [ ] Speaker notes missing/incorrect
- [ ] Timing off (too fast/slow)

**Step 10.3 - Fix Issues Iteratively**
For each issue:
1. Document the problem
2. Research Slidev docs for solution
3. Apply fix
4. Test fix
5. Move to next issue

**Step 10.4 - Styling Refinement (30 min)**
- Adjust font sizes for readability
- Ensure diagrams fit on screen
- Fix any color contrast issues
- Add custom CSS where needed
- Test dark mode consistency

**Step 10.5 - Export Test (30 min)**
- Export to PDF
- Export to PNG (sample slides)
- Build static HTML
- Test on different browsers
- Verify all assets load correctly

---

### Phase 11: Final Polish & Rehearsal (60 min)

**Step 11.1 - Add Custom Styling**
```vue
<style>
/* Custom styles at end of slides.md */

/* Mermaid diagrams */
.mermaid-large svg {
  max-height: 85vh !important;
  margin: 0 auto;
}

/* Money slide border */
.money-slide {
  border: 4px solid #10b981;
  border-radius: 1rem;
  padding: 1.5rem;
}

/* Highlighted text */
.highlight {
  background: #fbbf24;
  color: #1f2937;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
}

/* Code blocks */
.slidev-code {
  font-size: 1.1rem !important;
  line-height: 1.6 !important;
}
</style>
```

**Step 11.2 - Add Logo/Branding (Optional)**
- Add FlowForge logo to `public/` folder
- Reference in slides: `![FlowForge](/logo.svg)`
- Or add to cover slide

**Step 11.3 - Full Rehearsal**
- Run through entire presentation
- Practice with timer
- Use presenter mode
- Simulate Q&A
- Record timing for each section
- Identify slides to condense if running over

**Step 11.4 - Create Backup Slides (Optional)**
- Export key slides as PNG
- Have PDF backup on USB drive
- Screenshot Mermaid diagrams (in case rendering fails)

---

## Part 9: Risk Mitigation & Contingencies

### 9.1 Common Issues & Solutions

**Issue 1: Mermaid Diagrams Don't Render**
- **Solution 1:** Use PNG exports as `<img>` tags
- **Solution 2:** Simplify diagram syntax
- **Solution 3:** Increase export timeout in config

**Issue 2: Code Highlighting Breaks**
- **Solution 1:** Verify language identifier (`scala` not `Scala`)
- **Solution 2:** Check for unescaped characters in code
- **Solution 3:** Use `text` language as fallback

**Issue 3: Click Animations Out of Order**
- **Solution 1:** Use absolute click numbers (`v-click="3"`)
- **Solution 2:** Review `clicks:` frontmatter count
- **Solution 3:** Use `v-after` for synchronization

**Issue 4: Speaker Notes Don't Show**
- **Solution 1:** Verify HTML comment syntax (`<!-- -->`)
- **Solution 2:** Ensure comments are at END of slide
- **Solution 3:** Check presenter mode is enabled in config

**Issue 5: Export Fails**
- **Solution 1:** Increase timeout: `export.timeout: 60000`
- **Solution 2:** Export with `--dark false` if dark mode causes issues
- **Solution 3:** Export slides individually (split into multiple files)

### 9.2 Fallback Plan

**If Slidev completely fails:**
1. **Export to PDF** (most compatible)
2. **Use Google Slides** (import PDF, add speaker notes manually)
3. **Use Keynote** (import PDF, add animations manually)
4. **Use reveal.js** (simpler Markdown presentation framework)

**If Mermaid fails:**
1. Export diagrams as SVG/PNG from Mermaid Live Editor
2. Embed as images in slides
3. Loses interactivity but ensures rendering

---

## Part 10: Success Criteria & Quality Gates

### 10.1 Must-Have Features

- ✅ All 7 Mermaid diagrams render correctly
- ✅ All code blocks have Scala syntax highlighting
- ✅ Presenter mode works with speaker notes
- ✅ All click animations work smoothly
- ✅ S05 (Money Slide) has line-by-line highlighting
- ✅ Timing targets met (35 min for S01a-S16)
- ✅ PDF export succeeds
- ✅ Dark theme applies consistently

### 10.2 Nice-to-Have Features

- ⭐ Custom theme (beyond default/seriph)
- ⭐ Logo/branding on slides
- ⭐ Smooth transitions between slides
- ⭐ Interactive elements (draggable arrows)
- ⭐ Recording for practice runs
- ⭐ Static HTML deployment for sharing

### 10.3 Quality Checklist

**Before Final Export:**
- [ ] Spell-check all slide content
- [ ] Verify all speaker notes transferred
- [ ] Test all keyboard shortcuts
- [ ] Test presenter mode on external display
- [ ] Verify all URLs are clickable
- [ ] Check code indentation consistency
- [ ] Ensure all diagrams fit on screen
- [ ] Test PDF export renders correctly
- [ ] Verify timing with full rehearsal
- [ ] Have PNG backups for critical slides

---

## Part 11: Post-Implementation Tasks

### 11.1 Documentation

**Create `README.md` in slidev project:**
```markdown
# ScalaIO 2025 - Slidev Presentation

## Running Locally
\`\`\`bash
pnpm install
pnpm run dev
\`\`\`

## Presenter Mode
Navigate to http://localhost:3030/presenter

## Export
\`\`\`bash
pnpm run export        # PDF
pnpm run build         # Static HTML
\`\`\`

## Shortcuts
- P: Presenter mode
- O: Overview
- D: Dark mode
- F: Fullscreen
```

### 11.2 Version Control

**Commit structure:**
```bash
git add slides.md package.json
git commit -m "feat: initial Slidev implementation with all 20 slides"

git add public/
git commit -m "assets: add logos and diagrams"

git add vite.config.ts
git commit -m "config: custom theme and export settings"
```

### 11.3 Sharing & Distribution

**For Conference:**
- PDF export (upload to organizers)
- Static HTML build (optional web link)

**For Team:**
- Git repository (share repo link)
- Deployed site (Netlify/Vercel)
- Recording (YouTube/internal)

---

## Part 12: Timeline Estimate

| Phase | Task | Estimated Time | Dependencies |
|-------|------|----------------|--------------|
| 1 | Project Setup | 15 min | Node.js, pnpm |
| 2 | Headmatter Config | 10 min | Phase 1 |
| 3 | Title & Intro Slides | 20 min | Phase 2 |
| 4 | S01c (DE Workflow) | 30 min | Phase 3 |
| 5 | S01 (Scar Story) | 15 min | Phase 4 |
| 6 | All Remaining Slides | 180 min | Phase 5 |
| 7 | S05 Code Highlighting | 45 min | Phase 6 |
| 8 | Mermaid Optimization | 60 min | Phase 6 |
| 9 | Speaker Notes Transfer | 90 min | Phase 6 |
| 10 | Testing & Refinement | 120 min | Phases 1-9 |
| 11 | Final Polish | 60 min | Phase 10 |
| **TOTAL** | **Full Implementation** | **~10.5 hours** | |

**Realistic Timeline:**
- **Day 1 (3 hours):** Phases 1-5 (setup + initial slides)
- **Day 2 (4 hours):** Phases 6-7 (all slides + S05 focus)
- **Day 3 (3.5 hours):** Phases 8-11 (diagrams + polish + rehearsal)

---

## Part 13: Next Steps (Immediate Actions)

### Ready to Start? Here's What to Do:

1. **Install Slidev:**
   ```bash
   cd /Users/vim/IdeaProjects/flowforge
   pnpm create slidev flowforge-scalaio-2025
   ```

2. **Verify Installation:**
   ```bash
   cd flowforge-scalaio-2025
   pnpm run dev
   ```

3. **Start with Headmatter** (copy from Phase 2)

4. **Build Title Slide** (copy from Phase 3)

5. **Test Mermaid** (S01c diagram from Phase 4)

6. **Iterate** through remaining phases

---

## Conclusion

This implementation plan provides:
- ✅ **100% feature coverage** of Slidev capabilities mapped to our content
- ✅ **Granular step-by-step** instructions for each slide
- ✅ **Risk mitigation** for common issues
- ✅ **Quality gates** to ensure professional delivery
- ✅ **Realistic timeline** (~10.5 hours total)
- ✅ **Fallback plans** if issues arise

**Key Success Factors:**
1. **Mermaid diagrams** work natively (tested in setup)
2. **Scala syntax highlighting** works via Shiki
3. **Presenter mode** provides speaker notes on second screen
4. **Click animations** enable progressive disclosure
5. **Export to PDF** ensures compatibility

**Recommendation:** Start with Phase 1-5 (setup + first 5 slides) to validate the approach, then proceed with full implementation.

---

_Last updated: 2025-10-17_
_Implementation ready: Yes_
_Estimated completion: 3 days (10.5 hours total)_
