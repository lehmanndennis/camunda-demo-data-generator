package de.novatec.demo.util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import de.novatec.demo.model.ConfigurationEntry;
import de.novatec.demo.model.DemoDataGeneratorConfig;
import de.novatec.demo.model.ProcessVariable;
import lombok.extern.java.Log;

/**
 * Helper class for everything containing a random selection of start events or
 * next process steps. The calculation of durations is contained too.
 */
@Log
public class Helper {
	// BBPMN attribute names
	public static final String MEAN = "mean";
	public static final String SD = "sd";
	// Distribution
	public static final int DEFAULT_SD = 200; // in seconds
	public static final int DEFAULT_MEAN = 600; // in seconds

	public static final int BOUNDARY_POSSIBILITY = 25;

	public static final NormalDistribution DEFAULT = new NormalDistribution(DEFAULT_MEAN, DEFAULT_SD);
	private static final Random RANDOM = new Random();
	private static final int MAX_PROBABILITY = 100;
	private final Map<ModelElementInstance, NormalDistribution> distributions = new HashMap<>();
	private NormalDistribution nextInstanceStartDistribution;
	private Map<ModelElementInstance, RandomSelector<ConfigurationEntry>> randomSelectors = new HashMap<>();
	private DemoDataGeneratorConfig config;
	Map<Object, List<StartEvent>> startEvents = new HashMap<>();

	public Helper(DemoDataGeneratorConfig config, BpmnModelInstance bpmnModelInstance) {
		super();
		this.config = config;
		nextInstanceStartDistribution = new NormalDistribution(config.getMeanBetweenStarts(),
				config.getSdBetweenStarts());
		Map<String, ConfigurationEntry> elementConfigurations = config.getElementConfigurations();
		Collection<Task> tasks = bpmnModelInstance.getModelElementsByType(Task.class);
		for (Task task : tasks) {
			ConfigurationEntry configurationEntry = elementConfigurations.get(task.getId());
			NormalDistribution normalDistribution;
			if (configurationEntry != null) {
				normalDistribution = new NormalDistribution(configurationEntry.getMean(), configurationEntry.getSd());
			} else {
				normalDistribution = DEFAULT;
			}
			distributions.put(task, normalDistribution);
		}
		Collection<Gateway> gateways = bpmnModelInstance.getModelElementsByType(Gateway.class);
		for (Gateway gateway : gateways) {
			// skip ParallelGateways. There are all sequenceFlows selected and
			// no random selection necessary.
			if (gateway instanceof ParallelGateway) {
				continue;
			}
			Collection<SequenceFlow> outgoing = gateway.getOutgoing();
			List<ConfigurationEntry> possibilities = new ArrayList<>();
			for (SequenceFlow sequenceFlow : outgoing) {
				ConfigurationEntry configurationEntry = elementConfigurations.get(sequenceFlow.getId());
				possibilities.add(configurationEntry);
			}
			try {
				randomSelectors.put(gateway, new RandomSelector<>(possibilities));
			} catch (IllegalArgumentException e) {
				log.log(Level.WARNING, "An Exception occurred in initializing RandomSelector for: " + gateway.getId(),
						e);
				throw e;
			}
		}
		List<StartEvent> startEvents = new ArrayList<>();
		startEvents.addAll(bpmnModelInstance.getModelElementsByType(StartEvent.class));
		this.startEvents.put(null, startEvents);
		Collection<SubProcess> subs = bpmnModelInstance.getModelElementsByType(SubProcess.class);
		for (SubProcess subProcess : subs) {
			List<StartEvent> events = new ArrayList<>();
			this.startEvents.put(subProcess, events);
			for (StartEvent event : subProcess.getChildElementsByType(StartEvent.class)) {
				startEvents.remove(event);
				events.add(event);
			}
		}
	}

	/**
	 * calculates if a boundary event shall be used.
	 *
	 * @param event
	 *            the boundary event which could be used.
	 * @return true if the event shall be used. False otherwise.
	 */
	public boolean useBoundary(BoundaryEvent event) {
		ConfigurationEntry configurationEntry = config.getElementConfigurations().get(event.getId());
		if (configurationEntry != null && configurationEntry.getProbability() <= 100) {
			return RANDOM.nextInt(MAX_PROBABILITY) + 1 <= configurationEntry.getProbability();
		} else {
			return RANDOM.nextInt(MAX_PROBABILITY) + 1 <= BOUNDARY_POSSIBILITY;
		}
	}

	/**
	 * Calculates a {@link List} of {@link ModelElementInstance}s for a given
	 * Gateway. For a parallel gateway all Outgoing sequence flows are used.
	 *
	 * @param gateway
	 * @return
	 */
	public List<ModelElementInstance> getNext(Gateway gateway) {
		List<ModelElementInstance> result = new ArrayList<>();
		if (gateway instanceof ParallelGateway) {
			// and gateway -> use all outgoing sequence flows.
			for (SequenceFlow flow : gateway.getOutgoing()) {
				result.add(flow.getTarget());
			}
		} else {
			ConfigurationEntry random = randomSelectors.get(gateway).getRandom();
			// Random outgoing for the moment
			if (random == null) {
				// Random outgoing for the moment
				int size = gateway.getOutgoing().size();
				int use = Helper.RANDOM.nextInt(size);
				result.add(((SequenceFlow) gateway.getOutgoing().toArray()[use]).getTarget());
			} else {
				for (SequenceFlow sequenceFlow : gateway.getOutgoing()) {
					if (sequenceFlow.getId().equals(random.getElementId())) {
						result.add(sequenceFlow.getTarget());
						break;
					}
				}
			}
		}
		return result;
	}

	/**
	 * Used to get a (random) Start Event for a bpmn Model. Default is a random
	 * selection of all possibilities. If default shall not be used a
	 * configuration for all bpmn StartEvents is needed.
	 *
	 * @param startEvents
	 *            a collection with all start events.
	 * @return
	 */
	public StartEvent getStartEvent(ModelElementInstance instance) {
		List<StartEvent> startEvents = this.startEvents.get(instance);
		boolean useRandom = false;
		List<ConfigurationEntry> configurations = new ArrayList<>();
		for (ModelElementInstance startEvent : startEvents) {
			ConfigurationEntry configurationEntry = config.getElementConfigurations()
					.get(((BaseElement) startEvent).getId());
			configurations.add(configurationEntry);
			if (configurationEntry == null) {
				useRandom = true;
				break;
			}
		}
		StartEvent event = null;
		if (useRandom) {
			log.log(Level.FINE, "Using random Start-Event");
			event = getRandomStartEvent(startEvents);
		} else {
			RandomSelector<ConfigurationEntry> selector = new RandomSelector<>(configurations);
			ConfigurationEntry random = selector.getRandom();
			if (random == null) {
				event = getRandomStartEvent(startEvents);
			} else {
				for (ModelElementInstance startEvent : startEvents) {
					if (random.getElementId().equals(((BaseElement) startEvent).getId())) {
						event = (StartEvent) startEvent;
						break;
					}
				}
			}
		}
		log.log(Level.FINE, "Event: " + event.getId());
		return event;
	}

	private StartEvent getRandomStartEvent(Collection<StartEvent> startEvents) {
		int size = startEvents.size();
		int use = RANDOM.nextInt(size);
		return startEvents.toArray(new StartEvent[size])[use];
	}

	private NormalDistribution getDistributions(ModelElementInstance modelElementInstance) {
		NormalDistribution normalDistribution = distributions.get(modelElementInstance);
		if (normalDistribution == null) {
			normalDistribution = getDistributionForElement(modelElementInstance);
			distributions.put(modelElementInstance, normalDistribution);
		}
		return normalDistribution;
	}

	private NormalDistribution getDistributionForElement(ModelElementInstance element) {
		if (element == null)
			return DEFAULT;
		String mean = element.getAttributeValue(MEAN);
		String sd = element.getAttributeValue(SD);
		if (mean == null || sd == null) {
			return DEFAULT;
		}
		return new NormalDistribution(Double.valueOf(mean), Double.valueOf(sd));
	}

	/**
	 * Used to get the next Start Date for an Process instance.
	 *
	 * @param current
	 *            the date of the last instance start.
	 * @return The next (random) Date after current. The Date is a workday and
	 *         in working hours.
	 */
	public long nextInstanceStart(long current) {
		long offset = Math.round(nextInstanceStartDistribution.sample());
		offset *= Time.DISTRIBUTION.SECOND.getValue();
		current += offset;
		return current;
	}

	private boolean isInWorktime(long current) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(current);
		int day = cal.get(Calendar.DAY_OF_WEEK);
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int minute = cal.get(Calendar.MINUTE);
		// weekend
		if (day == Calendar.SUNDAY || day == Calendar.SATURDAY) {
			return false;
		} else if (hour > Time.END_OF_WORKDAY.HOUR.getValue()) {
			return false;
		} else if (hour == Time.END_OF_WORKDAY.HOUR.getValue() && minute > Time.END_OF_WORKDAY.MINUTE.getValue()) {
			return false;
		} else if (hour < Time.START_OF_WORKDAY.HOUR.getValue()) {
			return false;
		} else if (hour == Time.START_OF_WORKDAY.HOUR.getValue() && minute < Time.START_OF_WORKDAY.MINUTE.getValue()) {
			return false;
		}
		return true;
	}

	/**
	 * get the duration for the specific Element. It uses a normal Distribution.
	 * mean and sd can be set in minutes in the bpmn as attributes. If nothing
	 * is set a default will be used.
	 *
	 * @param element
	 * @param start
	 * @return
	 */
	public long getDuration(ModelElementInstance element, Date start) {
		long endTime = start.getTime();
		do {
			long offset = Math.round(getDistributions(element).sample());
			offset *= Time.DISTRIBUTION.SECOND.getValue();
			endTime += offset;
			if (!(element instanceof UserTask || element instanceof StartEvent)) {
				break;
			}
		} while (!isInWorktime(endTime) && ((element instanceof StartEvent && config.isUseWorkdaysForNewProcesses())
				|| (element instanceof UserTask && config.isUseWorkdaysForUsertasks())));
		return endTime - start.getTime();
	}

	public List<ProcessVariable> getProcessVariables(BaseElement currentStep) {
		Map<String, ConfigurationEntry> elementConfigurations = config.getElementConfigurations();
		ConfigurationEntry elementConfig = elementConfigurations.get(currentStep.getId());
		if (elementConfig != null) {
			return elementConfig.getProcessVariables();
		} else {
			return new ArrayList<>();
		}
	}
}