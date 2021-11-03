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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitBuilder;
import org.kohsuke.github.GHContentBuilder;
import org.kohsuke.github.GHContentUpdateResponse;
import org.kohsuke.github.GHEventPayload.PullRequest;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequest.MergeMethod;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles all events whit event type "pull_request".
 */
public class IssuePullRequest extends Event {
	// Contants for Logger
	static Logger logger = LogManager.getLogger(IssuePullRequest.class);
	private static ArrayList<String> labelNameList = new ArrayList<>(Arrays.asList(MANUAL, NO_PORTING));

	@Override
	public void process(HttpServletRequest incoming, HttpServletResponse outgoing, String eventType) {
		Map<String, String> mergeTriggerMap = new HashMap<>();
		StringBuilder builder = new StringBuilder();
		String aux = null;
		String text = null;
		try(BufferedReader readerIncoming = incoming.getReader();){
			while ((aux = readerIncoming.readLine()) != null) {
				builder.append(aux);
			}
			text = builder.toString();
			mergeTriggerMap = generateMergeTriggerMap();
		} catch (IOException e){
			logger.info("No MergeTrigger rules are defined or payload can not be read.", e);
		} 
		try (StringReader reader = new StringReader(text)){

			GitHub github = connectToEnterpriseWithOAuth();
			PullRequest existingpr = github.parseEventPayload(reader, PullRequest.class);
			GHPullRequest existingPullRequest = existingpr.getPullRequest();
			GHRepository ghRepo = github.getRepository(existingPullRequest.getRepository().getFullName());
			if (prLabeledOrSynchronized(existingpr, existingPullRequest)) 
			{
				logger.info(PARTING_LINE);
				logger.info("STATUS CHECKS RUN ON REPO {}___{}___ID {}___NUMBER {}",ghRepo.getFullName(), existingpr.getAction(), existingPullRequest.getId(), existingpr.getNumber());
				rerunStatusChecks(mergeTriggerMap, ghRepo, existingPullRequest);
			}

			if (prOpened(existingpr, existingPullRequest)) {
				logger.info(PARTING_LINE);
				logger.info("OPEN PULL REQUEST INFO:REPO {}___ID{}___NUMBER {}",ghRepo.getFullName(), existingPullRequest.getId(), existingpr.getNumber());

				String mergeToBranch = existingPullRequest.getBase().getRef();
				String mergeFromBranch = existingPullRequest.getHead().getRef();

				if (developerIsUsingMergeBotBranch(mergeFromBranch, existingPullRequest, github)) {
					developerIsUsingMergeBotBranchClosePR(mergeToBranch, existingPullRequest, ghRepo);
				} else {
					GHPullRequest initialPullRequest = ghRepo
							.getPullRequest(getNumberInitialPullRequest(existingPullRequest));
					runStatusChecks(mergeTriggerMap, ghRepo, existingPullRequest, mergeToBranch, initialPullRequest);
				}

			}
			if (prClosed(existingpr, existingPullRequest)) {
				logger.info(PARTING_LINE);
				logger.info("CLOSE PULL REQUEST INFO:REPO {}___ID {}___NUMBER {}", ghRepo.getFullName(), existingPullRequest.getId(), existingpr.getNumber());

				GHPullRequest initialPullRequest = ghRepo
						.getPullRequest(getNumberInitialPullRequest(existingPullRequest));

				String mergeFromBranch;
				mergeFromBranch = existingPullRequest.getBase().getRef();
				String mergeToBranch = mergeTriggerMap.get(mergeFromBranch);

				deleteOldTmpMergeBotBranch(ghRepo, existingPullRequest);
				logger.info(
						"Merge from Branch {}___Merge to Branch {}___PR Number {}___PR ID {}",
						mergeFromBranch, mergeToBranch, existingPullRequest.getNumber(),existingPullRequest.getId());
				// if default branch (main) is reached no more action is taken -- finished!
				// if default branch (main) is reached no more action is taken -- finished!
				if (!mergeFromBranch.equals(ghRepo.getDefaultBranch()) && mergeToBranch != null) {

					if (containsLabel(existingPullRequest, NO_PORTING)) {
						logger.info("NO_PORTING MERGE");
					}
					if (containsLabel(existingPullRequest, MANUAL)
							|| !containsLabel(existingPullRequest, labelNameList)) {
						logger.info("NORMAL/MANUAL MERGE");
						doCherryPickAndCreateNewPr(ghRepo, existingPullRequest, initialPullRequest, mergeToBranch);
					}
				}
					executePostCommitGroovyScript(initialPullRequest, existingPullRequest);
			}
			github.refreshCache();
		} catch (IOException e) {
			logger.error("IOExcepton occured", e);
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			logger.error("Exception", e);
		} 
	}

	private GitHub connectToEnterpriseWithOAuth() throws IOException {
		return GitHub.connectToEnterpriseWithOAuth(API_URL,
				USER,
				TOKEN);
	}

	private boolean prClosed(PullRequest existingpr, GHPullRequest existingPullRequest) throws IOException {
		return existingpr.getAction().matches("closed") && existingPullRequest.isMerged();
	}

	private boolean prOpened(PullRequest existingpr, GHPullRequest existingPullRequest) throws IOException {
		return !existingPullRequest.isMerged() && (existingpr.getAction().matches("opened"));
	}

	private boolean prLabeledOrSynchronized(PullRequest existingpr, GHPullRequest existingPullRequest) {
		return existingpr.getAction().matches("labeled") && containsLabel(existingPullRequest, RERUN_CHECK)
				|| existingpr.getAction().equals("synchronize");
	}

	private boolean developerIsUsingMergeBotBranch(String mergeFromBranch, GHPullRequest existingPullRequest,
			GitHub github) {
		try {
			return mergeFromBranch.contains(MERGEBOT_ID)
					&& !existingPullRequest.getUser().getLogin().equals(github.getMyself().getLogin());
		} catch (IOException e) {
			logger.info("Developer use MergeBotBranch -- cannot get User", e);
		}
		return false;
	}

	private void developerIsUsingMergeBotBranchClosePR(String mergeToBranch, GHPullRequest existingPullRequest,
			GHRepository ghRepo) {
		try {
			existingPullRequest.comment("Please create a new feature branch with " + mergeToBranch
					+ " as base branch, because it is not allowed to reuse MergeBot-Branches.");
			existingPullRequest.close();
			deleteOldTmpMergeBotBranch(ghRepo, existingPullRequest);
		} catch (IOException e) {
			logger.info("Developer is using MbBranch -- close PR -- can not perform action on repo", e);
		}
	}

	private void doCherryPickAndCreateNewPr(GHRepository ghRepo, GHPullRequest existingPullRequest,
			GHPullRequest initialPullRequest, String mergeToBranch) {

		try {
			GHBranch base = ghRepo.getBranch(mergeToBranch);
			String tmpBranchNameMergeBot = getNameTmpMergeBotBranch(initialPullRequest, existingPullRequest,
					mergeToBranch);
			GHRef tmpToRef = createReference(ghRepo, tmpBranchNameMergeBot, base);

			String urlEncodedTmpBranchNameMergeBot = urlEncode(tmpBranchNameMergeBot);
			GHBranch tmpFromBranch = ghRepo.getBranch(urlEncodedTmpBranchNameMergeBot);

			// Create a temporary commit on the branch, which extends as a sibling of
			// the commit we want but contains the current tree of the target branch:
			GHCommit squashedCommitForCherryPick = ghRepo.getCommit(existingPullRequest.getMergeCommitSha());
			String parentSha = squashedCommitForCherryPick.getParentSHA1s().get(0);
			String tmpToBranchSha = tmpFromBranch.getSHA1();
			String tmptoBranchTreeSha = ghRepo.getCommit(tmpToBranchSha).getTree().getSha();

			GHCommitBuilder commitBuilder = ghRepo.createCommit();
			commitBuilder.parent(parentSha);
			commitBuilder.tree(tmptoBranchTreeSha);
			commitBuilder.message("MB prepares cherry pick");
			GHCommit prepareCherryCommit = commitBuilder.create();
			// Now temporarily force the branch over to that commit
			tmpToRef.updateTo(prepareCherryCommit.getSHA1(), true);

			// Merge the commit we want into this mess:
			HttpResponse response = doMergeForCherryPick(ghRepo, tmpBranchNameMergeBot, squashedCommitForCherryPick);
			int statusCode = response.getStatusLine().getStatusCode();
			switch (statusCode) {
			case 201:
				logger.info("STATUS CODE 201/ MERGE CREATED");
				// and get that tree!
				// Now that we know what the tree should be, create the cherry-pick commit.
				// Note that branchSha is the original from up at the top.
				String sha = getTreeShaFromMergeResponse(response);
				GHCommitBuilder commitBuilder2 = ghRepo.createCommit();
				commitBuilder2.parent(tmpToBranchSha);
				commitBuilder2.tree(sha);
				commitBuilder2
						.message("[" + initialPullRequest.getUser().getLogin() + "] " + existingPullRequest.getTitle()
								+ "\n" + squashedCommitForCherryPick.getCommitShortInfo().getMessage() + " | "
								+ squashedCommitForCherryPick.getSHA1() + "\n");
				GHCommit prepareCherryCommit2 = commitBuilder2.create();
				tmpToRef.updateTo(squashedCommitForCherryPick.getSHA1(), true);

				tmpToRef.updateTo(prepareCherryCommit2.getSHA1(), true);
				tmpFromBranch = ghRepo.getBranch(urlEncodedTmpBranchNameMergeBot);
				GHPullRequest newPr = createNewPullRequestAndRelateWithParent(ghRepo, existingPullRequest,
						initialPullRequest, mergeToBranch, tmpFromBranch, squashedCommitForCherryPick.getSHA1());
				// Merge newly created Pull Request because no Label is set to it / no new Pull
				// Request if label "Manual" is set
				if (!containsLabel(existingPullRequest, labelNameList)) {
					getMergeabilityAndMerge(tmpFromBranch, newPr);
				}
				else if (containsLabel(existingPullRequest, MANUAL)) {
					newPr.addLabels(MANUAL);
					addAssigneeToNewPr(initialPullRequest, newPr);
				}
				break;
			/*
			 * If merge is not possible, delete MB-Ref (because it is messed up from the
			 * prepareCherryCommit), create it again and commit
			 * MB_MergeConflictInstructions.delete file to it. assign PR to Developer and
			 * comment
			 */
			case 409:
				logger.info("STATUS CODE 409/ MERGE ERROR");
				logger.info("SQUASHED COMMIT SHA: {}", squashedCommitForCherryPick.getSHA1());
				tmpToRef.delete();
				GHRef tmpFromRefError = createReference(ghRepo, tmpBranchNameMergeBot, base);
				GHBranch tmpFromBranchError = ghRepo.getBranch(urlEncodedTmpBranchNameMergeBot);
				GHContentBuilder contentBuilder = ghRepo.createContent();

				contentBuilder.branch(tmpFromBranchError.getName());
				contentBuilder.path("MB_MergeConflictInstructions.deleteMe.md");
				contentBuilder.content(generateContentMergeConflictFile(squashedCommitForCherryPick.getSHA1(),
						tmpFromBranchError, initialPullRequest.getTitle()));
				contentBuilder.message("Instruction file for solving the merge error.");
				GHContentUpdateResponse responseContentBuilder = contentBuilder.commit();
				tmpFromRefError.updateTo(responseContentBuilder.getCommit().getSHA1());

				GHPullRequest newlyCreatedPr = createNewPullRequestAndRelateWithParent(ghRepo, existingPullRequest,
						initialPullRequest, mergeToBranch, tmpFromBranchError, squashedCommitForCherryPick.getSHA1());
				addAssigneeToNewPr(initialPullRequest, newlyCreatedPr);
				if (containsLabel(existingPullRequest, MANUAL)) {
					newlyCreatedPr.addLabels(MANUAL);
				}
				break;
			default:
				logger.info("Unknown error occured while trying to cherry pick. {}___{}", statusCode, response);
				break;
			}
		} catch (IOException e) {
			logger.error("ERROR/FakeCherryPick.", e);
		}
	}

	private void addAssigneeToNewPr(GHPullRequest initialPullRequest, GHPullRequest newlyCreatedPr) {
		try {
			newlyCreatedPr.setAssignees(initialPullRequest.getUser());
		} catch (IOException e) {
			logger.info("Can not set Assignees to PR -- repo connection", e);
		}
	}

	private String generateContentMergeConflictFile(String sha1, GHBranch tmpFromBranchError, String commitMessage) {
		String[] tmp = tmpFromBranchError.getName().split("/", 3);
		if (commitMessage.contains("\"")) {
			commitMessage = commitMessage.replace("\"", "\\ \"");
		}
		String data = "";
		try {
			data = new String(Files.readAllBytes(Paths.get(PATH_MERGE_CONFLICT_INSTRUCTIONS)));
			data = data.replace("_tmp0_", tmp[0]);
			data = data.replace("_tmp1_", tmp[1]);
			data = data.replace("_tmp2_", tmp[2]);
			data = data.replace("_tmpFromBranchErrorName_", tmpFromBranchError.getName());
			data = data.replace("_sha1_", sha1);
			data = data.replace("_commitMessage_", commitMessage);
		} catch (IOException e) {
			logger.info("Can not read MergeConflict-File.", e);
		}
		return data;
	}

	private String getTreeShaFromMergeResponse(HttpResponse response) {
		String sha = null;
		try (JsonReader jsonReader = Json.createReader(new StringReader(readHttpResponse(response)));) {
			JsonObject reply = jsonReader.readObject();
			JsonObject commit = reply.getJsonObject("commit");
			JsonObject tree = commit.getJsonObject("tree");
			sha = tree.getString("sha");

		} catch (UnsupportedOperationException e) {
			logger.info("Can not get Tree SHA from Merge Response", e);
		}
		return sha;
	}

	private String readHttpResponse(HttpResponse response) {
		try (BufferedReader bufReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));) {
			StringBuilder builder1 = new StringBuilder();
			String line;

			while ((line = bufReader.readLine()) != null) {
				builder1.append(line);
				builder1.append(System.lineSeparator());
			}

			return builder1.toString();
		} catch (IOException e) {
			logger.info("Cannot read HttpResponse to get tre sha.", e);
		}
		return null;
	}

	private HttpResponse doMergeForCherryPick(GHRepository ghRepo, String tmpBranchNameMergeBot, GHCommit squashedCommit) throws IOException {
		HttpPost request;
		StringEntity entity;
		HttpClient httpClient;
			httpClient = HttpClientBuilder.create().build();

			String json = Json.createObjectBuilder().add("base", tmpBranchNameMergeBot)
					.add("head", squashedCommit.getSHA1()).build().toString();
			request = new HttpPost(
					API_URL + "/repos/" + ghRepo.getFullName() + "/merges?client_id=" + USER + "&access_token=" + TOKEN);
			entity = new StringEntity(json);
			request.addHeader("Accept", "application/json");
			request.setEntity(entity);
			return httpClient.execute(request);
	}

	private GHRef createReference(GHRepository ghRepo, String tmpBranchNameMergeBot, GHBranch base) throws IOException {
			return ghRepo.createRef(REFS_HEADS_PATH + tmpBranchNameMergeBot, base.getSHA1());
	}

	private void deleteOldTmpMergeBotBranch(GHRepository ghRepo, GHPullRequest existingPullRequest) {
		// just deleting tmp branches created by the MergeBot
		if (existingPullRequest.getHead().getRef().contains(MERGEBOT_ID)) {
			try {
				GHRef oldBaseRef = ghRepo.getRef(HEADS_PATH + existingPullRequest.getHead().getRef());
				oldBaseRef.delete();
			} catch (IOException e) {
				logger.info("Can not delete Branch {} on Repo {} because it is already deleted.", existingPullRequest.getHead().getRef(), ghRepo.getFullName());
			}
		}
	}

	private void getMergeabilityAndMerge(GHBranch head, GHPullRequest newPr) {
		try {
			while (newPr.getMergeable() == null) {
				Thread.sleep(100);
				}
				boolean mergeable = newPr.getMergeable();
				if (Boolean.TRUE.equals(mergeable)) {
					newPr.merge("", head.getSHA1(), MergeMethod.SQUASH);
				} else {
					logger.info("Can not merge PR {}", newPr.getNumber());
				}
		} catch (InterruptedException | IOException e) {
			Thread.currentThread().interrupt();
			logger.info("Cannot check for mergeability or merge PR {}", newPr.getNumber(), e);
		}
	}

	private GHPullRequest createNewPullRequestAndRelateWithParent(GHRepository ghRepo,
			GHPullRequest existingPullRequest, GHPullRequest initialPullRequest, String mergeToBranch,
			GHBranch tmpFromBranch, String squashedCommitForCherryPickSha) throws IOException {
		return ghRepo.createPullRequest(generateTitel(existingPullRequest, mergeToBranch), tmpFromBranch.getName(),
				mergeToBranch,
				"Initial Pull Request(from Developer): #" + initialPullRequest.getNumber() + "\nParent Pull Request: #"
						+ existingPullRequest.getNumber() + "\nCherry-picked commit SHA "
						+ squashedCommitForCherryPickSha);
	}

	private String urlEncode(String encodeString) {
		try {
			String[] split = encodeString.split("/", 5);
			split[4] = URLEncoder.encode(split[4], StandardCharsets.UTF_8.name()).replace("%2F", "/");
			return String.join("/", split);
		} catch (UnsupportedEncodingException e) {
			logger.info("URL Encoding encountered a Prolbem", e);
		}
		return null;
	}

	private String generateTitel(GHPullRequest existingPullRequest, String mergeToBranch) {
		return existingPullRequest.getTitle() + " | " + mergeToBranch;

	}

	private boolean containsLabel(GHPullRequest pullRequest, ArrayList<String> labelNameList){
		return !pullRequest.getLabels().stream()
				.filter(l -> labelNameList.stream().anyMatch(v -> v.equals(l.getName()))).collect(Collectors.toList())
				.isEmpty();
	}
}
