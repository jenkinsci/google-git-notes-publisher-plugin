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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.PushCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.gson.JsonObject;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.model.JenkinsLocationConfiguration;

/**
 * Tests for {@link GitNotesJobLogger}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(JenkinsLocationConfiguration.class)
public class GitNotesJobLoggerTest {
  private static final String BASE_URL = "http://some.fake.host/jenkins/";
  private static final String JOB_URL = "job/somejob/12/";
  private static final String FULL_URL = BASE_URL + JOB_URL;
  private static final String REMOTE_URI = "http://git.host/remote/";
  private static final long CURRENT_TIME_MILLIS = 1234567;

  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Mock private FreeStyleBuild build;
  @Mock private FreeStyleProject project;
  @Mock private Launcher launcher;
  @Mock private BuildListener listener;
  @Mock private JenkinsLocationConfiguration locationConfig;
  @Mock private SCM scm;
  @Mock private GitSCM gitSCM;
  @Mock private GitClient gitClient;
  @Mock private RemoteConfig gitRepoConfig;
  @Mock private PrintStream logger;
  @Mock private FetchCommand fetchCommand;
  @Mock private PushCommand pushCommand;
  private List<URIish> remoteURIs;
  private List<RefSpec> refs;
  private GitNotesJobLogger recorder;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mockStatic(JenkinsLocationConfiguration.class);
    when(JenkinsLocationConfiguration.get()).thenReturn(locationConfig);
    mockStatic(System.class);
    when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MILLIS);
    when(build.getProject()).thenReturn(project);
    when(build.getUrl()).thenReturn("");
    when(gitSCM.createClient(
        Matchers.<TaskListener>anyObject(),
        Matchers.<EnvVars>anyObject(),
        Matchers.<Run<?, ?>>anyObject(),
        Matchers.<FilePath>anyObject()))
        .thenReturn(gitClient);
    remoteURIs = new ArrayList<URIish>();
    remoteURIs.add(new URIish(REMOTE_URI));
    refs = new ArrayList<RefSpec>();
    refs.add(new RefSpec(GitNotesJobLogger.GIT_NOTES_REFS));
    when(listener.getLogger()).thenReturn(logger);

    // Set up git client objects
    when(gitClient.refExists(GitNotesJobLogger.GIT_NOTES_REFS))
        .thenReturn(true);
    when(gitClient.fetch_()).thenReturn(fetchCommand);
    when(gitClient.push()).thenReturn(pushCommand);
    when(fetchCommand.from(Matchers.<URIish>anyObject(),
            Matchers.<List<RefSpec>>anyObject())).thenReturn(fetchCommand);
    when(pushCommand.to(Matchers.<URIish>anyObject())).thenReturn(pushCommand);
    when(pushCommand.ref(Matchers.anyString())).thenReturn(pushCommand);
    when(pushCommand.force()).thenReturn(pushCommand);
    when(pushCommand.tags(Matchers.anyBoolean())).thenReturn(pushCommand);
    when(pushCommand.timeout(Matchers.<Integer>anyObject()))
        .thenReturn(pushCommand);

    // Object under test
    recorder = new GitNotesJobLogger();
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testSetupNoSCM() throws Exception {
    when(project.getScm()).thenReturn(null);
    recorder.setUp((AbstractBuild<?, ?>) build, launcher, listener);
    verifyZeroInteractions(gitClient);
  }

  @Test
  public void testSetupNonGitSCM() throws Exception {
    when(project.getScm()).thenReturn(scm);
    recorder.setUp((AbstractBuild<?, ?>) build, launcher,
        listener);
    verifyZeroInteractions(gitClient);
  }

  @Test
  public void testSetupNoRemoteConfig() throws Exception {
    when(project.getScm()).thenReturn(gitSCM);
    recorder.setUp((AbstractBuild<?, ?>) build, launcher,
        listener);
    verifyZeroInteractions(gitClient);
  }

  @Test
  public void testSetupNoBuildUrl() throws Exception {
    when(project.getScm()).thenReturn(gitSCM);
    when(gitSCM.getRepositoryByName(Matchers.anyString()))
        .thenReturn(gitRepoConfig);
    when(gitRepoConfig.getURIs()).thenReturn(remoteURIs);

    recorder.setUp((AbstractBuild<?, ?>) build, launcher,
        listener);
    GitNotesCiMessage notes = new GitNotesCiMessage();
    JsonObject obj = notes.message;
    obj.addProperty(GitNotesCiMessage.METADATA_VERSION, 0);
    obj.addProperty(GitNotesCiMessage.METADATA_URL,
        GitNotesCiMessage.BUILD_URL_NOT_AVAILABLE);
    verifyStatusWritten(obj.toString());
  }

  @Test
  public void testSetupWithNoRootUrl() throws Exception {
    when(project.getScm()).thenReturn(gitSCM);
    when(gitSCM.getRepositoryByName(Matchers.anyString()))
        .thenReturn(gitRepoConfig);
    when(gitRepoConfig.getURIs()).thenReturn(remoteURIs);
    when(locationConfig.getUrl()).thenReturn(null);
    when(build.getUrl()).thenReturn(JOB_URL);

    recorder.setUp((AbstractBuild<?, ?>) build, launcher,
        listener);
    GitNotesCiMessage notes = new GitNotesCiMessage();
    JsonObject obj = notes.message;
    obj.addProperty(GitNotesCiMessage.METADATA_VERSION, 0);
    obj.addProperty(GitNotesCiMessage.METADATA_URL, JOB_URL);
    verifyStatusWritten(obj.toString());
  }

  @Test
  public void testSetupWithFullBuildUrl() throws Exception {
    when(project.getScm()).thenReturn(gitSCM);
    when(gitSCM.getRepositoryByName(Matchers.anyString()))
        .thenReturn(gitRepoConfig);
    when(gitRepoConfig.getURIs()).thenReturn(remoteURIs);
    when(build.getResult()).thenReturn(Result.SUCCESS);
    when(locationConfig.getUrl()).thenReturn(BASE_URL);
    when(build.getUrl()).thenReturn(JOB_URL);

    recorder.setUp((AbstractBuild<?, ?>) build, launcher,
        listener);
    GitNotesCiMessage notes = new GitNotesCiMessage();
    JsonObject obj = notes.message;
    obj.addProperty(GitNotesCiMessage.METADATA_VERSION, 0);
    obj.addProperty(GitNotesCiMessage.METADATA_URL, FULL_URL);
    verifyStatusWritten(obj.toString());
  }

  @Test
  public void testSetupNotesRefNotExist() throws Exception {
    when(gitClient.refExists(GitNotesJobLogger.GIT_NOTES_REFS))
      .thenReturn(false);
    when(project.getScm()).thenReturn(gitSCM);
    when(gitSCM.getRepositoryByName(Matchers.anyString()))
        .thenReturn(gitRepoConfig);
    when(gitRepoConfig.getURIs()).thenReturn(remoteURIs);
    when(locationConfig.getUrl()).thenReturn(BASE_URL);
    when(build.getUrl()).thenReturn(JOB_URL);

    recorder.setUp((AbstractBuild<?, ?>) build, launcher,
        listener);
    GitNotesCiMessage notes = new GitNotesCiMessage();
    JsonObject obj = notes.message;
    obj.addProperty(GitNotesCiMessage.METADATA_VERSION, 0);
    obj.addProperty(GitNotesCiMessage.METADATA_URL, FULL_URL);

    // verify code path to create the refs
    verify(gitClient).ref(GitNotesJobLogger.GIT_NOTES_REFS);
    verify(gitClient, times(2)).push();
    verify(pushCommand, times(2)).to(new URIish(REMOTE_URI));
    verify(pushCommand, times(2)).ref(GitNotesJobLogger.GIT_NOTES_REFS);
    verify(pushCommand, times(2)).execute();

    verify(gitClient).appendNote(obj.toString(),
        GitNotesJobLogger.GIT_NOTES_REFS);
  }

  @Test
  public void testLoggerCLoseFailedStatusFullBuildUrl() throws Exception {
    when(project.getScm()).thenReturn(gitSCM);
    when(gitSCM.getRepositoryByName(Matchers.anyString()))
        .thenReturn(gitRepoConfig);
    when(gitRepoConfig.getURIs()).thenReturn(remoteURIs);
    when(build.getResult()).thenReturn(Result.FAILURE);
    when(locationConfig.getUrl()).thenReturn(BASE_URL);
    when(build.getUrl()).thenReturn(JOB_URL);

    OutputStream decoratedLogger = recorder.decorateLogger(build, logger);
    decoratedLogger.close();
    GitNotesCiMessage notes = new GitNotesCiMessage();
    JsonObject obj = notes.message;
    obj.addProperty(GitNotesCiMessage.METADATA_VERSION, 0);
    obj.addProperty(GitNotesCiMessage.METADATA_URL, FULL_URL);
    obj.addProperty(GitNotesCiMessage.METADATA_STATUS,
        GitNotesCiMessage.STATUS_FAILURE);
    verifyStatusWritten(obj.toString());
  }

  @Test
  public void testLoggerCloseSuccessStatusPartialBuildUrl() throws Exception {
    when(project.getScm()).thenReturn(gitSCM);
    when(gitSCM.getRepositoryByName(Matchers.anyString()))
        .thenReturn(gitRepoConfig);
    when(gitRepoConfig.getURIs()).thenReturn(remoteURIs);
    when(build.getResult()).thenReturn(Result.SUCCESS);
    when(locationConfig.getUrl()).thenReturn(null);
    when(build.getUrl()).thenReturn(JOB_URL);

    OutputStream decoratedLogger = recorder.decorateLogger(build, logger);
    decoratedLogger.close();
    GitNotesCiMessage notes = new GitNotesCiMessage();
    JsonObject obj = notes.message;
    obj.addProperty(GitNotesCiMessage.METADATA_VERSION, 0);
    obj.addProperty(GitNotesCiMessage.METADATA_URL, JOB_URL);
    obj.addProperty(GitNotesCiMessage.METADATA_STATUS,
        GitNotesCiMessage.STATUS_SUCCESS);
  }

  private void verifyStatusWritten(String expectedMessage) throws Exception {
    verify(gitClient).appendNote(expectedMessage,
        GitNotesJobLogger.GIT_NOTES_REFS);
    verify(gitClient).fetch_();
    verify(gitClient).push();
    verify(fetchCommand).execute();
    verify(pushCommand).execute();
  }
}

