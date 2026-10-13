/**
 * Reports this Pi session's execution state to the Pi Launcher JetBrains plugin.
 *
 * The plugin starts Pi with `--extension <this file>` and watches the `status` file
 * written next to it, which is how the IDE learns what Pi is doing. Pi cannot tell
 * a plugin directly: its own Program Status Protocol is an OSC escape sequence that
 * the IDE terminal drops (`docs/adr/0004`).
 *
 * Design rules, in order of importance:
 *
 * 1. Never break the session. Every write is wrapped, because a status file is a
 *    nice-to-have and a crashed extension is not.
 * 2. Never lie. Only report a state Pi actually announced; a missing file means
 *    "unknown" to the plugin, and that is a better answer than a guess.
 * 3. Keep it dependency-free, so it loads anywhere Pi runs.
 */
import { mkdirSync, renameSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

type State = "idle" | "working" | "blocked" | "done" | "error";

/** Where the plugin reads this session's state. */
const STATUS_FILE =
  process.env.PI_LAUNCHER_STATUS_FILE ?? join(__dirname, "status");

function firstLine(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  const line = value.split("\n", 1)[0]?.trim();
  return line ? line.slice(0, 200) : undefined;
}

/**
 * What a tool call is doing, in one glanceable line: the tool and the one argument
 * that identifies its subject. Bounded, because this ends up in a status bar tooltip.
 */
function toolActivity(toolName: unknown, args: unknown): string {
  const name = typeof toolName === "string" ? toolName : "tool";
  const values = (args ?? {}) as Record<string, unknown>;
  const subject = [values.command, values.filePath, values.path, values.pattern, values.query, values.url]
    .find((value) => typeof value === "string" && value.trim().length > 0) as string | undefined;
  const head = firstLine(subject);
  return head ? `${name}: ${head.slice(0, 120)}` : name;
}

/**
 * Writes the report atomically: the plugin polls this file, and half a report
 * would read as a state that was never announced.
 */
function report(state: State, kind?: string, message?: string, activity?: string): void {
  const lines = [`state=${state}`];
  if (kind) lines.push(`kind=${kind}`);
  if (message) {
    lines.push(`message=${Buffer.from(message, "utf8").toString("base64")}`);
  }
  if (activity) {
    lines.push(`activity=${Buffer.from(activity, "utf8").toString("base64")}`);
  }

  try {
    const temp = `${STATUS_FILE}.tmp`;
    mkdirSync(dirname(STATUS_FILE), { recursive: true });
    writeFileSync(temp, `${lines.join("\n")}\n`);
    renameSync(temp, STATUS_FILE);
  } catch {
    // Best effort only - the plugin falls back to its own inference.
  }
}

function clear(): void {
  try {
    unlinkSync(STATUS_FILE);
  } catch {
    // Already gone, or never written.
  }
}

export default function (pi: any): void {
  pi.on("session_start", () => report("idle"));

  // Pi counts as busy from the moment a run starts, before the model is called.
  pi.on("agent_start", () => report("working"));

  // A blocking dialog (a permission confirm, a question) is the one state a user
  // must see, and the one the IDE cannot infer.
  pi.on("ui_prompt_start", (event: any) => report("blocked", event?.kind, event?.title));
  pi.on("ui_prompt_end", () => report("working"));

  pi.on("message_end", (event: any) => {
    const message = event?.message;
    if (message?.stopReason === "error") {
      report("error", undefined, firstLine(message.errorMessage) ?? "Pi reported an error");
    }
  });

  // Tool calls are what a reader wants to see while a run is going on, and the
  // detail is cleared again the moment the run ends.
  pi.on("tool_execution_start", (event: any) =>
    report("working", undefined, undefined, toolActivity(event?.toolName, event?.args))
  );
  pi.on("tool_execution_end", (event: any) => {
    const name = typeof event?.toolName === "string" ? event.toolName : "tool";
    report("working", undefined, undefined, `${name} · ${event?.isError ? "failed" : "done"}`);
  });

  // The settled signal is Pi's own "no more automatic work" boundary: retries,
  // compaction and steering are all over by then.
  pi.on("agent_settled", (event: any) => report(event?.aborted ? "idle" : "done"));

  pi.on("session_shutdown", () => clear());
}
