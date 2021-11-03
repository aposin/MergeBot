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
import java.io.StringReader;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GHEventPayload.PullRequestReview;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

public class IssuePullRequestReview extends Event {
	private static Logger logger = LogManager.getLogger(IssuePullRequestReview.class);

	@Override
	void process(HttpServletRequest incoming, HttpServletResponse outgoing, String eventType) {
		GitHub github;

		StringBuilder builder = new StringBuilder();
		String aux = null;
		String text = null;
		try(BufferedReader readerIncoming = incoming.getReader();){
			while ((aux = readerIncoming.readLine()) != null) {
				builder.append(aux);
			}
			text = builder.toString();
		} catch (IOException e){
			logger.info("Problems reading payload - Pull Request Review", e);
		} 
		try (StringReader reader = new StringReader(text)){

			github = GitHub.connectToEnterpriseWithOAuth(Event.API_URL, Event.USER,
					Event.TOKEN);
			PullRequestReview pullRequestReview = github.parseEventPayload(new StringReader(text),
					PullRequestReview.class);
			GHPullRequest reviewedPr = pullRequestReview.getPullRequest();
			GHRepository ghRepo = github.getRepository(reviewedPr.getRepository().getFullName());
			GHPullRequest initialPr = ghRepo.getPullRequest(getNumberInitialPullRequest(reviewedPr));
			logger.info("REPO: {} Review submitted  from {}__InitialPR from {}",ghRepo.getFullName(), pullRequestReview.getSender().getLogin(), initialPr.getUser().getLogin());
			setReviewStatus(ghRepo, reviewedPr, !selfreviewDone(pullRequestReview, reviewedPr, initialPr));
			github.refreshCache();
		} catch (IOException e) {
			logger.error("IOExcepton occured", e);
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	private boolean selfreviewDone(PullRequestReview pullRequestReview, GHPullRequest reviewedPr,
			GHPullRequest initialPr) throws IOException {
		return pullRequestReview.getAction().matches("submitted") 
		&& reviewedPr.getHead().getRef().contains(getMergebotId())
		&& pullRequestReview.getSender().getLogin().equals(initialPr.getUser().getLogin());
	}

	private void setReviewStatus(GHRepository ghRepo, GHPullRequest reviewedPr, boolean statusOfCheck) {
		setStatusDirectlyInPr(statusOfCheck, ghRepo, reviewedPr, "Self-Review",
				"Choose another reviewer. It is not allowed to review own PR.");
	}
}
