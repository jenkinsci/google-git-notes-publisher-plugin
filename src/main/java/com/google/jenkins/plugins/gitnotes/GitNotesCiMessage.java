/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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
package com.google.jenkins.plugins.gitnotes;

import java.util.Objects;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gson.JsonObject;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import jenkins.model.Jenkins;

/**
 * Encapsulation of message for continuous integration job's status
 * and information.
 */
public class GitNotesCiMessage {
  @VisibleForTesting
  static final String METADATA_STATUS = "status";
  @VisibleForTesting
  static final String STATUS_SUCCESS = "success";
  @VisibleForTesting
  static final String STATUS_FAILURE = "failure";
  @VisibleForTesting
  static final String METADATA_AGENT = "agent";
  @VisibleForTesting
  static final String AGENT = "Jenkins(" +
      Jenkins.getVersion() + ") " +
      GitNotesJobLogger.class.getSimpleName();
  @VisibleForTesting
  static final String METADATA_URL = "url";
  @VisibleForTesting
  static final String METADATA_VERSION = "v";
  @VisibleForTesting
  static final String METADATA_TIMESTAMP = "timestamp";
  @VisibleForTesting
  static final int DEFAULT_VERSION = 0;
  @VisibleForTesting
  static final String BUILD_URL_NOT_AVAILABLE = "unavailable";

  @VisibleForTesting
  final JsonObject message;

  public GitNotesCiMessage() {
    this.message = new JsonObject();
    message.addProperty(METADATA_TIMESTAMP,
        String.format("%010d", System.currentTimeMillis() / 1000));

    message.addProperty(METADATA_VERSION, DEFAULT_VERSION);
    message.addProperty(METADATA_AGENT, AGENT);
  }

  /**
   * Adds build status to the message.
   */
  public GitNotesCiMessage addStatus(
      AbstractBuild<?, ?> build, BuildListener listener) {
    Result result = build.getResult();
    if (result == null) {
      listener.error("No build result found.");
    } else if (result.equals(Result.SUCCESS)) {
      message.addProperty(METADATA_STATUS, STATUS_SUCCESS);
    } else {
      message.addProperty(METADATA_STATUS, STATUS_FAILURE);
    }
    return this;
  }

  /**
   * Adds this build job's URL to the message.
   */
  public GitNotesCiMessage addBuildLogUrl(AbstractBuild<?, ?> build,
      BuildListener listener) {
    // The rootUrl will be null when it is not configured by the user and
    // the calling thread is not in a http request.
    Jenkins jenkins = Jenkins.getInstance();
    String rootUrl = jenkins == null ? null : jenkins.getRootUrl();
    String buildUrl = build.getUrl();
    if (Strings.isNullOrEmpty(buildUrl)) {
      if (listener != null) {
        listener.getLogger().println("Git notes recorder: no build URL found.");
      }
      buildUrl = BUILD_URL_NOT_AVAILABLE;
    }
    String fullUrl = Strings.isNullOrEmpty(rootUrl) ?
        buildUrl : rootUrl + buildUrl;
    message.addProperty(METADATA_URL, fullUrl);

    return this;
  }

  /**
   * Adds this message's version number.
   */
  public GitNotesCiMessage addVersion(int version) {
    message.addProperty(METADATA_VERSION, version);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return message.toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object o) {
    return (o == this) || (o instanceof GitNotesCiMessage
        && ((GitNotesCiMessage) o).message.equals(message));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hashCode(message);
  }
}
