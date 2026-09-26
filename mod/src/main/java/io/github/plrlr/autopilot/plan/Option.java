package io.github.plrlr.autopilot.plan;

/** One thing the tactician may choose: a skill, its argument, and a one-line reason shown to the brain. */
public record Option(String skill, String arg, String why) {
	public String label() {
		return arg == null || arg.isEmpty() ? skill : skill + " " + arg;
	}
}
