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

import java.util.ArrayList;
import java.util.List;

public class ScriptInputPre{

	private List<FilesToMerge> filesToMerge = new ArrayList<>();
	private String user;
	private String userEmail;
	private List<String> commentsList;
	private String initialDeveloper;
	private List<String> reviewerList;
	private List<String> reviewSubmitterList;
	private String maintainerFilePath;
	
	public List<FilesToMerge> getFilesToMerge() {
		return filesToMerge;
	}

	public void setFilesToMerge(List<FilesToMerge> filesToMerge) {
		this.filesToMerge = filesToMerge;
	}
	
	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getUserEmail() {
		return userEmail;
	}

	public void setUserEmail(String userEmail) {
		this.userEmail = userEmail;
	}

	public List<String> getCommentsList() {
		return commentsList;
	}

	public void setCommentsList(List<String> commentsList) {
		this.commentsList = commentsList;
	}

	public String getInitialDeveloper() {
		return initialDeveloper;
	}

	public void setInitialDeveloper(String initialDeveloper) {
		this.initialDeveloper = initialDeveloper;
	}

	public List<String> getReviewerList() {
		return reviewerList;
	}

	public void setReviewerList(List<String> setReviewer) {
		this.reviewerList = setReviewer;
	}
	public String getMaintainerFilePath() {
		return maintainerFilePath;
	}

	public void setMaintainerFilePath(String maintainerFilePath) {
		this.maintainerFilePath = maintainerFilePath;
	}

	public List<String> getReviewSubmitterList() {
		return reviewSubmitterList;
	}

	public void setReviewSubmitterList(List<String> submittedReviews) {
		this.reviewSubmitterList = submittedReviews;
	}
}
