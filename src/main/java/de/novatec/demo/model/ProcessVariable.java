package de.novatec.demo.model;

import lombok.Data;
import lombok.NonNull;

@Data
public class ProcessVariable {
	@NonNull
	private String name;
	@NonNull
	private VariableType type;
}
