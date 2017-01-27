package de.novatec.demo.util;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.model.bpmn.instance.UserTask;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * @author dle
 */
@Stateless
public class AssigneeHelper {

	private static final Random RANDOM = new Random();

	@Inject
	private IdentityService identityService;

	/**
	 * Checks a {@link UserTask} for assignees or candidate users/groups and return one selected assignee.
	 * A camunda assignee is always used if set.
	 *
	 * @param task the Task you want to get an assignee for.
	 * @return the assignee or null if nothing is set in the bpmn (assignee or candidate user / group).
	 */
	public String getAssignee(UserTask task) {
		// Get Assignee with no 1 Priority.
		String assignee = task.getCamundaAssignee();
		String selectedCandidate = null;

		// Get all candidate users. Use users from candidate groups too
		Set<String> potentialCandidates = new HashSet<>(task.getCamundaCandidateUsersList());
		for (String candidateGroup : task.getCamundaCandidateGroupsList()) {
			List<User> groupMembers = identityService.createUserQuery().memberOfGroup(candidateGroup).list();
			for (User user : groupMembers) {
				potentialCandidates.add(user.getId());
			}
		}
		// Select a random user from all possibilities.
		if (!potentialCandidates.isEmpty()) {
			selectedCandidate = (String) potentialCandidates.toArray()[RANDOM.nextInt(potentialCandidates.size())];
		}
		// Prio:
		// 1. Assignee
		// 2. One of the candidate users / groups
		// 3. null if none exists.
		return assignee != null ? assignee : selectedCandidate != null ? selectedCandidate : null;
	}
}