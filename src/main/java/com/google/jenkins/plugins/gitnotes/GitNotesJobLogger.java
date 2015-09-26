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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.PushCommand;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.annotations.VisibleForTesting;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

/**
 * A logger that logs the job's status and other information to git-notes.
 */
public class GitNotesJobLogger extends BuildWrapper {

  private static final String GIT_REPO_ORIGIN = "origin";

  @VisibleForTesting
  static final String GIT_NOTES_REFS = "refs/notes/devtools/ci";
  private static final Logger LOGGER = Logger.getLogger(
      GitNotesJobLogger.class.getName());

  @DataBoundConstructor
  public GitNotesJobLogger() {
  }

  @Override
  public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) {
    class ResultLogger extends FilterOutputStream {
      private AbstractBuild build;
      public ResultLogger(AbstractBuild build, OutputStream out) {
        super(out);
        this.build = build;
      }

      // We use the "close" event on the Build's logger to perform the final
      // log because the Environment we "setUp" is torn down before the
      // build result is finalized.  Without this, we would see all of the
      // build steps open and close, but the build would be open and never
      // closed.
      @Override
      public void close() throws IOException {
        logBuildFinishedMessage(build, new StreamBuildListener(this.out));
        super.close();
      }
    }
    return new ResultLogger(build, logger);
  }

  /**
   * Logs a message into git-notes when build/job starts.
   */
  private void logBuildStartMessage(AbstractBuild<?, ?> build,
      BuildListener listener) {
    GitSCM scm = getGitSCM(build);
    if (scm != null) {
      GitNotesCiMessage message = new GitNotesCiMessage();
      message.addVersion(0).addBuildLogUrl(build, listener);
      writeGitNoteMessage(build, listener, message, scm);
    }
  }

  /**
   * Logs a message into git-notes when build/job finishes.
   */
  private void logBuildFinishedMessage(AbstractBuild<?, ?> build,
      BuildListener listener) {
    GitSCM scm = getGitSCM(build);
    if (scm != null) {
      GitNotesCiMessage message = new GitNotesCiMessage();
      message.addVersion(0).addBuildLogUrl(build, listener)
          .addStatus(build, listener);
      writeGitNoteMessage(build, listener, message, scm);
    }
  }

  /**
   * Helper function to write the given message to git-notes.
   */
  private void writeGitNoteMessage(AbstractBuild<?, ?> build,
      final BuildListener listener, GitNotesCiMessage message, GitSCM gitScm) {
    try {
      final GitClient gitClient = gitScm.createClient(listener,
          build.getEnvironment(listener), build, build.getWorkspace());

      RemoteConfig remoteConfig = gitScm.getRepositoryByName(GIT_REPO_ORIGIN);

      if (remoteConfig == null) {
        listener.getLogger().println("Failed to find Git repository.");
        return;
      }

      URIish remoteURI = remoteConfig.getURIs().get(0);
      try {
        ArrayList<RefSpec> refs = new ArrayList<RefSpec>();
        refs.add(new RefSpec(
            String.format("+%s:%s", GIT_NOTES_REFS, GIT_NOTES_REFS)));
        FetchCommand fetch = gitClient.fetch_().from(remoteURI, refs);
        fetch.execute();
      } catch (GitException e) {
        // This could be a normal case, when the remote doesn't have the
        // expected git-notes reference yet. The git library doesn't return
        // a dedicated exception type for "reference not found", so we
        // would just ignore all GitExceptions here.
        listener.getLogger().printf(
            "Caught GitException: %s. Most likely remote doesn't have " +
            "git notes reference %s", e.getMessage(), GIT_NOTES_REFS);
      }
      if (!gitClient.refExists(GIT_NOTES_REFS)) {
        try {
          gitClient.ref(GIT_NOTES_REFS);
          PushCommand push = gitClient.push().to(remoteURI).ref(GIT_NOTES_REFS);
          push.execute();
        } catch (GitException e) {
          // if the push failed, we should remove locally created GIT_NOTES_REFS
          listener.getLogger().printf(
              "Failed to push %s, removing locally created notes refs",
              GIT_NOTES_REFS);
          gitClient.deleteRef(GIT_NOTES_REFS);
          throw e;
        }
      }
      gitClient.appendNote(message.toString(), GIT_NOTES_REFS);

      PushCommand push = gitClient.push().to(remoteURI).ref(GIT_NOTES_REFS);
      push.execute();
    } catch (GitException e) {
      e.printStackTrace(
          listener.error("Caught git-notes exception. " + e.getMessage()));
    } catch (IOException e) {
      e.printStackTrace(listener.error(e.getMessage()));
    } catch (InterruptedException e) {
      e.printStackTrace(listener.error(e.getMessage()));
    }
  }

  /**
   * The environment that is instantiated for the duration of the build.
   */
  public class EnvironmentImpl extends Environment {
    protected EnvironmentImpl(final AbstractBuild build,
        final TaskListener listener) {
    }

    /** {@inheritDoc} */
    @Override
    public boolean tearDown(AbstractBuild build, BuildListener listener)
        throws IOException, InterruptedException {
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public EnvironmentImpl setUp(AbstractBuild build,
    Launcher launcher, BuildListener listener) {
    logBuildStartMessage(build, listener);
    return new EnvironmentImpl(build, listener);
  }

  private static GitSCM getGitSCM(AbstractBuild build) {
    if (build == null) {
      LOGGER.warning("Cannot extract Git SCM for null build.");
      return null;
    }
    final AbstractProject<?, ?> project = build.getProject();
    if (project == null) {
      LOGGER.warning("Cannot extract Git SCM for null project.");
      return null;
    }
    final SCM scm = project.getScm();
    if (scm == null || !(scm instanceof GitSCM)) {
      LOGGER.warning("No Git SCM detected.");
      return null;
    }

    return (GitSCM) scm;
  }

  /**
   * Descriptor for this plugin.
   */
  @Extension
  public static class DescriptorImpl extends BuildWrapperDescriptor {

    /** {@inheritDoc} */
    @Override
    public boolean isApplicable(AbstractProject<?, ?> item) {
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return "Log Build Status to Git Notes";
    }
  }

  /** {@inheritDoc} */
  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }
}
