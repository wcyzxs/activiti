package io.renren.modules.activiti.test.service;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.EndEvent;
import org.activiti.bpmn.model.ExclusiveGateway;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefiniationProcessService {
	@Autowired
	protected RepositoryService repositoryService;

	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private TaskService taskService;

	@Autowired
	protected HistoryService historyService;

	public void testDynamicDeploy() throws Exception {
		// 1. Build up the model from scratch
		BpmnModel model = new BpmnModel();
		Process process = new Process();
		model.addProcess(process);
		process.setId("multiple-process3");

		// 判断是否仅为一个节点任务
		List<String> taskList = new ArrayList<String>();
		taskList.add("标注1");
		taskList.add("质检初审1");
		taskList.add("质检复审2");
		taskList.add("审核");
		if (taskList.size() == 1) {
			process.addFlowElement(createStartEvent());
			process.addFlowElement(createUserTask("task1", taskList.get(0), null));
			process.addFlowElement(createEndEvent());
			process.addFlowElement(createSequenceFlow("start", "task1", "", ""));
			process.addFlowElement(createSequenceFlow("task1", "end", "", ""));

		} else {
			// 多节点任务
			// 构造开始节点任务
			process.addFlowElement(createStartEvent());
			// 构造首个节点任务
			process.addFlowElement(createUserTask("task1", taskList.get(0), null));
			// 构造除去首尾节点的任务
			for (int i = 1; i < taskList.size() - 1; i++) {
				process.addFlowElement(createExclusiveGateway("createExclusiveGateway" + i));
				process.addFlowElement(createUserTask("task" + (i + 1), taskList.get(i), null));
			}
			// 构造尾节点任务
			process.addFlowElement(createExclusiveGateway("createExclusiveGateway" + (taskList.size() - 1)));
			process.addFlowElement(createUserTask("task" + taskList.size(), taskList.get(taskList.size() - 1), null));
			// 构造结束节点任务
			process.addFlowElement(createEndEvent());

			// 构造连线(加网关)
			process.addFlowElement(createSequenceFlow("start", "task1", "", ""));
			// 第一个节点任务到第二个百分百通过的，因此不存在网关
			process.addFlowElement(createSequenceFlow("task1", "task2", "", ""));
			for (int i = 1; i < taskList.size(); i++) {
				process.addFlowElement(createSequenceFlow("task" + (i + 1), "createExclusiveGateway" + i, "", ""));
				// 判断网关走向(同意则直接到下一节点即可，不同意需要判断回退层级，决定回退到哪个节点，returnLevel等于0，即回退到task1)
				// i等于几，即意味着回退的线路有几种可能，例如i等于1，即是task2,那么只能回退 到task1
				// 如果i等于2，即是task3,那么此时可以回退到task1和task2
				for (int j = 1; j <= i; j++) {
					process.addFlowElement(createSequenceFlow("createExclusiveGateway" + i, "task" + j, "不通过",
							"${result == '0' && returnLevel== '" + j + "'}"));
				}

				// 操作结果为通过时，需要判断是否为最后一个节点任务，若是则直接到end
				if (i == taskList.size() - 1) {
					process.addFlowElement(
							createSequenceFlow("createExclusiveGateway" + i, "end", "通过", "${result == '1'} "));

				} else {
					process.addFlowElement(createSequenceFlow("createExclusiveGateway" + i, "task" + (i + 2), "通过",
							"${result == '1'}"));
				}

			}

		}

		// 2. Generate graphical information
		new BpmnAutoLayout(model).execute();

		// 3. Deploy the process to the engine
		Deployment deployment = repositoryService.createDeployment().addBpmnModel("dynamic-model.bpmn", model)
				.name("multiple process deployment").deploy();

		// 4. Start a process instance
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("multiple-process3");
		System.out.println("流程实例ID---》》》" + processInstance.getId());

		// 6. Save process diagram to a file
		InputStream processDiagram = repositoryService.getProcessDiagram(processInstance.getProcessDefinitionId());
		FileUtils.copyInputStreamToFile(processDiagram, new File("target/multiple-process3-diagram.png"));

		// 7. Save resulting BPMN xml to a file
		InputStream processBpmn = repositoryService.getResourceAsStream(deployment.getId(), "dynamic-model.bpmn");
		FileUtils.copyInputStreamToFile(processBpmn, new File("target/multiple-process3.bpmn20.xml"));
	}

	protected UserTask createUserTask(String id, String name, String assignee) {
		UserTask userTask = new UserTask();
		userTask.setName(name);
		userTask.setId(id);
		userTask.setAssignee(assignee);
		return userTask;
	}

	protected SequenceFlow createSequenceFlow(String from, String to, String name, String conditionExpression) {
		SequenceFlow flow = new SequenceFlow();
		flow.setSourceRef(from);
		flow.setTargetRef(to);
		flow.setName(name);
		if (StringUtils.isNotEmpty(conditionExpression)) {
			flow.setConditionExpression(conditionExpression);
		}
		return flow;
	}

	/* 排他网关 */
	protected static ExclusiveGateway createExclusiveGateway(String id) {
		ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
		exclusiveGateway.setId(id);
		return exclusiveGateway;
	}

	protected StartEvent createStartEvent() {
		StartEvent startEvent = new StartEvent();
		startEvent.setId("start");
		return startEvent;
	}

	protected EndEvent createEndEvent() {
		EndEvent endEvent = new EndEvent();
		endEvent.setId("end");
		return endEvent;
	}

	public void completeTask(String taskId, Map<String, Object> map) {
		taskService.complete(taskId, map);
	}
}