# Lane: the flow crawler. Every action, every branch, a grade, no LLM (2026-09-02)

Branch `build/flow-crawler` off the default branch, worktree `.git-worktrees/lane-flow-crawler`.
Jim's ask, verbatim: "regression testing that moves through EVERY possible action on a page and branches out from there, and grades each flow. I don't want an LLM to do that; build it into factored-ui's accompanying skill."

## BDD

**Given** a spec and a host action map (action name to a handler that mutates the store or returns the next spec),
**when** the crawler runs,
**then** it enumerates every node with a non-null action, fires each in a fresh render, follows the resulting state, and repeats breadth-first with dedup by canonical spec-plus-store hash and a depth limit,
**and** every flow (a path of actions) receives a numeric grade from deterministic checks only,
**and** the run emits a report (JSON plus markdown) listing flows worst-first, with a screenshot per step.

## Checks (authored by the lead)

1. `actionable_nodes_lists_exactly_the_nodes_whose_action_is_not_null`
2. `firing_every_action_from_a_two_button_spec_yields_two_flows_of_depth_one`
3. `a_flow_that_returns_to_a_seen_state_is_cut_and_marked_as_a_cycle`
4. `an_action_with_no_registered_handler_grades_as_a_dead_action`
5. `an_action_that_leaves_spec_and_store_unchanged_grades_as_a_no_op`
6. `a_tap_target_smaller_than_48dp_costs_the_flow_points`
7. `overlapping_visible_nodes_cost_the_flow_points`
8. `a_node_rendered_outside_the_viewport_costs_the_flow_points`
9. `a_state_with_no_action_leading_anywhere_new_is_a_dead_end_unless_it_is_the_root`
10. `the_report_orders_flows_worst_first_and_carries_a_png_per_step`
11. `the_gradle_task_flowCrawl_runs_the_crawler_on_a_spec_file_and_an_action_map_class_name`

## Grade

Start at 100 per flow. Deductions are named constants in one file, one line each, the check name beside each. Page score is the minimum over flows, with the mean beside it. No LLM anywhere in the crawler; the judge-model tier in ux-optimizer stays a separate, later step.

## Build on what exists (read first)

`packages/kotlin-compose/src/desktopTest/.../testing/SpecVisualCheck.kt` (shadowTree, tap, drag, binding); `RenderContext.dispatch`; `renderSpecToPng` and `RenderSpecCli` plus the `renderSpecCli` gradle task pattern in `packages/kotlin-compose/build.gradle.kts`. The crawler must run outside ComposeUiTest (a standalone main and a gradle task), so lift what SpecVisualCheck needs into desktopMain rather than duplicating it.

## Skill

Add a section "Flow crawl (no LLM)" to `~/.claude/skills/ux-optimizer/SKILL.md`: trigger phrases ("crawl the screen", "regression the flows", "grade every flow"), the exact gradle command, how to read the report, and when to escalate to the judge tier. Two examples at the bottom (input to output). Follow `~/.claude/references/skill-template.md`.

## Standards

`~/.claude/references/coding-standards.md` and `kotlin.md` apply. Names reveal intent; one verb per function; comments are a budget. Pin any new dependency. Test-first. No AI attribution trailer in commits. Do not merge. Do not push.

## Report (mandatory shape)

diff --stat · verbatim `./gradlew :kotlin-compose:desktopTest` output · the crawler's report on the genesis phone spec if reachable (Screen.kt in agent-platform-genesis produces it; otherwise a fixture spec) · CONTRADICTIONS · QUESTIONS.
