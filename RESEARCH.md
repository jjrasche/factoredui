# Research: Autonomous UI Optimization Landscape (April 2026)

## Academic foundations

### MAPE-K loop (IBM, 2001)
Monitor, Analyze, Plan, Execute, Knowledge. Designed for infrastructure self-management. This concept applies MAPE-K to componentized UI with LLM-powered analysis and democratic governance as the execute gate. MAPE-K has never been applied to frontend UI in the literature.

### Multi-armed bandits for UI
- CHI 2016: "Interface Design Optimization as a Multi-Armed Bandit Problem" -- bandit algorithms for data-driven design, but human participation essential for target metrics
- SIGIR 2022 (Booking.com): "Scalable UI Optimization Using Combinatorial Bandits" -- widgets as bandit arms, optimizes layout. Closest industry-backed academic work. But widgets are arrangement units, not instrumented observation units.
- EICS 2024: MARLUI -- multi-agent RL where interface agent and user agent co-evolve. Closest to LLM-as-hypothesis-generator but uses RL, not LLMs.

### Computational UI design (Aalto/Oulasvirta)
Predictive models of human perception + combinatorial optimization to generate/adapt GUIs. Integer programming for layout. Rigorous but uses cognitive models, not real-time behavioral data + LLMs.

## Industry: experimentation platforms

| Platform | Scale | Autonomy level |
|----------|-------|---------------|
| Netflix | ~10K exp/year | Semi-auto: humans design, platform deploys/analyzes |
| Booking.com | 1000+ concurrent | Highest: widget optimization via bandits runs autonomously |
| Spotify Confidence | ~10K exp/year | Automated analysis, human-designed experiments |
| Statsig | 1T+ daily events | Copilot: AI assists, humans decide |
| LaunchDarkly | Enterprise | Semi-auto: auto-rollback on regression (Guarded Rollouts) |
| Eppo (now Datadog) | Warehouse-native | Analysis-focused, human-driven |

None generate hypotheses or create experiments autonomously. All automate execution and measurement.

## Industry: AI CRO platforms

| Platform | Innovation | Gap |
|----------|-----------|-----|
| Evolv AI | Active learning + MAB. Continuously evolves experiences. Auto-recommends ideas. | No component vocabulary. No factor engine. No governance. |
| Kameleoon PBX | AI scans for friction, generates hypotheses, creates test-ready code (React aware). **Closest to LLM inference step.** | Reads DOM, not standardized factors. Human approval required. |
| Optimizely Opal | Ideation Agent, Variation Agent, Summary Agent. 78.7% more experiments. | Agents assist, don't run autonomously. |
| Webflow Optimize | ML-driven autonomous variant testing per segment. | Page-level, not component-level. No hypothesis generation. |
| AB Tasty Evi | Evidence-based hypothesis scoring. | Explicitly anti-autonomy. Human at every step. |

## Industry: behavioral analytics

| Platform | Innovation |
|----------|-----------|
| Contentsquare | AI Frustration Score (0-100) from rage clicks, dead zones, errors. **Closest to factor engine.** But single composite score, not multi-tier. |
| Quantum Metric Felix | Agentic AI analyst. Autonomously monitors, analyzes, alerts. Cross-session cross-KPI insight. |
| FullStory | Session replay + behavioral signals (rage clicks, dead clicks, backtracking). |
| Heap Illuminate | Auto-surfaces top events influencing conversion. |

## Autonomous optimization loops

### Karpathy autoresearch (March 2026)
630-line Python script. Infinite hill-climbing: modify code, run experiment, measure metric, keep or revert. LLM as mutation function. 12 experiments/hour. Shopify CEO found 53% speedup on storefront rendering.

Gap: single file, single metric, no factor engine, no component vocabulary, no governance.

### Datadog BitsEvolve
Full autonomous loop with formal verification. LLM generates variants, Verus proofs establish safety, shadow eval against real traffic, live hot-swap. 270-541% performance improvements.

Prerequisites: bounded complexity, deterministic baseline, formal specs, isolation mechanism. "Full autonomy is possible when every property is machine-checkable."

## Factor frameworks

| Framework | Dimensions | Origin |
|-----------|-----------|--------|
| Google HEART | Happiness, Engagement, Adoption, Retention, Task success | Google UX Research 2010 |
| PULSE | Page views, Uptime, Latency, Seven-day active, Earnings | Google (pre-HEART) |
| Core Web Vitals | LCP, INP, CLS | Google Chrome |
| Lighthouse | Performance, Accessibility, Best Practices, SEO, PWA | Google Chrome |

The three-tier model (alarm/diagnostic/structural) does not exist as a named framework. The term "factor engine" does not exist in industry or academic literature.

## Server-driven UI + experimentation

Airbnb Ghost Platform: universal schema of sections/layouts/actions. A/B experiments by changing one backend source of truth. Lyft: experiment deployment from 2+ weeks to 1-2 days with SDUI. Closest architecture to "component as observation+change unit" but doesn't instrument at component level.

## Democratic governance

Platform cooperativism (Scholz, Schneider): democratic governance of digital platforms. CoopCycle uses sociocratic consent. Civic AI: citizen juries building algorithms for political decisions.

No existing system combines democratic governance with automated experimentation.

## Terminology

Established: DXO, CRO, PLG, growth engineering, experimentation engineering, continuous experimentation, feature management, progressive delivery, SDUI, design tokens, MAB, contextual bandits, AIOps, self-healing applications, observability-driven development.

Academic: MAPE-K, autonomic computing, self-adaptive systems, computational interaction, combinatorial UI optimization, CBUT.

Emerging: autoresearch pattern, prompt-based experimentation (PBX), agentic AI experimentation, harness-first engineering.

## Novelty assessment

### Already exists
- Componentized UI with standardized vocabulary (shadcn, Polaris, Ghost)
- SDUI for component variant swapping (Airbnb, Lyft, Netflix)
- Behavioral signal detection (Contentsquare, FullStory, Quantum Metric)
- Composite friction scoring (Contentsquare)
- Structural factor auditing (Lighthouse, Web Vitals)
- UX metric frameworks (HEART, PULSE)
- MAB layout optimization (Booking.com, Evolv AI)
- LLM-generated experiment variants (Kameleoon, Optimizely)
- Autonomous code optimization loops (autoresearch, BitsEvolve)
- Feature flags at component level (LaunchDarkly, Statsig)
- Auto-rollback on regression (LaunchDarkly, Split.io)
- Democratic platform governance models (platform cooperativism)

### Novel combinations (not yet integrated)
1. Component = observation unit = change unit (no translation layer)
2. Multi-tier factor engine unifying alarm + diagnostic + structural
3. LLM reads standardized factors, generates component-level hypotheses AND experiment code
4. Democratic governance as shipping gate for autonomous experiments
5. The full integrated loop: components -> factors -> LLM inference -> experimentation -> democratic governance
