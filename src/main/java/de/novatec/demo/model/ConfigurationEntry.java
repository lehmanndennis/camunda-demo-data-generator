package de.novatec.demo.model;

import java.util.ArrayList;
import java.util.List;

import de.novatec.demo.util.Helper;
import lombok.Data;

@Data
public class ConfigurationEntry implements IProbability {
	private String elementId;
	private double sd = Helper.DEFAULT.getStandardDeviation();
	private double mean = Helper.DEFAULT.getMean();
	private int probability;
	private List<ProcessVariable> processVariables = new ArrayList<>();
}
