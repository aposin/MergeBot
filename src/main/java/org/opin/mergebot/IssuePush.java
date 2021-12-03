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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHEventPayload.Push;
import org.kohsuke.github.GHEventPayload.Push.PushCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class IssuePush extends Event {
	private static Logger logger = LogManager.getLogger(IssuePush.class);

	@Override
	void process(HttpServletRequest incoming, HttpServletResponse outgoing, String eventType) {
		logger.info("FILES ON REPO WERE UPDATED; GET THEM TO SERVER");
		GitHub github;

		StringBuilder builder = new StringBuilder();
		String aux = null;
		String text = null;
		try (BufferedReader readerIncoming = incoming.getReader();) {
			while ((aux = readerIncoming.readLine()) != null) {
				builder.append(aux);
			}
			text = builder.toString();
		} catch (IOException e) {
			logger.info("payload can not be read.", e);
		}
		try {
			github = GitHub.connectToEnterpriseWithOAuth(Event.API_URL, Event.USER, Event.TOKEN);

			Push push = github.parseEventPayload(new StringReader(text), Push.class);
			GHRepository ghRepo = github.getRepository(push.getRepository().getFullName());
			logger.info("PUSH REF {}", push.getRef());
			logger.info("DEFUALT Branch {}{}", Event.REFS_HEADS_PATH, ghRepo.getDefaultBranch());

			if (push.getRef().equals(Event.REFS_HEADS_PATH + ghRepo.getDefaultBranch())) {
				List<PushCommit> list = push.getCommits();
				ArrayList<String> modified = new ArrayList<>();
				list.forEach(c -> {
					modified.addAll(c.getModified());
					modified.addAll(c.getAdded());
				});
				modified.forEach(m -> logger.info("REPO {} Modified::: {}", ghRepo.getFullName(), m));
				if (!modified.isEmpty()) {
					modified.forEach(f -> {
						try {
							Files.deleteIfExists(Paths.get(Event.ABSOLUTE_PATH_CONFIGURATION_FILES + f));
							updateFileOnServer(ghRepo, Event.ABSOLUTE_PATH_CONFIGURATION_FILES + f, f);
						} catch (IOException e) {
							logger.error("Delete MergeTriggerRules/Scripts or create Problem", e);
						}
					});

				}
			}
			github.refreshCache();
		} catch (IOException i) {
			logger.info("Cannot log in to repository", i);
		}
	}

	private void updateFileOnServer(GHRepository ghRepo, String filePath, String fileName) {
		GHContent contentFile;
		try {
			contentFile = ghRepo.getFileContent(fileName, ghRepo.getDefaultBranch());
			InputStream streamFile = contentFile.read();
			File file = new File(filePath);
			java.nio.file.Files.copy(streamFile, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			streamFile.close();
		} catch (IOException o) {
			logger.info("REPO {} can not update file on server {}", ghRepo.getFullName(), fileName, o);
		}
	}
}
