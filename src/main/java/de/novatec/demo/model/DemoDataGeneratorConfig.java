package de.novatec.demo.model;

import lombok.*;

import java.util.Date;
import java.util.Map;

@Builder
@Data
public class DemoDataGeneratorConfig {
	@NonNull
	private String processDefinitionKey;
	private int procDefVersion;
	@NonNull
	private Date startDate;
	private int numberOfInstances;
	private double meanBetweenStarts;
	private double sdBetweenStarts;
	private boolean useWorkdaysForUsertasks;
	private boolean useWorkdaysForNewProcesses;
	@NonNull
	private Map<String, ConfigurationEntry> elementConfigurations;
}