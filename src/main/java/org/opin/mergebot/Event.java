/**
 * Copyright 2020 Association for the promotion of open-source insurance software and for the establishment of open interface standards in the insurance industry (Verein zur Foerderung quelloffener Versicherungssoftware und Etablierung offener Schnittstellenstandards in der Versicherungsbranche)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opin.mergebot;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import groovy.lang.Binding;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles the execution of the groovy scripts. Parent class of all Events.
 */
public abstract class Event {
	static final String VALIDATION_DETAIL = "All checks were successful.";
	static final String VALIDATION_HEAD = "SUCCESS";
	protected static final String ABSOLUTE_PATH_CONFIGURATION_FILES = String
			.valueOf(WebhookListener.configuration.get("ABSOLUTE_PATH_CONFIGURATION_FILES"));
	static final String PATH_GROOVY_SCRIPT_PRE = String
			.valueOf(WebhookListener.configuration.get("PATH_GROOVY_SCRIPT_PRE"));
	static final String PATH_GROOVY_SCRIPT_POST = String
			.valueOf(WebhookListener.configuration.get("PATH_GROOVY_SCRIPT_POST"));
	static final String PATH_GROOVY_CONFIGURATION = String
			.valueOf(WebhookListener.configuration.get("PATH_GROOVY_CONFIGURATION"));
	protected static final String MERGEBOT_ID = "MB";
	static final String PATH_MERGE_TRIGGERS = String.valueOf(WebhookListener.configuration.get("PATH_MERGE_TRIGGERS"));
	static final String PATH_STATUS_CHECK_MESSAGES = String.valueOf(WebhookListener.configuration.get("PATH_STATUS_CHECK_MESSAGES"));
	static final String PATH_MAINTAINER = provideMaintainerFilePathIfPresent();
	static final String PATH_MERGE_CONFLICT_INSTRUCTIONS = String.valueOf(WebhookListener.configuration.get("PATH_MERGE_CONFLICT_INSTRUCTIONS"));

	static final String MB_BRANCH_EXISTS_DETAIL = "Please delete: ";
	static final String MB_BRANCH_EXISTS = "MB-Branch exists";
	//TODO add User and Token will be implenmented differently in one of the next Releases
	static final String USER = "";
	static final String TOKEN = "";
	static final String API_URL = String.valueOf(WebhookListener.configuration.get("API_URL"));
	static final String NO_PORTING = String.valueOf(WebhookListener.configuration.get("NO_PORTING"));
	static final String RERUN_CHECK = String.valueOf(WebhookListener.configuration.get("RERUN_CHECK"));
	static final String PATH_DELIMITER = "/";
	protected static final String PARTING_LINE = "___________________________________________________________________________________________________________________________________";
	protected static final String LOGGER_MERGE_FROM_BRANCH = "___mergeFromBranch___";
	protected static final String LOGGER_MERGE_TO_BRANCH = "___mergeToBranch___";
	protected static final String MERGE_COMMIT_SHA = "Merge Commit SHA ";
	protected static final String REFS_HEADS_PATH = "refs/heads/";
	protected static final String HEADS_PATH = "heads/";
	protected static final String MANUAL = String.valueOf(WebhookListener.configuration.get("MANUAL"));

	private static Logger logger = LogManager.getLogger(Event.class);

	/**
	 * Process the POST-Request from the Webhook in the corresponding sub-class.
	 * 
	 * @param incoming  the incoming request from the Webhook.
	 * @param outgoing  the outgoing request to GitHub if Webhook payload was
	 *                  received correctly.
	 * @param eventType the type of the event.
	 */
	abstract void process(HttpServletRequest incoming, HttpServletResponse outgoing, String eventType);

	/**
	 * Handles the execution of the groovy script.
	 * 
	 * @return <code>true</code> if all validations were passed otherwise
	 *         <code>false</code>
	 */
	public String executeGroovyScript(String groovyScriptName, String path) {
		Object output;
		try (StringWriter buff = new StringWriter(); PrintWriter out = new PrintWriter(buff);) {
			Binding binding = new Binding();
			binding.setVariable("path", path);
			GroovyShellInstance shell = GroovyShellInstance.getInstance();
			File file = new File(groovyScriptName);
			shell.setVariable("out", out);
			String[] input = new String[] { path };
			output = shell.run(file, input);
			return output != null ? output.toString():null;
		} catch (IOException io) {
			logger.error("Close Buffer trouble", io);
		} catch (Exception e) {
			logger.error("ERROR-execute Groovy-Script", e);
		}
		return null;
	}
	
	protected String executePreCommitGroovyScript(GHRepository ghRepo, GHPullRequest existingPullRequest,
			String initialDeveloper) {
		String output = null;
		String pathPreFile = "preInput"+existingPullRequest.getId()+".json";
		File preFile = new File(pathPreFile);
		try {
			ScriptInputPre scriptInputPre = new ScriptInputPre();
			scriptInputPre.setFilesToMerge(getChangedFilesFromPullRequest(existingPullRequest, ghRepo));
			scriptInputPre.setUser(existingPullRequest.getUser().getLogin());
			scriptInputPre.setUserEmail(existingPullRequest.getUser().getEmail());
			scriptInputPre.setCommentsList(splitUpCommitMessages(existingPullRequest.listCommits()));
			scriptInputPre.setInitialDeveloper(initialDeveloper);
			scriptInputPre.setReviewerList(getRequestedReviewer(existingPullRequest));
			scriptInputPre.setReviewSubmitterList(getReviewSubmitterList(existingPullRequest));
			scriptInputPre.setMaintainerFilePath(PATH_MAINTAINER);
			ScriptInputToJson toJson = new ScriptInputToJson();
			toJson.createScriptInputAsJson(scriptInputPre, preFile);
			if (preFile.exists()) {
				output = executeGroovyScript(PATH_GROOVY_SCRIPT_PRE, pathPreFile);
				logger.info("REPO: {} OUTPUT from {}::::::{}",ghRepo.getFullName(), pathPreFile, output);
			}
		} catch (IOException e) {
			logger.info("Can not get information for PR:{}", existingPullRequest.getNumber(), e);
		} finally {
			try {
				Files.deleteIfExists(preFile.toPath());
			} catch (IOException e) {
				logger.info("Can not delete file for PR:{}{}", existingPullRequest.getNumber(), preFile.getAbsolutePath(), e);
			}
		}
		return output;
	}

	protected void executePostCommitGroovyScript(GHPullRequest initialPullRequest, GHPullRequest existingPullRequest) {
		String output = null;
		String pathPostFile = "postInput"+existingPullRequest.getId()+".json";
		File postFile = new File(pathPostFile);
		try {
			ScriptInputPost scriptInputPost = new ScriptInputPost();
			scriptInputPost.setReviewerList(getApprovedReviewers(initialPullRequest));
			if (initialPullRequest.getNumber() == existingPullRequest.getNumber()) {
				scriptInputPost.setPullRequestUrl(initialPullRequest.getHtmlUrl().toString());
			} else {
				scriptInputPost.setPullRequestUrl(StringUtils.EMPTY);
			}
			ScriptInputToJson toJson = new ScriptInputToJson();
			toJson.createScriptInputAsJson(scriptInputPost, postFile);
			if (postFile.exists()) {
				output = executeGroovyScript(PATH_GROOVY_SCRIPT_POST, pathPostFile);
				logger.info("REPO: {} OUTPUT from {}::::::{}",existingPullRequest.getRepository().getFullName(), pathPostFile, output);
			}
		} finally {
			try {
				Files.deleteIfExists(postFile.toPath());
			} catch (IOException e) {
				logger.info("Can not delete file for PR:{}{}", existingPullRequest.getNumber(), postFile.getAbsolutePath(), e);
			}
		}
	}

	protected void setStatusFromGroovyScripts(GHRepository ghRepo, GHPullRequest existingPullRequest, String output)
			throws IOException {
		ArrayList<String> detectedErrors = new ArrayList<>();
		try {
			if (!output.isEmpty()) {
				String[] errors = output.split("\n");
				Collections.addAll(detectedErrors, errors); 
			}
			ObjectMapper mapper = new ObjectMapper();

			Map<String, StatusCheckProvider> readValue;
			readValue = mapper.readValue(new File(PATH_STATUS_CHECK_MESSAGES),
					new TypeReference<HashMap<String, StatusCheckProvider>>() {
					});

			readValue.forEach((key, value) -> 
					setStatusDirectlyInPr(!(detectedErrors.contains(key) && !detectedErrors.isEmpty()), ghRepo, existingPullRequest, value.validationHead,
							value.validationDetail)
			);
			readValue.clear();
		} catch (JsonProcessingException e) {
			logger.info("Parsing StatusCheckMessages encountered a Problem.", e);
		}
	}

	List<FilesToMerge> getChangedFilesFromPullRequest(GHPullRequest pullRequest, GHRepository ghRepo) {
		GHPullRequest pr;
		List<FilesToMerge> listFileNames = new ArrayList<>();
		try {
			pr = ghRepo.getPullRequest(pullRequest.getNumber());
			pr.listFiles().forEach(file -> listFileNames.add(new FilesToMerge(getFileName(file), getPluginName(file))));
		} catch (IOException e) {
			logger.info("Can not get changed files from PR.", e);
		}
		return listFileNames;
	}

	List<String> getApprovedReviewers(GHPullRequest pullRequest) {
		List<String> reviewersList = new ArrayList<>();
		pullRequest.listReviews().forEach(r -> {
			try {
				if (r.getState().equals(GHPullRequestReviewState.APPROVED)) {
					reviewersList.add(r.getUser().getLogin());
				}
			} catch (IOException e) {
				logger.debug("Tried to extract Reviewer", e);
			}
		});
		return reviewersList;
	}

	List<String> getRequestedReviewer(GHPullRequest pullRequest) {
		List<String> reviewersList = new ArrayList<>();
		try {
			pullRequest.getRequestedReviewers().forEach(r -> reviewersList.add(r.getLogin()));
		} catch (IOException e) {
			logger.debug("Requested Reviewers encountered a problem.", e);
		}
		return reviewersList;
	}
	
	List<String> getReviewSubmitterList(GHPullRequest pullRequest) {
		List<String> submittedList = new ArrayList<>();
		pullRequest.listReviews().forEach(r -> {
			try {
				submittedList.add(r.getUser().getLogin());
			} catch (IOException e) {
				logger.debug("Submitted reviews encountered a problem.", e);
			}
		});
		return submittedList;
	}
	
	private static String provideMaintainerFilePathIfPresent() {
		String maintainerFilePath = String.valueOf(WebhookListener.configuration.get("PATH_MAINTAINER"));
				return 
					(maintainerFilePath == null 
					|| maintainerFilePath.isEmpty() 
					|| maintainerFilePath.equals("NO_MAINTAINER_FILE"))
					? "NO_MAINTAINER_FILE" : String.valueOf(WebhookListener.configuration.get("PATH_MAINTAINER"));
	}

	private List<String> splitUpCommitMessages(PagedIterable<GHPullRequestCommitDetail> pagedIterable) {
		List<String> commitMessagesList = new ArrayList<>();
		pagedIterable.forEach(commit -> commitMessagesList.add(commit.getCommit().getMessage()));
		return commitMessagesList;
	}

	private String getPluginName(GHPullRequestFileDetail file) {
		return file.getFilename();
	}

	private String getFileName(GHPullRequestFileDetail file) {
		if (file.getFilename().contains("/")) {
			return file.getFilename().substring(file.getFilename().lastIndexOf('/') + 1);
		}
		return file.getFilename();
	}

	protected void setStatusDirectlyInPr(boolean success, GHRepository ghRepo, GHPullRequest existingPullRequest,
			String header, String detailText) {
		GHCommit commit;
		try {
			commit = ghRepo.getCommit(existingPullRequest.getHead().getSha());
			if (success) {
				ghRepo.createCommitStatus(commit.getSHA1(), GHCommitState.SUCCESS, commit.getHtmlUrl().toString(),
						detailText, header);
			} else {
				ghRepo.createCommitStatus(commit.getSHA1(), GHCommitState.FAILURE, commit.getHtmlUrl().toString(),
						detailText, header);
			}
		} catch (IOException e) {
			logger.info("Failed to set Status in PR {} {}", existingPullRequest.getNumber(), e);
		}
	}

	protected int getNumberInitialPullRequest(GHPullRequest existingPullRequest) {
		if (existingPullRequest.getHead().getRef().contains(MERGEBOT_ID)) {
			return Integer.valueOf(extractInitialPrFromComment(existingPullRequest));

		} else {
			return existingPullRequest.getNumber();
		}
	}

	String extractInitialPrFromComment(GHPullRequest existingPullRequest) {
		String text = existingPullRequest.getBody();
		text = text.replace("\r\n", "\n");
		if (!text.isEmpty()) {
			String[] tmpPull = text.split("\n");
			String[] numberPR = tmpPull[0].split("#");
			return numberPR[1];
		}
		logger.info("No body for PullRequest {}", existingPullRequest.getNumber());
		return text;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, String> generateMergeTriggerMap() {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.readValue(new File(PATH_MERGE_TRIGGERS), Map.class);
		} catch (IOException e) {
			logger.info("Parsing MergeTrigger rules encountered a problem.", e);
		} finally {
			mapper.clearProblemHandlers();
		}
		return null;

	}

	protected void runStatusChecks(Map<String, String> mergeTriggerMap, GHRepository ghRepo,
			GHPullRequest existingPullRequest, String mergeToBranch, GHPullRequest initialPullRequest) {
		try {
			checkIfMbBranchAlreadyExists(mergeTriggerMap, ghRepo, existingPullRequest, initialPullRequest,
					mergeToBranch);
			checkIfDeveloperIsOwnReviewer(ghRepo, existingPullRequest, initialPullRequest);
			String output = executePreCommitGroovyScript(ghRepo, existingPullRequest,
					initialPullRequest.getUser().getLogin());
			setStatusFromGroovyScripts(ghRepo, existingPullRequest, output);
		} catch (IOException e) {
			logger.info("Not able to run Status Checks.", e);
		}
	}

	protected void rerunStatusChecks(Map<String, String> mergeTriggerMap, GHRepository ghRepo,
			GHPullRequest existingPullRequest) {
		try {
			GHPullRequest initialPullRequest = ghRepo.getPullRequest(getNumberInitialPullRequest(existingPullRequest));
			runStatusChecks(mergeTriggerMap, ghRepo, existingPullRequest, existingPullRequest.getBase().getRef(),
					initialPullRequest);
			existingPullRequest.removeLabels(RERUN_CHECK);
		} catch (IOException e) {
			logger.info("Not able to rerun Status Checks.", e);
		}
	}

	void checkIfMbBranchAlreadyExists(Map<String, String> mergeTriggerMap, GHRepository ghRepo,
			GHPullRequest existingPullRequest, GHPullRequest initialPullRequest, String mergeToBranch) {
			if (existingPullRequest.getBase().getRef().equals(ghRepo.getDefaultBranch())
					|| containsLabel(existingPullRequest, NO_PORTING)) {
				setStatusDirectlyInPr(true, ghRepo, existingPullRequest, MB_BRANCH_EXISTS, MB_BRANCH_EXISTS_DETAIL);
			}else {
				checkIfOneOfTheMbBranchesAlreadyExists(initialPullRequest, existingPullRequest, ghRepo, mergeToBranch,
						mergeTriggerMap);
			}
	}

	void checkIfDeveloperIsOwnReviewer(GHRepository ghRepo, GHPullRequest existingPullRequest,
			GHPullRequest initialPullRequest) {
		boolean selfReview;
		selfReview = approvedReviewerIsDeveloper(existingPullRequest, initialPullRequest);
		setStatusDirectlyInPr(!selfReview, ghRepo, existingPullRequest, "Self-Review",
				"Choose another reviewer. It is not allowed to review own PR.");
	}

	void checkIfOneOfTheMbBranchesAlreadyExists(GHPullRequest initialPullRequest, GHPullRequest existingPullRequest,
			GHRepository ghRepo, String mergeToBranch, Map<String, String> mergeTriggerMap) {
		List<String> mergePath;
		try {
			mergePath = getBranchesWhereItShouldBeMerged(initialPullRequest, existingPullRequest, mergeToBranch,
					mergeTriggerMap);
			if (!mergePath.isEmpty()) {
				Map<String, GHBranch> branches = ghRepo.getBranches();
				Set<String> branchSet = branches.keySet();
				List<String> existingMbBranch = new ArrayList<>();
				mergePath.forEach(f -> {
					if (branchSet.contains(f)) {
						existingMbBranch.add(f);
					}

				});
				if (existingMbBranch.isEmpty()) {
					setStatusDirectlyInPr(true, ghRepo, existingPullRequest, MB_BRANCH_EXISTS, MB_BRANCH_EXISTS_DETAIL);
				} else {
					setStatusDirectlyInPr(false, ghRepo, existingPullRequest, MB_BRANCH_EXISTS,
							MB_BRANCH_EXISTS_DETAIL + String.join(" | ", existingMbBranch));
				}
			} else {
				setStatusDirectlyInPr(true, ghRepo, existingPullRequest, MB_BRANCH_EXISTS, MB_BRANCH_EXISTS_DETAIL);
			}
		} catch (IOException e) {
			logger.info("Creating MergeBotBranch encountered a Problem.", e);
		}
	}

	List<String> getBranchesWhereItShouldBeMerged(GHPullRequest initialPullRequest, GHPullRequest existingPullRequest,
			String mergeToBranch, Map<String, String> mergeTriggerMap) {
		List<String> mergePath = new ArrayList<>();
		String value = mergeTriggerMap.get(mergeToBranch);
		while (value != null) {
			mergePath.add(getNameTmpMergeBotBranch(initialPullRequest, existingPullRequest, value));
			value = mergeTriggerMap.get(value);
		}
		mergePath.forEach(m -> logger.info(MessageFormat.format("MERGEPATH_VALUE {0}", m)));
		return mergePath;
	}
	boolean approvedReviewerIsDeveloper(GHPullRequest existingPullRequest, GHPullRequest initialPullRequest) {
		try {
			existingPullRequest.refresh();
			List<String> approvedReviewers = getApprovedReviewers(existingPullRequest);
			if (!existingPullRequest.getHead().getRef().contains(MERGEBOT_ID)) {
				return false;
			}
			if (approvedReviewers.isEmpty()) {
				return false;
			} else {
			approvedReviewers.removeAll(Collections.singletonList(initialPullRequest.getUser().getLogin()));
			return approvedReviewers.isEmpty();
			}
		} catch (IOException e) {
			logger.info("Selfreview requested", e);
		}
		return false;
	}

	protected String getNameTmpMergeBotBranch(GHPullRequest initialPullRequest, GHPullRequest existingPullRequest,
			String mergeToBranch) {
		if (existingPullRequest.getHead().getRef().contains(MERGEBOT_ID)) {
			return generateTmpMergeBotBranchFromMbBranch(initialPullRequest, existingPullRequest, mergeToBranch);
		} else {
			String[] tmp = existingPullRequest.getHead().getRef().split("/", 3);
			return generateMbTmpBranchFromInitialPr(initialPullRequest, mergeToBranch, tmp);
		}
	}

	private String generateMbTmpBranchFromInitialPr(GHPullRequest initialPullRequest, String mergeToBranch,
			String[] tmp) {
		try {
			return tmp[0] + PATH_DELIMITER + mergeToBranch + PATH_DELIMITER + MERGEBOT_ID + PATH_DELIMITER
					+ initialPullRequest.getUser().getLogin() + PATH_DELIMITER + tmp[2];
		} catch (IOException e) {
			logger.info("Cannot get User - can not generate MbBranch from initial PR", e);
		}
		return null;
	}

	private String generateTmpMergeBotBranchFromMbBranch(GHPullRequest initialPullRequest,
			GHPullRequest existingPullRequest, String mergeToBranch) {
		try {
			String[] tmp = existingPullRequest.getHead().getRef().split("/", 5);
			return tmp[0] + PATH_DELIMITER + mergeToBranch + PATH_DELIMITER + MERGEBOT_ID + PATH_DELIMITER
					+ initialPullRequest.getUser().getLogin() + PATH_DELIMITER + tmp[4];
		} catch (IOException e) {
			logger.info("Cannot generate TmpMergeBotBranch from MbBranch.", e);
		}
		return null;
	}

	protected boolean containsLabel(GHPullRequest pullRequest, String labelName) {
		return pullRequest.getLabels().stream().anyMatch(l -> l.getName().equals(labelName));
	}

	public static String getMergebotId() {
		return MERGEBOT_ID;
	}
}
