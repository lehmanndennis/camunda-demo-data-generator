package de.novatec.demo.util;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.identity.UserQuery;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AssigneeHelperTest {

	private static List<String> CANDIDATE_USERS;
	private static List<String> CANDIDATE_GROUPS;

	@Mock
	private IdentityService identityService;
	@Mock
	private UserTask task;
	@Mock
	private UserQuery userQuery;

	@InjectMocks
	private AssigneeHelper assigneeHelper;

	@BeforeClass
	public static void initGroups() {
		CANDIDATE_USERS = new ArrayList<>();
		CANDIDATE_USERS.add("candidateUser");
		CANDIDATE_GROUPS = new ArrayList<>();
		CANDIDATE_GROUPS.add("group1");
	}

	@Before
	public void init() {
		List<User> users = new ArrayList<>();
		User user = Mockito.mock(User.class);
		when(user.getId()).thenReturn("user1");
		users.add(user);

		when(identityService.createUserQuery()).thenReturn(userQuery);
		when(userQuery.memberOfGroup(anyString())).thenReturn(userQuery);
		when(userQuery.list()).thenReturn(users);
	}

	@Test
	public void testNoAssignees() {
		when(task.getCamundaAssignee()).thenReturn("assignee");

		String assignee = assigneeHelper.getAssignee(task);

		Assert.assertEquals("assignee", assignee);
	}

	@Test
	public void testCandidateUsersList() {
		when(task.getCamundaCandidateUsersList()).thenReturn(CANDIDATE_USERS);

		String assignee = assigneeHelper.getAssignee(task);

		Assert.assertEquals("candidateUser", assignee);
	}

	@Test
	public void testCandidateGroupList() {
		when(task.getCamundaCandidateGroupsList()).thenReturn(CANDIDATE_GROUPS);

		String assignee = assigneeHelper.getAssignee(task);
		verify(userQuery, times(1)).memberOfGroup("group1");

		Assert.assertEquals("user1", assignee);
	}

	@Test
	public void testGroupAndUserList() {
		when(task.getCamundaCandidateUsersList()).thenReturn(CANDIDATE_USERS);
		when(task.getCamundaCandidateGroupsList()).thenReturn(CANDIDATE_GROUPS);

		String assignee = assigneeHelper.getAssignee(task);
		verify(userQuery, times(1)).memberOfGroup("group1");

		Assert.assertTrue("candidateUser".equals(assignee) || "user1".equals(assignee));
	}
}
