package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.StructuredForm;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitSCM;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Browser for TFS 2013
 */
public class TFS2013GitRepositoryBrowser extends GitRepositoryBrowser {

    @DataBoundConstructor
    public TFS2013GitRepositoryBrowser(String repoUrl) {
        super(repoUrl);
    }

    // <repo>/commit/<hash>#path=<path>&_a=compare
    @Override
    public URL getDiffLink(GitChangeSet.Path path) throws IOException {
        return new URL(getChangeSetLink(path.getChangeSet()), "#path=" + path.getPath() + "&_a=compare");
    }

    @Override
    public URL getFileLink(GitChangeSet.Path path) throws IOException {
        return new URL(getChangeSetLink(path.getChangeSet()), "#path=" + path.getPath() + "&_a=history");
    }

    @Override
    public URL getChangeSetLink(GitChangeSet gitChangeSet) throws IOException {
        return new URL(getRepoUrl(gitChangeSet), "commit/" + gitChangeSet.getId());
    }

    URL getRepoUrl(GitChangeSet changeSet) throws MalformedURLException {
        String result = getRepoUrl();
        
        if (StringUtils.isBlank(result))
            result = getUrlFromFirstConfiguredRepository(changeSet);

        else if (!result.contains("/"))
            result = getResultFromNamedRepository(changeSet);

        if (!result.endsWith("/"))
            result = result + "/";
        return new URL(result);
    }

    private String getResultFromNamedRepository(GitChangeSet changeSet) {
        GitSCM scm = getScmFromProject(changeSet);
        return scm.getRepositoryByName(getRepoUrl()).getURIs().get(0).toString();
    }

    private String getUrlFromFirstConfiguredRepository(GitChangeSet changeSet) throws MalformedURLException {
        GitSCM scm = getScmFromProject(changeSet);
        return scm.getRepositories().get(0).getURIs().get(0).toString();
    }

    private GitSCM getScmFromProject(GitChangeSet changeSet) {
        AbstractProject<?,?> build = (AbstractProject<?, ?>) changeSet.getParent().getRun().getParent();

        return (GitSCM) build.getScm();
    }

    @Extension
    public static class TFS2013GitRepositoryBrowserDescriptor extends Descriptor<RepositoryBrowser<?>> {

        public String getDisplayName() {
            return "Microsoft TFS 2013";
        }

        @Override
        public TFS2013GitRepositoryBrowser newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            try {
                JSONObject form = req.getSubmittedForm();
                System.out.println(form);
                System.out.println(jsonObject);
            } catch (ServletException e) {
                e.printStackTrace();
            }
            return req.bindJSON(TFS2013GitRepositoryBrowser.class, jsonObject);
        }

        /**
         * Performs on-the-fly validation of the URL.
         */
        public FormValidation doCheckRepoUrl(@QueryParameter(fixEmpty = true) String value, @AncestorInPath AbstractProject project) throws IOException,
                ServletException {
            
            if (value == null) // nothing entered yet
                value = "origin";

            if (!value.contains("/")) {
                GitSCM scm = (GitSCM) project.getScm();
                RemoteConfig remote = scm.getRepositoryByName(value);
                if (remote == null)
                    return FormValidation.errorWithMarkup("There is no remote with the name <tt>" + value + "</tt>");
                
                value = remote.getURIs().get(0).toString();
            }
            
            if (!value.endsWith("/"))
                value += '/';
            if (!URL_PATTERN.matcher(value).matches())
                return FormValidation.errorWithMarkup("The URL should end like <tt>.../_git/foobar/</tt>");

            // Connect to URL and check content only if we have admin permission
            if (!Jenkins.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();

            final String finalValue = value;
            return new FormValidation.URLCheck() {
                @Override
                protected FormValidation check() throws IOException, ServletException {
                    try {
                        if (findText(open(new URL(finalValue)), "Team Foundation Server 2013")) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("This is a valid URL but it doesn't look like Microsoft TFS 2013");
                        }
                    } catch (IOException e) {
                        return handleIOException(finalValue, e);
                    }
                }
            }.check();
        }

        private static final Pattern URL_PATTERN = Pattern.compile(".+/_git/[^/]+/");

    }
}
