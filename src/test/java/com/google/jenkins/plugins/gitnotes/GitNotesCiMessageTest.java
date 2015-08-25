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

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import jenkins.model.JenkinsLocationConfiguration;

/**
 * Tests for {@link GitNotesCiMessage}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(JenkinsLocationConfiguration.class)
public class GitNotesCiMessageTest {
  private static final String BASE_URL = "http://some.fake.host/jenkins/";
  private static final String JOB_URL = "job/somejob/12/";

  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Mock private FreeStyleBuild build;
  @Mock private FreeStyleProject project;
  @Mock private Launcher launcher;
  @Mock private BuildListener listener;
  @Mock private JenkinsLocationConfiguration locationConfig;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mockStatic(JenkinsLocationConfiguration.class);
    when(JenkinsLocationConfiguration.get()).thenReturn(locationConfig);
  }

  @Test
  public void testDefaultMessage() {
    GitNotesCiMessage notes = new GitNotesCiMessage();
    assertTrue(notes.message.has(GitNotesCiMessage.METADATA_TIMESTAMP));
    assertEquals(GitNotesCiMessage.DEFAULT_VERSION, notes.message.get(
        GitNotesCiMessage.METADATA_VERSION).getAsInt());
    assertEquals(GitNotesCiMessage.AGENT, notes.message.get(
        GitNotesCiMessage.METADATA_AGENT).getAsString());
  }

  @Test
  public void testAddStatus_sucess() {
    when(build.getResult()).thenReturn(Result.SUCCESS);

    GitNotesCiMessage notes = new GitNotesCiMessage();
    notes.addStatus(build, listener);
    assertEquals(GitNotesCiMessage.STATUS_SUCCESS, notes.message.get(
        GitNotesCiMessage.METADATA_STATUS).getAsString());
  }

  @Test
  public void testAddStatus_failure() {
    when(build.getResult()).thenReturn(Result.FAILURE);

    GitNotesCiMessage notes = new GitNotesCiMessage();
    notes.addStatus(build, listener);
    assertEquals(GitNotesCiMessage.STATUS_FAILURE, notes.message.get(
        GitNotesCiMessage.METADATA_STATUS).getAsString());
  }

  @Test
  public void testAddBuildLogUrl() {
    when(locationConfig.getUrl()).thenReturn(null);
    when(build.getUrl()).thenReturn(JOB_URL);
    GitNotesCiMessage notes = new GitNotesCiMessage();
    notes.addBuildLogUrl(build, listener);
    assertEquals(JOB_URL, notes.message.get(
        GitNotesCiMessage.METADATA_URL).getAsString());
  }

  @Test
  public void testAddBuildLogUrl_noBaseUrl() {
    when(locationConfig.getUrl()).thenReturn(null);
    when(build.getUrl()).thenReturn(JOB_URL);
    GitNotesCiMessage notes = new GitNotesCiMessage();
    notes.addBuildLogUrl(build, listener);
    assertEquals(JOB_URL, notes.message.get(
        GitNotesCiMessage.METADATA_URL).getAsString());
  }

  @Test
  public void testAddVersion() {
    GitNotesCiMessage notes = new GitNotesCiMessage();
    notes.addVersion(5);
    assertEquals(5, notes.message.get(
        GitNotesCiMessage.METADATA_VERSION).getAsInt());
  }
}

