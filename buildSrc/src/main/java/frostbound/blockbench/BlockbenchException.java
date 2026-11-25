package frostbound.blockbench;

import java.util.List;

/** Everything wrong with the Blockbench projects, one line per problem, each naming the file and what to do. */
public class BlockbenchException extends RuntimeException {
	private final List<String> problems;

	public BlockbenchException(List<String> problems) {
		super("Blockbench import failed:\n  - " + String.join("\n  - ", problems));
		this.problems = List.copyOf(problems);
	}

	public List<String> problems() {
		return problems;
	}
}
