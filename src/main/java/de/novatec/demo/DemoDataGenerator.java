package de.novatec.demo;

import static org.camunda.bpm.engine.impl.pvm.runtime.ActivityInstanceState.SCOPE_COMPLETE;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Level;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.impl.cfg.IdGenerator;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.entitymanager.DbEntityManager;
import org.camunda.bpm.engine.impl.history.event.HistoricActivityInstanceEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricProcessInstanceEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricTaskInstanceEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoryEvent;
import org.camunda.bpm.engine.impl.history.handler.HistoryEventHandler;
import org.camunda.bpm.engine.impl.history.producer.HistoryEventProducer;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.impl.pvm.PvmActivity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.camunda.bpm.engine.impl.pvm.runtime.PvmExecutionImpl;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.InteractionNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;

import de.novatec.demo.model.DemoDataGeneratorConfig;
import de.novatec.demo.model.ElementType;
import de.novatec.demo.model.ProcessVariable;
import de.novatec.demo.util.AssigneeHelper;
import de.novatec.demo.util.Helper;
import de.novatec.demo.util.RandomGenerator;
import de.novatec.demo.util.Time.START_OF_WORKDAY;
import lombok.NonNull;
import lombok.extern.java.Log;

@Stateless
@Log
public class DemoDataGenerator implements Serializable {

	private static final long serialVersionUID = 8563536849833853255L;

	private static final String BUSINESS_KEY = "demoDataGeneration";

	private final Map<ModelElementInstance, List<BoundaryEvent>> boundaryEvents = new HashMap<>();

	private Helper helper;
	@Inject
	private AssigneeHelper assigneeHelper;
	private ProcessDefinition processDefinition;

	@Inject
	private ProcessEngine processEngine;
	private HistoryEventProducer eventProducer;
	private HistoryEventHandler eventHandler;
	private DbEntityManager dbEntityManager;
	private IdGenerator idGenerator;

	private void initBoundaryEvents(@NonNull BpmnModelInstance bpmn) {
		Collection<ModelElementInstance> boundaryEvents = bpmn
				.getModelElementsByType(bpmn.getModel().getType(BoundaryEvent.class));
		for (ModelElementInstance boundaryEvent : boundaryEvents) {
			BoundaryEvent event = (BoundaryEvent) boundaryEvent;
			List<BoundaryEvent> eventList = this.boundaryEvents.get(event.getAttachedTo());
			eventList = eventList == null ? new ArrayList<BoundaryEvent>() : eventList;
			eventList.add(event);
			this.boundaryEvents.put(event.getAttachedTo(), eventList);
		}
	}

	@Asynchronous
	public Future<Exception> startDataGeneration(@NonNull DemoDataGeneratorConfig generatorConfig) {
		try {
			long startDate = generatorConfig.getStartDate().getTime();
			// set startDate to start of day
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(startDate);
			cal.set(Calendar.HOUR_OF_DAY, START_OF_WORKDAY.HOUR.getValue());
			cal.set(Calendar.MINUTE, START_OF_WORKDAY.MINUTE.getValue());
			cal.set(Calendar.SECOND, START_OF_WORKDAY.SECOND.getValue());
			startDate = cal.getTimeInMillis();

			ProcessEngineConfiguration processEngineConfiguration = processEngine.getProcessEngineConfiguration();
			ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) processEngineConfiguration;

			eventProducer = configuration.getHistoryEventProducer();
			eventHandler = configuration.getHistoryEventHandler();
			idGenerator = configuration.getIdGenerator();

			Context.setCommandContext(new CommandContext(configuration));
			Context.setProcessEngineConfiguration((ProcessEngineConfigurationImpl) processEngineConfiguration);

			dbEntityManager = Context.getCommandContext().getDbEntityManager();

			processDefinition = processEngine.getRepositoryService().createProcessDefinitionQuery()
					.processDefinitionKey(generatorConfig.getProcessDefinitionKey())
					.processDefinitionVersion(generatorConfig.getProcDefVersion()).singleResult();
			BpmnModelInstance bpmn = processEngine.getRepositoryService()
					.getBpmnModelInstance(processDefinition.getId());
			initBoundaryEvents(bpmn);
			helper = new Helper(generatorConfig, bpmn);

			long count = processEngine.getHistoryService().createHistoricProcessInstanceQuery()
					.processDefinitionKey(processDefinition.getKey()).count();
			log.log(Level.INFO, "Historic process instances before data generation: " + count);

			generateDemoData(startDate, generatorConfig.getNumberOfInstances());

			count = processEngine.getHistoryService().createHistoricProcessInstanceQuery()
					.processDefinitionKey(processDefinition.getKey()).count();
			log.log(Level.INFO, "Historic process instances after data generation: " + count);
		} catch (Exception e) {
			log.log(Level.WARNING, "An Exception occurred.", e);
			return new AsyncResult<>(e);
		}
		return new AsyncResult<>(null);
	}

	private DelegateExecution createTestExecution(@NonNull String processInstanceId, @NonNull PvmActivity activity) {
		// create an ExecutionEntity and fill it with all(?) possible values
		ExecutionEntity entity = new ExecutionEntity();
		entity.setProcessDefinition((ProcessDefinitionImpl) processDefinition);
		entity.setProcessDefinitionId(processDefinition.getId());
		entity.setProcessInstance(entity);
		entity.setProcessInstanceId(processInstanceId);
		entity.setActivity(activity);
		entity.setExecutions(new ArrayList<ExecutionEntity>());
		// set businessKey (used to delete generated instances too)
		entity.setBusinessKey(BUSINESS_KEY);
		entity.setId(generateId());
		return entity;
	}

	private void processStart(@NonNull DelegateExecution testExecution, @NonNull Date startTime,
			ModelElementInstance startEvent) {
		HistoricProcessInstanceEventEntity processInstanceStartEvt = (HistoricProcessInstanceEventEntity) eventProducer
				.createProcessInstanceStartEvt(testExecution);
		// Set the startTime based on the calculated startTime value
		processInstanceStartEvt.setStartTime(startTime);
		startAndFinishActivity(testExecution, startEvent, startTime, 0);
		handleEvent(processInstanceStartEvt);
	}

	private void processEnd(@NonNull DelegateExecution execution, @NonNull Date endTime,
			ModelElementInstance endEvent) {
		HistoricProcessInstanceEventEntity processInstanceEndEvt = (HistoricProcessInstanceEventEntity) eventProducer
				.createProcessInstanceEndEvt(execution);
		// Set the endTime based on the calculated endTime value
		processInstanceEndEvt.setEndTime(endTime);
		processInstanceEndEvt.setDurationInMillis(endTime.getTime() - processInstanceEndEvt.getStartTime().getTime());
		startAndFinishActivity(execution, endEvent, endTime, 0);
		handleEvent(processInstanceEndEvt);
		log.log(Level.FINEST, "Using end-event " + execution.getEventName());
	}

	private String generateId() {
		return idGenerator.getNextId();
	}

	private void handleEvent(@NonNull HistoryEvent... historyEvent) {
		eventHandler.handleEvents(Arrays.asList(historyEvent));
	}

	private void generateDemoData(long startDate, int numberOfInstances) {
		for (int i = 0; i < numberOfInstances; i++) {
			StartEvent startEvent = helper.getStartEvent(null);
			startDate = helper.nextInstanceStart(startDate);
			log.log(Level.FINEST, "started Instance at: " + new Date(startDate));
			processStart(startEvent, new Date(startDate));

			// TODO verify the process has ended correctly and throw an
			// Exception if it hasn't

			// insert Events in db and flush + clear inserts to avoid exceptions
			log.log(Level.FINE, "flushing and clearing insert statements");
			dbEntityManager.flush();
			dbEntityManager.getDbOperationManager().inserts.clear();
		}
	}

	private void processStart(@NonNull StartEvent instance, @NonNull Date start) {
		String processInstanceId = generateId();
		DelegateExecution execution = createTestExecution(processInstanceId,
				new ActivityImpl(instance.getId(), (ProcessDefinitionImpl) processDefinition));
		ExecutionEntity entity = (ExecutionEntity) execution;
		entity.setEventName(instance.getName());
		processStart(execution, start, instance);

		List<ModelElementInstance> nextSteps = nextSteps(instance, start);
		for (ModelElementInstance nextStep : nextSteps) {
			processSteps(nextStep, execution, start);
		}
	}

	private void processSteps(@NonNull ModelElementInstance instance, @NonNull DelegateExecution execution,
			@NonNull Date start) {
		if (instance instanceof EndEvent) {
			ExecutionEntity entity = (ExecutionEntity) execution;
			entity.setEventName(((FlowElement) instance).getName());
			entity.setActivity(
					new ActivityImpl(((InteractionNode) instance).getId(), (ProcessDefinitionImpl) processDefinition));
			processEnd(execution, start, instance);
		} else if (instance instanceof BoundaryEvent) {
			// Execute BoundaryEvents with zero duration.
			startAndFinishActivity(execution, instance, start, 0);
		} else if (!(instance instanceof Gateway)) {
			// Process all other Instances that aren't Gateways
			long duration = helper.getDuration(instance, start);
			startAndFinishActivity(execution, instance, start, duration);
			start = new Date(start.getTime() + duration);
		} else {
			startAndFinishActivity(execution, instance, start, 0);
		}

		List<ModelElementInstance> nextSteps = nextSteps(instance, start);
		// recursively process all the steps through the process.
		for (ModelElementInstance nextStep : nextSteps) {
			processSteps(nextStep, execution, start);
		}
	}

	private void startAndFinishActivity(@NonNull DelegateExecution execution, @NonNull ModelElementInstance currentStep,
			@NonNull Date startTime, long duration) {
		ActivityImpl activity = new ActivityImpl(((BaseElement) currentStep).getId(),
				(ProcessDefinitionImpl) processDefinition);

		// create Execution
		DelegateExecution delegateExecution = createTestExecution(execution.getProcessInstanceId(), activity);
		ExecutionEntity executionEntity = (ExecutionEntity) delegateExecution;
		executionEntity.setActivity(activity);
		executionEntity.setParent((PvmExecutionImpl) execution);
		executionEntity.setEventName(((BaseElement) currentStep).getId());

		String executionId = delegateExecution.getId();
		String activityType = currentStep.getElementType().getTypeName();
		String activityName = ((FlowElement) currentStep).getName();
		String parentId = delegateExecution.getParentId() != null ? delegateExecution.getParentId()
				: delegateExecution.getId();

		// create End Events (Start is included)
		// UserTask is written in 2 history tables.
		HistoricTaskInstanceEventEntity taskInstanceCreateEvt = new HistoricTaskInstanceEventEntity();
		if (currentStep instanceof UserTask) {
			UserTask userTask = (UserTask) currentStep;
			TaskEntity task = new TaskEntity(generateId());
			task.setProcessInstanceId(execution.getProcessInstanceId());
			task.setExecutionId(task.getId());
			task.setName(userTask.getName());
			task.setEventName(userTask.getId());
			task.setProcessDefinitionId(execution.getProcessDefinitionId());

			taskInstanceCreateEvt.setProcessDefinitionId(processDefinition.getId());
			taskInstanceCreateEvt.setTaskDefinitionKey(userTask.getId());
			taskInstanceCreateEvt.setStartTime(startTime);
			taskInstanceCreateEvt.setActivityInstanceId((userTask.getId() + ":" + executionId));
			taskInstanceCreateEvt.setExecutionId(executionId);
			taskInstanceCreateEvt.setDeleteReason("completed");
			taskInstanceCreateEvt.setProcessDefinitionKey(processDefinition.getKey());
			taskInstanceCreateEvt.setProcessInstanceId(delegateExecution.getProcessInstanceId());

			taskInstanceCreateEvt.setName(task.getName());
			taskInstanceCreateEvt.setId(task.getId());
			taskInstanceCreateEvt.setPriority(task.getPriority());

			taskInstanceCreateEvt.setEndTime(new Date(startTime.getTime() + duration));
			taskInstanceCreateEvt.setAssignee(assigneeHelper.getAssignee(userTask));
			handleEvent(taskInstanceCreateEvt);
		}

		HistoricActivityInstanceEventEntity activityInstanceEndEvt = (HistoricActivityInstanceEventEntity) eventProducer
				.createActivityInstanceStartEvt(delegateExecution);
		// Set the Variables that are later written to DB
		activityInstanceEndEvt.setParentActivityInstanceId(parentId);
		activityInstanceEndEvt.setId(generateId());
		activityInstanceEndEvt.setActivityType(activityType);
		activityInstanceEndEvt.setActivityName(activityName);
		activityInstanceEndEvt.setExecutionId(executionId);
		activityInstanceEndEvt.setActivityInstanceState(SCOPE_COMPLETE.getStateCode());
		activityInstanceEndEvt.setStartTime(startTime);
		activityInstanceEndEvt.setEndTime(new Date(startTime.getTime() + duration));
		activityInstanceEndEvt.setDurationInMillis(duration);
		activityInstanceEndEvt.setProcessDefinitionKey(processDefinition.getKey());
		activityInstanceEndEvt.setCalledProcessInstanceId(delegateExecution.getProcessInstanceId());

		activityInstanceEndEvt.setTaskId(taskInstanceCreateEvt != null ? taskInstanceCreateEvt.getId() : null);
		handleEvent(activityInstanceEndEvt);

		if (currentStep instanceof BaseElement) {
			List<ProcessVariable> variablesToProcess = helper.getProcessVariables((BaseElement) currentStep);
			VariableInstanceEntity var;
			List<HistoryEvent> variableEvents = new ArrayList<>();
			for (ProcessVariable processVariable : variablesToProcess) {
				var = new VariableInstanceEntity();
				var.setExecution(executionEntity);
				var.setExecutionId(null);
				var.setName(processVariable.getName());
				var.setValue(RandomGenerator.generate(processVariable));
				variableEvents.add(eventProducer.createHistoricVariableCreateEvt(var, executionEntity));
			}
			handleEvent(variableEvents.toArray(new HistoryEvent[variableEvents.size()]));
		}
	}

	/**
	 * calculate the next steps that shall be processed and follow in the
	 * sequence flow.
	 *
	 * @param toProcess
	 *            the current {@link ModelElementInstance}.
	 * @return returns a list with the next Steps
	 */
	private List<ModelElementInstance> nextSteps(@NonNull ModelElementInstance toProcess, Date startDate) {
		List<ModelElementInstance> result = new ArrayList<>();
		List<BoundaryEvent> boundary = boundaryEvents.get(toProcess);
		BoundaryEvent useEvent = null;
		if (boundary != null) {
			for (BoundaryEvent event : boundary) {
				if (helper.useBoundary(event)) {
					useEvent = event;
					break;
				}
			}
		}
		if (toProcess instanceof Gateway) {
			result = helper.getNext((Gateway) toProcess);
		} else if (useEvent != null) {
			result.add(useEvent);
		} else {
			Method getOutgoing;
			Collection<SequenceFlow> possibilities = new ArrayList<>();
			try {
				getOutgoing = toProcess.getClass().getMethod("getOutgoing");
				possibilities = (Collection<SequenceFlow>) getOutgoing.invoke(toProcess);
			} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
				log.log(Level.WARNING, "Could not invoke Method to get Outgoing sequences", e);
			}
			for (SequenceFlow possibility : possibilities) {
				result.add(possibility.getTarget());
			}
		}
		for (ModelElementInstance instance : result) {
			if (instance instanceof SubProcess) {
				// FIXME Subprocess wird quasi asynchron verarbeitet
				processStart(helper.getStartEvent(instance), startDate);
			}
		}
		return result;
	}

	/**
	 * Used to get a List of all deployed {@link ProcessDefinition}s.
	 *
	 * @return
	 */
	public List<ProcessDefinition> getProcessDefinitionList() {
		return processEngine.getRepositoryService().createProcessDefinitionQuery().orderByProcessDefinitionName().asc()
				.latestVersion().list();
	}

	/**
	 * Used to get a List of {@link ProcessDefinition}s with all Versions for a
	 * given process definition key.
	 *
	 * @param processDefinitionKey
	 * @return
	 */
	public List<ProcessDefinition> getProcessVersions(@NonNull String processDefinitionKey) {
		return processEngine.getRepositoryService().createProcessDefinitionQuery().orderByProcessDefinitionVersion()
				.asc().processDefinitionKey(processDefinitionKey).list();
	}

	/**
	 * Used to get the XML for a given process definition key and version as
	 * String representation from the engine.
	 *
	 * @param processDefKey
	 * @param version
	 * @return the String representation of the BPMN diagram.
	 */
	public String getProcessXml(@NonNull String processDefKey, int version) {
		ProcessDefinition processDefinition = processEngine.getRepositoryService().createProcessDefinitionQuery()
				.processDefinitionKey(processDefKey).processDefinitionVersion(version).singleResult();
		BpmnModelInstance bpmn = processEngine.getRepositoryService().getBpmnModelInstance(processDefinition.getId());
		return Bpmn.convertToString(bpmn);
	}

	/**
	 * Used to get the correct {@link ElementType} for a bpmn element in a given
	 * Process for correct presentation in the UI.
	 *
	 * @param processDefKey
	 * @param version
	 * @param elementId
	 * @return
	 */
	public ElementType getElementType(@NonNull String processDefKey, int version, @NonNull String elementId) {
		ProcessDefinition processDefinition = processEngine.getRepositoryService().createProcessDefinitionQuery()
				.processDefinitionKey(processDefKey).processDefinitionVersion(version).singleResult();
		BpmnModelInstance bpmn = processEngine.getRepositoryService().getBpmnModelInstance(processDefinition.getId());
		ModelElementInstance modelElement = bpmn.getModelElementById(elementId);
		if (modelElement == null) {
			return null;
		}
		ModelElementType elementType = modelElement.getElementType();
		if (elementType.getInstanceType() != null
				&& SequenceFlow.class.isAssignableFrom(elementType.getInstanceType())) {
			return ElementType.FLOW;
		} else if (elementType.getBaseType() != null && Task.class.isAssignableFrom(elementType.getInstanceType())) {
			return ElementType.TASK;
		} else if (elementType.getBaseType() != null
				&& BoundaryEvent.class.isAssignableFrom(elementType.getInstanceType())) {
			return ElementType.BOUNDARY;
		} else if ((elementType.getTypeName() != null
				&& StartEvent.class.isAssignableFrom(elementType.getInstanceType()))) {
			return ElementType.START_EVENT;
		} else if ((elementType.getTypeName() != null
				&& EndEvent.class.isAssignableFrom(elementType.getInstanceType()))) {
			return ElementType.END_EVENT;
		}
		return null;
	}

	/**
	 * Used to delete all {@link HistoricActivityInstance}s,
	 * {@link HistoricProcessInstance}s, {@link HistoricTaskInstance} for a
	 * given process definition key and version.
	 *
	 * @param processDefKey
	 * @param version
	 */
	public void deleteInstances(@NonNull String processDefKey, int version) {
		ProcessDefinition processDefinition = processEngine.getRepositoryService().createProcessDefinitionQuery()
				.processDefinitionKey(processDefKey).processDefinitionVersion(version).singleResult();
		HistoryService historyService = processEngine.getHistoryService();

		List<HistoricProcessInstance> processInstances = historyService.createHistoricProcessInstanceQuery()
				.processInstanceBusinessKey(BUSINESS_KEY).finished().processDefinitionId(processDefinition.getId())
				.list();
		for (HistoricProcessInstance historicProcessInstance : processInstances) {
			historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
		}
		log.log(Level.INFO, "Deleted " + processInstances.size() + " process instances for definition: "
				+ processDefinition.getId());
	}
}