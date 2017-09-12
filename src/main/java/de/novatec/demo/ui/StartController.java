package de.novatec.demo.ui;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;

import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;

import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.primefaces.context.RequestContext;

import de.novatec.demo.DemoDataGenerator;
import de.novatec.demo.model.ConfigurationEntry;
import de.novatec.demo.model.DemoDataGeneratorConfig;
import de.novatec.demo.model.ElementType;
import de.novatec.demo.model.ProcessVariable;
import de.novatec.demo.model.VariableType;
import de.novatec.demo.util.Helper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

/**
 * Contains everything for the UI.
 */
@Named
@SessionScoped
@Log
public class StartController implements Serializable {

	private static final long serialVersionUID = 1L;
	// Max number of Instances to avoid a timeout
	public static final int MAX_NUMBER_OF_INSTANCE = 5000;
	@Inject
	private DemoDataGenerator dataGenerator;

	@Getter
	@Setter
	private String processDefinitionKey;
	@Getter
	@Setter
	private Integer procDefVersion;
	@Getter
	@Setter
	private int numberOfInstances = 10;
	@Getter
	@Setter
	private double meanBetweenStarts = Helper.DISTRIBUTION_DEFAULT.getMean();
	@Getter
	@Setter
	private double sdBetweenStarts = Helper.DISTRIBUTION_DEFAULT.getStandardDeviation();
	@Getter
	@Setter
	private Date startDate = new Date();
	@Getter
	@Setter
	private boolean useWorkdaysForUsertasks = true;
	@Getter
	@Setter
	private boolean useWorkdaysForNewProcesses;

	@Getter
	@Setter
	private String elementId;
	@Getter
	@Setter
	private ElementType elementType;
	@Getter
	@Setter
	private double sdElement;
	@Getter
	@Setter
	private double meanElement;
	@Getter
	@Setter
	private int elementProbability;
	@Getter
	private List<ProcessVariable> processVariables = new ArrayList<>();

	private Map<String, ConfigurationEntry> elementConfig = new HashMap<>();

	/**
	 * Starts a new history data generation.
	 */
	public void start() {
		if (numberOfInstances > MAX_NUMBER_OF_INSTANCE) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
					"max instances exceeded", "Max number of instances shall not exceed " + MAX_NUMBER_OF_INSTANCE));
			return;
		}
		// save the currently entered SD and Mean Time for the selected element
		saveSdMean();
		DemoDataGeneratorConfig config = DemoDataGeneratorConfig.builder()
				// general
				.processDefinitionKey(processDefinitionKey) //
				.procDefVersion(procDefVersion).startDate(startDate) //
				.numberOfInstances(numberOfInstances) //
				.elementConfigurations(elementConfig) //
				.useWorkdaysForNewProcesses(useWorkdaysForNewProcesses)//
				.useWorkdaysForUsertasks(useWorkdaysForUsertasks)
				// start time
				.meanBetweenStarts(meanBetweenStarts) //
				.sdBetweenStarts(sdBetweenStarts)//
				.build();

		FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Data generation started",
				"Data generation for: " + processDefinitionKey + " started"));
		log.log(Level.FINEST,
				"started: " + processDefinitionKey + ", at: " + SimpleDateFormat.getDateInstance().format(startDate));
		// call async. Seems not to work properly
		Future<Exception> exceptionFuture = dataGenerator.startDataGeneration(config);
		try {
			Exception exception = exceptionFuture.get();
			if (exception == null) {
				FacesContext.getCurrentInstance().addMessage(null,
						new FacesMessage("successfully created " + config.getNumberOfInstances() + " instances."));
			} else {
				FacesContext.getCurrentInstance().addMessage(null,
						new FacesMessage("An Exception occurred while generating Data. Please see log for further "
								+ "information.\n" + exception.getMessage()));
			}
		} catch (InterruptedException | ExecutionException e) {
			log.log(Level.WARNING, "An exception occurred while asynchronous execution.", e);
		}
	}

	public List<ProcessDefinition> getProcessDefinitions() {
		return Collections.unmodifiableList(dataGenerator.getProcessDefinitionList());
	}

	public List<ProcessDefinition> getVersions() {
		log.log(Level.FINEST, "called getVersions for: " + processDefinitionKey);
		if (processDefinitionKey == null) {
			procDefVersion = null;
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(dataGenerator.getProcessVersions(processDefinitionKey));
	}

	public String getBpmnForSelectedProcessAndVersion() {
		if (processDefinitionKey != null && procDefVersion != null && procDefVersion > 0) {
			return dataGenerator.getProcessXml(processDefinitionKey, procDefVersion);
		} else {
			return "";
		}
	}

	public void reset() {
		procDefVersion = null;
		RequestContext.getCurrentInstance().execute("viewer.clear();");
	}

	public void updateFields() {
		if (elementId != null) {
			saveSdMean();
		}
		String elementId = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap()
				.get("element");
		ConfigurationEntry configurationEntry = elementConfig.get(elementId);
		if (configurationEntry == null) {
			configurationEntry = new ConfigurationEntry();
			configurationEntry.setElementId(elementId);
			elementConfig.put(elementId, configurationEntry);
		}
		this.elementId = elementId;
		this.elementType = dataGenerator.getElementType(processDefinitionKey, procDefVersion, elementId);
		this.meanElement = configurationEntry.getMean();
		this.sdElement = configurationEntry.getSd();
		this.elementProbability = configurationEntry.getProbability();
		this.processVariables = configurationEntry.getProcessVariables();
	}

	public void addProcessVariable() {
		this.processVariables.add(new ProcessVariable("Example Variable Name", VariableType.STRING));
	}

	public void removeProcessVariable(ProcessVariable variable) {
		this.processVariables.remove(variable);
	}

	public VariableType[] getProcessVariableTypes() {
		return VariableType.values();
	}

	private void saveSdMean() {
		ConfigurationEntry configurationEntry = elementConfig.get(elementId);
		if (configurationEntry != null) {
			configurationEntry.setMean(meanElement);
			configurationEntry.setSd(sdElement);
			configurationEntry.setProbability(elementProbability);
			configurationEntry.setProcessVariables(processVariables);
		}
	}

	public boolean isSequenceFlow() {
		return elementType == ElementType.FLOW;
	}

	public boolean isTask() {
		return elementType == ElementType.TASK;
	}

	public boolean isBoundaryEvent() {
		return elementType == ElementType.BOUNDARY;
	}

	public boolean isStartEvent() {
		return elementType == ElementType.START_EVENT;
	}

	public boolean isEndEvent() {
		return elementType == ElementType.END_EVENT;
	}

	/**
	 * loads a bpmn diagram in the UI.
	 * <p/>
	 * hint: therefore it is necessary to replace linebreaks.
	 */
	public String getBpmnDiagramAsJS() {
		log.log(Level.FINEST, "called getBpmnDiagramAsJS for: " + processDefinitionKey + " : " + procDefVersion);
		elementId = null;
		elementType = null;
		elementConfig = new HashMap<>();
		// get BPMN in single line
		String bpmnForSelectedProcessAndVersion = getBpmnForSelectedProcessAndVersion();
		String processXML = bpmnForSelectedProcessAndVersion.replaceAll("[\\r\\n]+", "").replaceAll("\\s+", " ")
				.replaceAll("'", "&quot;");
		return "load('" + processXML + "');";
	}

	/**
	 * deletes all historic data for the given process definition key.
	 */
	public void deleteInstances() {
		try {
			dataGenerator.deleteInstances(processDefinitionKey, procDefVersion);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					"Deleted all generated instances for: " + processDefinitionKey + ", version: " + procDefVersion));
		} catch (Exception e) {
			log.log(Level.WARNING, "Exception occurred while deleting Instances.", e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(e.getMessage()));
		}
	}
}